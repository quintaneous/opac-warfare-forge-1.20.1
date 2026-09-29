package com.quin.opacwarfare1201.mixin;

import com.quin.opacwarfare1201.war.WarPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent CBC impact/shell transformation effects (for example stone -> cobblestone) in protected claims. */
@Pseudo
@Mixin(targets = {
        "rbasamoyai.createbigcannons.munitions.ImpactExplosion",
        "rbasamoyai.createbigcannons.munitions.ShellExplosion",
        "rbasamoyai.createbigcannons.munitions.big_cannon.mortar_stone.MortarStoneExplosion"
}, remap = false)
public abstract class CBCExplosionEditBlockMixin {
    @Inject(method = "editBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void opacWarfare1201$protectImpactTransform(Level level, BlockPos pos, BlockState blockState,
                                                         FluidState fluidState, float power, CallbackInfo ci) {
        if (!WarPermissions.canCbcDamageTerrain(level, pos)) ci.cancel();
    }
}
