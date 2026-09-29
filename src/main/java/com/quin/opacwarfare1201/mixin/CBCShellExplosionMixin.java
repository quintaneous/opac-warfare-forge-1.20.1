package com.quin.opacwarfare1201.mixin;

import com.quin.opacwarfare1201.war.WarPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * CBC shell explosions can transform impacted blocks (for example stone into
 * damaged variants) after the ordinary explosion-protection path has already
 * run. Gate that explicit setBlock call as well.
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.ShellExplosion", remap = false)
public abstract class CBCShellExplosionMixin {

    @Redirect(
            method = "editBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            ),
            require = 0
    )
    private boolean opacWarfare1201$protectShellTransformation(Level level, BlockPos pos, BlockState state, int flags) {
        if (!WarPermissions.canCbcDamageTerrain(level, pos)) {
            return false;
        }
        return level.setBlock(pos, state, flags);
    }
}
