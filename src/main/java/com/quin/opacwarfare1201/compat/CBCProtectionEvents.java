package com.quin.opacwarfare1201.compat;

import com.quin.opacwarfare1201.war.WarPermissions;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Forge-side protection for CBC custom explosion block lists. */
public final class CBCProtectionEvents {
    private CBCProtectionEvents() {}

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!isCbcExplosion(event.getExplosion())) return;
        event.getAffectedBlocks().removeIf(pos -> !WarPermissions.canCbcDamageTerrain(event.getLevel(), pos));
    }

    private static boolean isCbcExplosion(Explosion explosion) {
        Class<?> type = explosion.getClass();
        while (type != null) {
            if (type.getName().startsWith("rbasamoyai.createbigcannons.")) return true;
            type = type.getSuperclass();
        }
        return false;
    }
}
