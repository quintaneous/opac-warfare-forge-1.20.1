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

import java.util.function.BiConsumer;

/**
 * CBC 1.20.1 routes autocannon, shrapnel and several other terrain-damage
 * paths through PartialBlockDamageManager. Gate the final overload so every
 * caller is checked before CBC stores or applies block damage.
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.base.PartialBlockDamageManager", remap = false)
public abstract class CBCPartialBlockDamageMixin {

    @Inject(
            method = "damageBlock(Lnet/minecraft/core/BlockPos;FLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Ljava/util/function/BiConsumer;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void opacWarfare1201$protectClaimedBlock(
            BlockPos pos,
            float added,
            BlockState state,
            Level level,
            BiConsumer<Level, BlockPos> onDestroy,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!WarPermissions.canCbcDamageTerrain(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
