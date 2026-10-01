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
@Mixin(targets = "com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour", remap = false)
public abstract class CreateBlockBreakingSiegeMixin {
    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gateCreateBlockBreaking(Level level, BlockPos pos, BlockState state,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!WarPermissions.canCreateBreakBlock(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
