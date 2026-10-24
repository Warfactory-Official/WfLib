package com.wf.wflib.mixin;

import com.wf.wflib.ui.input.UiKeyboard;
import com.wf.wflib.wiki.client.WikiAccess;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link UiKeyboard} focus => keys / chars to it, never to key mappings (incl. Esc => pause). */
@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void wflib$uiKey(long window, int key, int scan, int action, int modifiers, CallbackInfo ci) {
        UiKeyboard.Target target = UiKeyboard.focused();
        Minecraft mc = Minecraft.getInstance();
        if (target == null || window != mc.getWindow().getWindow()) {
            return;
        }
        if (mc.screen != null) {
            UiKeyboard.blur();
            return;
        }
        if (action != GLFW.GLFW_RELEASE) {
            target.key(key, scan, modifiers);
        }
        ci.cancel();
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void wflib$wikiKey(long window, int key, int scan, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && window == Minecraft.getInstance().getWindow().getWindow()
                && UiKeyboard.focused() == null && WikiAccess.key(key, scan)) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void wflib$uiChar(long window, int codepoint, int modifiers, CallbackInfo ci) {
        UiKeyboard.Target target = UiKeyboard.focused();
        Minecraft mc = Minecraft.getInstance();
        if (target == null || window != mc.getWindow().getWindow() || mc.screen != null) {
            return;
        }
        target.character(codepoint, modifiers);
        ci.cancel();
    }
}
