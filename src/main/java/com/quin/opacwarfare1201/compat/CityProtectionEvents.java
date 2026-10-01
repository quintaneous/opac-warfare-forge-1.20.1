package com.quin.opacwarfare1201.compat;

import com.quin.opacwarfare1201.war.StrategicCity;
import com.quin.opacwarfare1201.war.WarManager;
import com.quin.opacwarfare1201.war.WarPhase;
import com.quin.opacwarfare1201.war.WarRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CityProtectionEvents {
    private CityProtectionEvents() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        WarManager manager = WarManager.get(level.getServer());
        BlockPos pos = event.getPos();

        // Original/snapshotted city infrastructure is permanent at all times.
        if (manager.isProtectedCityBlock(level.dimension().location(), pos)) {
            event.setCanceled(true);
            return;
        }

        StrategicCity city = manager.cityAtBlock(level.dimension().location(), pos);
        if (city == null) return;

        WarRecord war = manager.anyWarForCity(city.id);
        if (war == null) {
            // Peacetime demolition is allowed. The persisted accounting set is
            // lazily pruned against the real world before the next budget check.
            return;
        }

        // PREPARING freezes the battlefield completely.
        if (war.phase != WarPhase.ACTIVE) {
            event.setCanceled(true);
            return;
        }

        // During ACTIVE, only living/eligible attackers may hand-breach
        // player-built fortifications. Defenders cannot repair/demolish and
        // rebuild their wall while the assault is underway.
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !manager.isAttacker(war, player.getUUID())
                || !manager.isParticipant(war, player.getUUID(), true)) {
            event.setCanceled(true);
            return;
        }

        // Infantry breaching intentionally yields no block/XP drops. Cancel the
        // vanilla harvest and remove the block server-side without loot.
        event.setCanceled(true);
        event.setExpToDrop(0);
        if (level.destroyBlock(pos, false, player)) {
            manager.releaseCityFortification(level, pos);

            // Keep the normal one-point tool wear cost even though vanilla
            // harvesting was replaced to suppress drops.
            if (!player.isCreative() && !player.getMainHandItem().isEmpty()) {
                player.getMainHandItem().hurtAndBreak(1, player,
                        p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPosition().orElse(null);
        if (pos == null) return;

        WarManager manager = WarManager.get(level.getServer());

        if (manager.isProtectedCityBlock(level.dimension().location(), pos)) {
            event.setCanceled(true);
            return;
        }

        StrategicCity city = manager.cityAtBlock(level.dimension().location(), pos);
        if (city == null) return;

        WarRecord war = manager.activeWarForCity(city.id);
        if (war == null
                || !manager.isAttacker(war, player.getUUID())
                || !manager.isParticipant(war, player.getUUID(), true)) {
            return;
        }

        BlockState state = event.getState();
        float vanillaHardness = state.getDestroySpeed(level, pos);

        if (vanillaHardness < 0F) {
            event.setCanceled(true);
            return;
        }
        if (vanillaHardness == 0F) return;

        CBCArmorClassifier.SiegeBreakWindow window =
                CBCArmorClassifier.siegeBreakWindow(level, state, pos);

        boolean correctTool = player.hasCorrectToolForDrops(state);
        int divisor = correctTool ? 30 : 100;

        // Vanilla progress is speed / hardness / divisor per tick.
        // Upper speed bound = minimum break time: Efficiency/Haste cannot
        // trivialize a wall.
        float maxAllowedSpeed =
                vanillaHardness * divisor / (window.minimumSeconds() * 20F);
        float adjusted = Math.min(event.getNewSpeed(), maxAllowedSpeed);

        // With the correct tool, also apply the resistance ceiling. This keeps
        // extreme/modded fortifications breachable by infantry as a fallback,
        // while wrong-tool mining can still be much slower. Highest CBC tier:
        // 8s minimum, 10s maximum.
        if (correctTool) {
            float minAllowedSpeed =
                    vanillaHardness * divisor / (window.maximumSeconds() * 20F);
            adjusted = Math.max(adjusted, minAllowedSpeed);
        }

        event.setNewSpeed(adjusted);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        WarManager manager = WarManager.get(level.getServer());
        StrategicCity city = manager.cityAtBlock(level.dimension().location(), event.getPos());
        if (city == null) return;

        // The city freezes the instant a siege enters PREPARING. No attacker
        // instant cover and no defender repair/rebuild during PREPARING/ACTIVE.
        if (manager.anyWarForCity(city.id) != null) {
            event.setCanceled(true);
            return;
        }

        // Outside a siege, newly added blocks are fortifications and consume the
        // city's budget. Original snapshotted infrastructure never consumes it.
        if (!manager.registerCityFortification(level, event.getPos())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Strategic city fortification budget reached: "
                                + manager.fortificationCount(city) + "/"
                                + manager.fortificationBudget(city) + " blocks."));
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        WarManager manager = WarManager.get(level.getServer());

        event.getAffectedBlocks().removeIf(pos -> {
            StrategicCity city = manager.cityAtBlock(level.dimension().location(), pos);
            if (city == null) return false;

            // Original city infrastructure is always permanent.
            if (city.isProtected(pos)) return true;

            WarRecord war = manager.anyWarForCity(city.id);

            // PREPARING is a frozen snapshot: TNT, creepers and other explosion
            // sources cannot alter the fortifications before the fight goes live.
            // During ACTIVE, non-permanent fortifications are destructible.
            return war != null && war.phase != WarPhase.ACTIVE;
        });
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        WarManager manager = WarManager.get(level.getServer());

        // Prevent a piston outside the boundary from being used to mutate the
        // frozen battlefield. Scan the piston line plus vanilla's 12-block push
        // limit; this also covers ordinary sticky-piston pulls.
        for (int i = 0; i <= 13; i++) {
            BlockPos check = event.getPos().relative(event.getDirection(), i);
            StrategicCity city = manager.cityAtBlock(level.dimension().location(), check);
            if (city != null && manager.anyWarForCity(city.id) != null) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
