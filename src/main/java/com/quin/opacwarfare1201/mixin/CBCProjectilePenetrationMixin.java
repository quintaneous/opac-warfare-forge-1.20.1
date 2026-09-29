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

/**
 * CBC 5.11.x on Minecraft 1.20.1 does not use ProjectileDamageHooks.
 * Instead, the big-cannon and autocannon projectile classes decide whether a
 * hit block is "unbreakable" by reading BlockState#getDestroySpeed.
 *
 * Returning -1 for protected OPaC claims makes CBC treat the target block as
 * unbreakable, so the projectile follows its normal protected-block behavior
 * instead of destroying or accumulating damage on the block.
 */
@Pseudo
@Mixin(targets = {
        "rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile",
        "rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile"
}, remap = false)
public abstract class CBCProjectilePenetrationMixin {

    @Redirect(
            method = "calculateBlockPenetration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
            ),
            require = 0
    )
    private float opacWarfare1201$protectClaimedBlock(BlockState state, BlockGetter getter, BlockPos pos) {
        if (getter instanceof Level level && !WarPermissions.canCbcDamageTerrain(level, pos)) {
            return -1.0F;
        }
        return state.getDestroySpeed(getter, pos);
    }
}
