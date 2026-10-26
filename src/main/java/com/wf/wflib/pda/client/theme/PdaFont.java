package com.wf.wflib.pda.client.theme;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Only the PDA opts into the bundled, unmodified OFL Liberation Sans. */
public final class PdaFont {
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("wflib", "gamma");
    private static boolean measuring;
    private PdaFont() { }
    public static boolean active() { return (PdaThemeState.inPda() || measuring) && PdaThemeState.theme() == PdaTheme.GAMMA; }
    /** Pointer-to-caret runs between frames, outside ThemeScope. Retain the last draw's font metrics. */
    public static float width(com.wf.gemrender.ui.Canvas canvas, CharSequence text, int start, int end, boolean gamma) {
        boolean before = measuring;
        measuring = gamma;
        try { return canvas.width(text, start, end); }
        finally { measuring = before; }
    }
    /** Neutralise legacy widget tints too; real image pixels and cyan map markers retain their colours. */
    public static int chrome(int original) {
        if (!active()) return original;
        int r = original >>> 16 & 255, g = original >>> 8 & 255, b = original & 255;
        if (b > r + 24 && g > r + 24) return original;
        if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 18) return original;
        int grey = (r + g + b) / 3;
        return original & 0xFF000000 | grey << 16 | grey << 8 | grey;
    }
    public static int ink(int original) {
        if (!active()) return original;
        int r = original >>> 16 & 255, g = original >>> 8 & 255, b = original & 255;
        if (b > r + 24 && g > r + 24) return original;
        if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 18) return original;
        int grey = Math.clamp((r + g + b) / 3, 145, 225);
        return original & 0xFF000000 | grey << 16 | grey << 8 | grey;
    }
    public static ResourceLocation font(ResourceLocation original) {
        return active() && Style.DEFAULT_FONT.equals(original) ? FONT : original;
    }
    private static Style style(Style original) {
        Style result = original.withFont(font(original.getFont()));
        return original.getColor() == null ? result : result.withColor(ink(0xFF000000 | original.getColor().getValue()) & 0xFFFFFF);
    }
    public static FormattedCharSequence styled(FormattedCharSequence original) {
        if (!active()) return original;
        return sink -> original.accept((index, style, cp) -> sink.accept(index, style(style), cp));
    }
}
