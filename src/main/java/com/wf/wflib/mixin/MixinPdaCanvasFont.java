package com.wf.wflib.mixin;

import com.wf.wflib.pda.client.theme.PdaFont;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** GemRender has no public font selector. Scope the default font at its shared lookup. */
@Mixin(targets = "com.wf.gemrender.ui.CanvasFont", remap = false)
public abstract class MixinPdaCanvasFont {
    @ModifyVariable(method = "fontSet(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/gui/font/FontSet;",
            at = @At("HEAD"), argsOnly = true)
    private ResourceLocation wflib$pdaFont(ResourceLocation original) { return PdaFont.font(original); }
}
