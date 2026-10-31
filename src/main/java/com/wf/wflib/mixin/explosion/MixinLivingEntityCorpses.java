package com.wf.wflib.mixin.explosion;

import com.wf.wflib.explosion.BlastCorpses;
import com.wf.wflib.explosion.ExplosionSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds a blast-thrown corpse's death removal. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityCorpses {

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void wflib$holdThrownCorpse(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (ExplosionSystem.active() && !self.level().isClientSide && BlastCorpses.holdDeath(self)) {
            ci.cancel();
        }
    }
}
