package com.wf.wflib.mixin.ui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wf.wflib.ui.api.EmbeddedScreen;
import com.wf.wflib.ui.UiSystem;
import com.wf.wflib.ui.api.input.MouseSteer;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Registered {@link MouseSteer}s (raised PDA cursor, TV link) own the mouse. Priority > default: outermost wrap, ahead of a vehicle mod's
 * turret-aim wrap.
 */
@Mixin(value = MouseHandler.class, priority = 1500)
public abstract class MixinMouseHandler {

    @WrapOperation(method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void wflib$steer(LocalPlayer player, double yaw, double pitch, Operation<Void> original) {
        if (UiSystem.active()) {
            for (MouseSteer steer : MouseSteer.STEERS.values()) {
                if (steer.steer(yaw, pitch)) {
                    return;
                }
            }
        }
        original.call(player, yaw, pitch);
    }

    /** Embedded screen running: its virtual pointer. */
    @Inject(method = "xpos", at = @At("HEAD"), cancellable = true)
    private void wflib$embeddedX(CallbackInfoReturnable<Double> cir) {
        EmbeddedScreen embedded = UiSystem.active() ? EmbeddedScreen.current() : null;
        if (embedded != null) {
            cir.setReturnValue(embedded.mouseX());
        }
    }

    @Inject(method = "ypos", at = @At("HEAD"), cancellable = true)
    private void wflib$embeddedY(CallbackInfoReturnable<Double> cir) {
        EmbeddedScreen embedded = UiSystem.active() ? EmbeddedScreen.current() : null;
        if (embedded != null) {
            cir.setReturnValue(embedded.mouseY());
        }
    }

    /** Embedded screen (JourneyMap drag start) => real mouse untouched; would drop a PDA cursor delta. */
    @Inject(method = "setIgnoreFirstMove", at = @At("HEAD"), cancellable = true)
    private void wflib$embeddedIgnore(CallbackInfo ci) {
        if (UiSystem.active() && EmbeddedScreen.current() != null) {
            ci.cancel();
        }
    }
}
