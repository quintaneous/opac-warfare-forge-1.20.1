package com.quin.opacwarfare1201.mixin;

import com.quin.opacwarfare1201.war.WarPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create contraptions bypass ordinary Forge block place/break events while
 * assembled. Stop their actor world mutations/disassembly inside city sieges
 * unless they are defender-origin machinery already belonging to that city.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
public abstract class CreateContraptionSiegeMixin {
    @Inject(method = "tickActors", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void opacWarfare1201$gateActors(CallbackInfo ci) {
        if (!WarPermissions.canCreateContraptionActors((Entity)(Object)this)) ci.cancel();
    }

    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void opacWarfare1201$gateDisassembly(CallbackInfo ci) {
        if (!WarPermissions.canCreateContraptionDisassemble((Entity)(Object)this)) ci.cancel();
    }

    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void opacWarfare1201$gateInteraction(Player player, BlockPos localPos, Direction side,
                                                  InteractionHand hand,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (!WarPermissions.canUseCreateContraption((Entity)(Object)this, player)) {
            cir.setReturnValue(false);
        }
    }
}
