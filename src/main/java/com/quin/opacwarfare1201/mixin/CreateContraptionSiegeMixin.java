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

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
public abstract class CreateContraptionSiegeMixin {
    @Inject(method = "tickActors", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gateActors(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (!WarPermissions.canCreateContraptionActors(self)) {
            ci.cancel();
            return;
        }
        WarPermissions.enterCreateContraption(self);
    }

    @Inject(method = "tickActors", at = @At("RETURN"), remap = false, require = 0)
    private void finishActors(CallbackInfo ci) {
        WarPermissions.exitCreateContraption();
    }

    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gateDisassembly(CallbackInfo ci) {
        if (!WarPermissions.canCreateContraptionDisassemble((Entity)(Object)this)) ci.cancel();
    }

    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gateInteraction(Player player, BlockPos localPos, Direction side,
                                 InteractionHand hand,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (!WarPermissions.canUseCreateContraption((Entity)(Object)this, player)) {
            cir.setReturnValue(false);
        }
    }
}
