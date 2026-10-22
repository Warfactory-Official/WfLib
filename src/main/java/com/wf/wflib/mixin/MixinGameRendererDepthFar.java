package com.wf.wflib.mixin;

import com.wf.wflib.config.ViewRange;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Far plane >= {@link ViewRange} reach (real entities, rounds, FX, sim ghosts); Voxy floor varies. */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererDepthFar {
    @Inject(method = "getDepthFar", at = @At("RETURN"), cancellable = true)
    private void wflib$viewRangeFar(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(Math.max(cir.getReturnValueF(), (float) Math.max(ViewRange.blocks, ViewRange.sim) + 64.0f));
    }
}
