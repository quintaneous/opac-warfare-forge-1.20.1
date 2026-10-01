package com.quin.opacwarfare1201.mixin;

import com.quin.opacwarfare1201.war.WarPermissions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tags CBC projectiles with the nation at their launch position and exposes
 * that nation only while CBC executes synchronous terrain-damage logic.
 */
@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile", remap = false)
public abstract class CBCCannonProjectileContextMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 0)
    private void opacWarfare1201$tagProjectile(CallbackInfo ci) {
        WarPermissions.tagCbcProjectile((Entity)(Object)this);
    }

    @Inject(method = "clipAndDamage", at = @At("HEAD"), remap = false, require = 0)
    private void opacWarfare1201$enterProjectileContext(CallbackInfo ci) {
        WarPermissions.enterCbcProjectile((Entity)(Object)this);
    }

    @Inject(method = "clipAndDamage", at = @At("RETURN"), remap = false, require = 0)
    private void opacWarfare1201$exitProjectileContext(CallbackInfo ci) {
        WarPermissions.exitCbcProjectile();
    }
}
