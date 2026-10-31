package com.wf.wflib.mixin.ui;

import com.wf.wflib.ui.UiSystem;
import com.wf.wflib.ui.api.EmbeddedScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In-world blur processes and rebinds the real main target: never inside an {@link EmbeddedScreen}. */
@Mixin(Screen.class)
public abstract class MixinScreenBlur {
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void wflib$noBlurEmbedded(float partialTick, CallbackInfo ci) {
        if (!UiSystem.active()) {
            return;
        }
        if (EmbeddedScreen.current() != null) {
            ci.cancel();
        }
    }
}
