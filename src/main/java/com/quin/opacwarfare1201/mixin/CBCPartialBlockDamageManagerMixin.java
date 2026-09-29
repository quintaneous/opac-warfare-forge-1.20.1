package com.quin.opacwarfare1201.mixin;

import com.quin.opacwarfare1201.war.WarPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.base.PartialBlockDamageManager", remap = false)
public abstract class CBCPartialBlockDamageManagerMixin {
    @Inject(method = "damageBlock(Lnet/minecraft/core/BlockPos;FLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void opacWarfare1201$protectClaimedTerrain(BlockPos pos, float added, BlockState state, Level level,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (!WarPermissions.canCbcDamageTerrain(level, pos)) cir.setReturnValue(false);
    }
}
