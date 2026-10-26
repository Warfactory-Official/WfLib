package com.wf.wflib.mixin;

import com.wf.wflib.pda.client.theme.PdaFont;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.renderer.RenderType;
import java.util.Map;
import java.util.WeakHashMap;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** GemRender has no public font selector. Scope the default font at its shared lookup. */
@Mixin(targets = "com.wf.gemrender.ui.CanvasFont", remap = false)
public abstract class MixinPdaCanvasFont {
    @Unique private final Map<RenderType, Boolean> wflib$intensityTypes = new WeakHashMap<>();
    /** Composite render types append their state to the name. R8 glyph pages must use the intensity shader. */
    @Inject(method = "texture", at = @At("RETURN"), cancellable = true)
    private void wflib$intensityPage(BakedGlyph glyph, CallbackInfoReturnable<Integer> cir) {
        if (!PdaFont.active()) return;
        RenderType type = glyph.renderType(Font.DisplayMode.NORMAL);
        boolean intensity = wflib$intensityTypes.computeIfAbsent(type, key -> {
            String name = key.toString();
            return name.equals("text_intensity") || name.startsWith("text_intensity:") || name.startsWith("RenderType[text_intensity:");
        });
        if (intensity) cir.setReturnValue(cir.getReturnValueI() | 0x80000000);
    }
    @ModifyVariable(method = "fontSet(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/gui/font/FontSet;",
            at = @At("HEAD"), argsOnly = true)
    private ResourceLocation wflib$pdaFont(ResourceLocation original) { return PdaFont.font(original); }
}
