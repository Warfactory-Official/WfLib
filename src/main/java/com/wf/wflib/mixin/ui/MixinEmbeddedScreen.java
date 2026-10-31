package com.wf.wflib.mixin.ui;

import com.wf.wflib.ui.UiSystem;
import com.wf.wflib.ui.api.EmbeddedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link EmbeddedScreen} capture: screens it opens (or closes to) stay embedded. */
@Mixin(Minecraft.class)
public abstract class MixinEmbeddedScreen {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void wflib$embed(Screen screen, CallbackInfo ci) {
        if (!UiSystem.active()) {
            return;
        }
        if (EmbeddedScreen.capture(screen)) {
            ci.cancel();
        }
    }
}
