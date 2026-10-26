package com.wf.wflib.mixin;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.pda.client.theme.PdaFont;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Canvas.class, remap = false)
public abstract class MixinPdaCanvas {
    @ModifyVariable(method = "vertex", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int wflib$neutralChrome(int original) { return PdaFont.chrome(original); }
    @Inject(method = "fontVersion", at = @At("RETURN"), cancellable = true)
    private void wflib$fontVersion(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValueI() * 2 + (PdaFont.active() ? 1 : 0));
    }
    @ModifyVariable(method = "text(Ljava/lang/CharSequence;IIFFIZ)F", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int wflib$plainInk(int original) { return PdaFont.ink(original); }
    @ModifyVariable(method = "text(Lnet/minecraft/util/FormattedCharSequence;FFIZZ)F", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int wflib$styledInk(int original) { return PdaFont.ink(original); }
    @ModifyVariable(method = {"width(Lnet/minecraft/util/FormattedCharSequence;)F",
            "text(Lnet/minecraft/util/FormattedCharSequence;FFIZZ)F"}, at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence wflib$styledFont(FormattedCharSequence original) { return PdaFont.styled(original); }
}
