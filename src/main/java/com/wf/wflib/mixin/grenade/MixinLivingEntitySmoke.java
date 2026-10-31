package com.wf.wflib.mixin.grenade;

import com.wf.wflib.grenade.GrenadeSystem;
import com.wf.wflib.grenade.api.SmokeScreens;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Smoke hides. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySmoke {

    @Inject(method = "hasLineOfSight", at = @At("RETURN"), cancellable = true)
    private void wflib$smokeHides(Entity target, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (GrenadeSystem.active() && cir.getReturnValueZ() && !self.level().isClientSide
                && SmokeScreens.blocked(self.level(), self.getEyePosition(), target.getEyePosition())) {
            cir.setReturnValue(false);
        }
    }
}
