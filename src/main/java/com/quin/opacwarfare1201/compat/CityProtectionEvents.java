package com.quin.opacwarfare1201.compat;

import com.quin.opacwarfare1201.war.StrategicCity;
import com.quin.opacwarfare1201.war.WarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

        // Once a strategic-city war is declared, the battlefield is frozen:
        // nobody can hand-break player-built/non-permanent blocks until the war ends.
        // Those blocks can still be removed by allowed siege damage while ACTIVE.
        StrategicCity city = manager.cityAtBlock(level.dimension().location(), pos);
        if (city != null && manager.anyWarForCity(city.id) != null) {
            event.setCanceled(true);
        }
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
