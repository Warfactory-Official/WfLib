package com.wf.wflib.mixin.pda;

import com.wf.wflib.pda.PdaSystem;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.theme.PdaFont;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Canvas.class, remap = false)
public abstract class MixinPdaCanvas {
    @ModifyVariable(method = "vertex", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int wflib$neutralChrome(int original) { return PdaSystem.active() ? PdaFont.chrome(original) : original; }
    @Inject(method = "fontVersion", at = @At("RETURN"), cancellable = true)
    private void wflib$fontVersion(CallbackInfoReturnable<Integer> cir) {
        if (!PdaSystem.active()) {
            return;
        }
        cir.setReturnValue(cir.getReturnValueI() * 2 + (PdaFont.active() ? 1 : 0));
    }
    @ModifyVariable(method = "text(Ljava/lang/CharSequence;IIFFIZ)F", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int wflib$plainInk(int original) { return PdaSystem.active() ? PdaFont.ink(original) : original; }
    @ModifyVariable(method = "text(Lnet/minecraft/util/FormattedCharSequence;FFIZZ)F", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int wflib$styledInk(int original) { return PdaSystem.active() ? PdaFont.ink(original) : original; }
    @ModifyVariable(method = {"width(Lnet/minecraft/util/FormattedCharSequence;)F",
            "text(Lnet/minecraft/util/FormattedCharSequence;FFIZZ)F"}, at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence wflib$styledFont(FormattedCharSequence original) { return PdaSystem.active() ? PdaFont.styled(original) : original; }
}
