package com.wf.wflib.mixin.cam;

import com.wf.wflib.cam.api.client.CameraEffects;
import io.github.avacadowizard120.cameraoverhaulneoforge.common.systems.CameraSystem;
import io.github.avacadowizard120.cameraoverhaulneoforge.core.structures.Transform;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CameraSystem.class, remap = false)
public abstract class MixinCameraOverhaulGate {

    @Shadow
    private static Transform offsetTransform;

    @Inject(method = "OnCameraUpdate", at = @At("RETURN"))
    private void wflib$gate(Entity focused, Camera camera, Transform transform, float partialTick, CallbackInfo ci) {
        float w = CameraEffects.weight(partialTick);
        if (w < 1.0f) {
            offsetTransform.position = offsetTransform.position.scale(w);
            offsetTransform.eulerRot = offsetTransform.eulerRot.scale(w);
        }
    }
}
