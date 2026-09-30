package com.quin.opacwarfare1201.compat;

import com.quin.opacwarfare1201.war.WarManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CityProtectionEvents {
    private CityProtectionEvents() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (WarManager.get(level.getServer()).isProtectedCityBlock(level.dimension().location(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        WarManager manager = WarManager.get(level.getServer());
        event.getAffectedBlocks().removeIf(pos ->
                manager.isProtectedCityBlock(level.dimension().location(), pos));
    }
}
