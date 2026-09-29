package com.quin.opacwarfare1201.mixin;

import com.quin.opacwarfare1201.war.WarPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes claimed blocks behave as unbreakable to CBC's direct big-cannon penetration calculation outside active battles. */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile", remap = false)
public abstract class CBCBigCannonProjectileMixin {
    @Redirect(
            method = "calculateBlockPenetration",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
                    remap = true),
            remap = false,
            require = 1
    )
    private float opacWarfare1201$claimedBlocksActUnbreakable(BlockState state, BlockGetter blockGetter, BlockPos pos) {
        if (blockGetter instanceof Level level && !WarPermissions.canCbcDamageTerrain(level, pos)) return -1.0f;
        return state.getDestroySpeed(blockGetter, pos);
    }
}
