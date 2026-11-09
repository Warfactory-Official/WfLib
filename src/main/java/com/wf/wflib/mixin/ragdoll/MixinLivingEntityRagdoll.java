package com.wf.wflib.mixin.ragdoll;

import com.wf.wflib.ragdoll.api.Ragdolls;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Body owned by the sim neither pushes nor is pushed: a carried body's box sits on its carrier. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityRagdoll {

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void wflib$noPush(CallbackInfo ci) {
        if (Ragdolls.active((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void wflib$notPushable(CallbackInfoReturnable<Boolean> cir) {
        if (Ragdolls.active((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
