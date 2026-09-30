package com.quin.opacwarfare1201.compat;

import com.quin.opacwarfare1201.war.StrategicCity;
import com.quin.opacwarfare1201.war.WarManager;
import com.quin.opacwarfare1201.war.WarPhase;
import com.quin.opacwarfare1201.war.WarRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
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
        if (war == null) return;

        // PREPARING freezes the battlefield completely.
        if (war.phase != WarPhase.ACTIVE) {
            event.setCanceled(true);
            return;
        }

        // During ACTIVE, only living/eligible attackers may hand-breach
        // player-built fortifications. Defenders cannot repair by mining and
        // replacing blocks mid-siege.
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !manager.isAttacker(war, player.getUUID())
                || !manager.isParticipant(war, player.getUUID(), true)) {
            event.setCanceled(true);
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

        // Keep vanilla-unbreakable blocks unbreakable. Zero-hardness blocks
        // are left alone because vanilla treats them as effectively instant.
        if (vanillaHardness < 0F) {
            event.setCanceled(true);
            return;
        }
        if (vanillaHardness == 0F) return;

        float minimumSeconds = CBCArmorClassifier.minimumSiegeBreakSeconds(level, state, pos);
        int divisor = player.hasCorrectToolForDrops(state) ? 30 : 100;

        // Vanilla break progress is speed / hardness / divisor per tick.
        // Capping speed this way guarantees the CBC-derived minimum break
        // time even with Efficiency V, Haste, or very fast modded tools.
        float maxAllowedSpeed = vanillaHardness * divisor / (minimumSeconds * 20F);
        event.setNewSpeed(Math.min(event.getNewSpeed(), maxAllowedSpeed));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        WarManager manager = WarManager.get(level.getServer());
        StrategicCity city = manager.cityAtBlock(level.dimension().location(), event.getPos());
        if (city == null) return;

        // No construction by either side from the moment a city war enters PREPARING
        // until it finishes. This prevents attackers from advancing behind instant walls
        // and prevents defenders from endlessly repairing/rebuilding during the siege.
        if (manager.anyWarForCity(city.id) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        WarManager manager = WarManager.get(level.getServer());

        // Permanent city infrastructure never becomes destructible. Player-built
        // fortifications are intentionally left in the explosion list so siege weapons
        // can remove them during an ACTIVE city battle.
        event.getAffectedBlocks().removeIf(pos ->
                manager.isProtectedCityBlock(level.dimension().location(), pos));
    }
}
