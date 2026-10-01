package com.quin.opacwarfare1201.compat;

import com.quin.opacwarfare1201.config.WarConfig;
import com.quin.opacwarfare1201.war.StrategicCity;
import com.quin.opacwarfare1201.war.WarManager;
import com.quin.opacwarfare1201.war.WarPhase;
import com.quin.opacwarfare1201.war.WarRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

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

        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !manager.isParticipant(war, player.getUUID(), true)) {
            event.setCanceled(true);
            return;
        }

        // Defenders may only reposition Create/CBC construction that they
        // personally placed during THIS active siege.
        if (manager.isDefender(war, player.getUUID())) {
            if (!manager.canDefenderRepositionSiegePlacement(war, pos, player.getUUID())) {
                event.setCanceled(true);
                return;
            }
            manager.releaseCityFortification(level, pos);
            return;
        }

        if (!manager.isAttacker(war, player.getUUID())) {
            event.setCanceled(true);
            return;
        }

        // Attacker infantry breaching intentionally yields no block/XP drops.
        event.setCanceled(true);
        event.setExpToDrop(0);
        if (level.destroyBlock(pos, false, player)) {
            manager.releaseCityFortification(level, pos);
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

        WarRecord war = manager.anyWarForCity(city.id);
        if (war != null) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !manager.isParticipant(war, player.getUUID(), true)) {
                event.setCanceled(true);
                return;
            }

            // CBC manual loading physically places projectile/propellant blocks.
            // Treat ammunition as loading state rather than construction. Both
            // sides may load/reload during PREPARING and ACTIVE, and ammunition
            // never consumes the city's fortification budget.
            if (CBCMunitionClassifier.isManualLoadingPlacement(
                    level, event.getPos(), event.getPlacedBlock().getBlock())) {
                return;
            }

            // PREPARING remains a hard freeze: defenders get warning time, but
            // nobody can modify the battlefield before the assault goes live.
            if (war.phase != WarPhase.ACTIVE) {
                event.setCanceled(true);
                return;
            }

            // During ACTIVE, living/eligible defenders may construct with any
            // Create or Create: Big Cannons block. This allows field repairs,
            // mechanical defenses, cannon construction, armor, loaders, etc.
            // These placements ARE fortifications and therefore consume/free the
            // same city budget as pre-war defensive construction.
            if (manager.isDefender(war, player.getUUID())
                    && isCreateOrCbcBlock(event.getPlacedBlock())) {
                if (manager.isInsideCityCaptureNoBuildCore(city, event.getPos())) {
                    event.setCanceled(true);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Structural defenses cannot be placed inside the city capture core."));
                    return;
                }
                if (!manager.registerCityFortificationDuringActiveSiege(level, event.getPos())) {
                    event.setCanceled(true);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Strategic city fortification budget reached: "
                                    + manager.fortificationCount(city) + "/"
                                    + manager.fortificationBudget(city) + " blocks."));
                    return;
                }
                manager.recordSiegePlacement(war, event.getPos(), player.getUUID());
                return;
            }

            // Attackers still cannot build forward positions inside the city,
            // and defenders cannot spam vanilla/general-purpose blocks.
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
            if (city.isProtected(pos)) return true;

            WarRecord war = manager.anyWarForCity(city.id);
            if (war != null && war.phase != WarPhase.ACTIVE) return true;
            if (war != null) manager.releaseCityFortification(level, pos);
            return false;
        });
    }

    private static boolean isCreateOrCbcBlock(BlockState state) {
        var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) return false;
        String namespace = key.getNamespace();
        return "create".equals(namespace) || "createbigcannons".equals(namespace);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        WarManager manager = WarManager.get(level.getServer());

        WarRecord playerWar = manager.activeWarForRosterPlayer(player.getUUID());
        if (playerWar != null && !manager.isParticipant(playerWar, player.getUUID(), true)) {
            if (isCreateOrCbcBlock(level.getBlockState(event.getPos()))) {
                event.setCanceled(true);
                return;
            }
        }

        if (!WarConfig.BLOCK_FLUIDS_DURING_CITY_SIEGE.get()) return;
        if (!(player.getItemInHand(event.getHand()).getItem() instanceof BucketItem)) return;
        if (touchesSiegedCity(manager, level, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (!WarConfig.BLOCK_FLUIDS_DURING_CITY_SIEGE.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        WarManager manager = WarManager.get(level.getServer());
        StrategicCity city = manager.cityAtBlock(level.dimension().location(), event.getPos());
        if (city == null || manager.anyWarForCity(city.id) == null) return;
        event.setNewState(event.getOriginalState());
    }

    private static boolean touchesSiegedCity(WarManager manager, ServerLevel level, BlockPos origin) {
        StrategicCity at = manager.cityAtBlock(level.dimension().location(), origin);
        if (at != null && manager.anyWarForCity(at.id) != null) return true;

        for (var direction : net.minecraft.core.Direction.values()) {
            StrategicCity adjacent = manager.cityAtBlock(
                    level.dimension().location(), origin.relative(direction));
            if (adjacent != null && manager.anyWarForCity(adjacent.id) != null) return true;
        }
        return false;
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
