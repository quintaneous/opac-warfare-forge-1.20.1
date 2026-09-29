package com.quin.opacwarfare1201.mixin;

import com.quin.opacwarfare1201.war.WarPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create Big Cannons 5.11.x performs terrain damage outside normal vanilla break/explosion paths.
 * This optional string-target Mixin closes that path without making CBC a hard build dependency.
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.ProjectileDamageHooks", remap = false)
public abstract class CBCProjectileDamageHooksMixin {
    @Inject(method = "canDamageTerrain", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void opacWarfare1201$protectClaimedTerrain(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!WarPermissions.canCbcDamageTerrain(level, pos)) cir.setReturnValue(false);
    }
}
