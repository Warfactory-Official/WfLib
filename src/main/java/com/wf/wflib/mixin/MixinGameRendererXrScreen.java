package com.wf.wflib.mixin;

import com.wf.wflib.client.xray.XrScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link XrScreen}'s post-process over the level and hands, before the GUI (and with it hidden). */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererXrScreen {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    private void wflib$xrScreen(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        XrScreen.afterLevel(deltaTracker.getGameTimeDeltaPartialTick(true));
    }
}
