package com.wf.wflib.mixin.cam;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.wf.wflib.cam.api.client.FeedPass;
import com.wf.wflib.ui.api.EmbeddedScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feed pass / embedded screen: "the main target" = their framebuffer. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftFeedTarget {

    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void wflib$feedTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget target = FeedPass.target();
        EmbeddedScreen embedded = EmbeddedScreen.current();
        if (target == null && embedded != null) {
            target = embedded.target();
        }
        if (target != null) {
            cir.setReturnValue(target);
        }
    }
}
