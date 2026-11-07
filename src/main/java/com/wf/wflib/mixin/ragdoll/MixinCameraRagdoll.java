package com.wf.wflib.mixin.ragdoll;

import com.wf.wflib.ragdoll.client.RagdollClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camera on the body: first person at the head, third person orbits the chest. Priority: last word on the pivot. */
@Mixin(value = Camera.class, priority = 1100)
public abstract class MixinCameraRagdoll {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V", ordinal = 0))
    private void wflib$orbitChest(BlockGetter level, Entity entity, boolean detached, boolean mirror, float pt, CallbackInfo ci) {
        double[] chest = detached ? RagdollClient.view(entity, pt, false) : null;
        if (chest != null) {
            setPosition(chest[0], chest[1], chest[2]);
        }
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void wflib$eyeAtHead(BlockGetter level, Entity entity, boolean detached, boolean mirror, float pt, CallbackInfo ci) {
        double[] head = detached ? null : RagdollClient.view(entity, pt, true);
        if (head != null) {
            setPosition(head[0], head[1], head[2]);
        }
    }
}
