package com.wf.wflib.mixin.physics;

import com.wf.wflib.physics.api.DeckAttachment;
import com.wf.wflib.physics.api.DeckRejoin;
import com.wf.wflib.physics.api.VehicleParenting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDeck {

    @Shadow
    protected int lerpSteps;

    @Shadow
    protected abstract void updateWalkAnimation(float partialTick);

    /**
     * Carried rider: force lerpSteps=1. Vanilla's 1/3-per-tick lerp vs hull's 1-tick-stale pose
     * renders several blocks astern on a fast carrier; render still interpolates, no smoothness cost.
     */
    @Inject(method = "lerpTo", at = @At("TAIL"))
    private void wflib$snapCarriedRider(double x, double y, double z, float yRot, float xRot, int steps,
                                        CallbackInfo ci) {
        if (this.lerpSteps <= 1) {
            return;
        }
        DeckAttachment attachment = VehicleParenting.attachmentOf((Entity) (Object) this);
        // Only a gripped, moving carry; parked hull keeps vanilla lerp.
        if (attachment != null && attachment.gripped() && attachment.lastCarrySpeedSqr() > 0) {
            this.lerpSteps = 1;
        }
    }

    /** World-space walk anim reads a moving deck as walking; measure step in hull frame instead. */
    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void wflib$deckWalkAnimation(boolean includeHeight, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        DeckAttachment attachment = VehicleParenting.attachmentOf(self);
        if (attachment == null || !attachment.gripped()) {
            return;
        }
        updateWalkAnimation(VehicleParenting.walkStep(self, attachment, includeHeight));
        ci.cancel();
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void wflib$holdForDeck(Vec3 travelVector, CallbackInfo ci) {
        if (DeckRejoin.hold((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
