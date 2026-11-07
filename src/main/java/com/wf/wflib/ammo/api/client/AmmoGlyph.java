package com.wf.wflib.ammo.api.client;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ammo.api.AmmoKind;
import com.wf.wflib.ammo.api.AmmoVariant;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Chip for the {@link #VARIANT} element of a {@code repeat}: {@link AmmoKind} glyph on the load's tip colour (dark chip
 * without one). Chip, not tinted glyph: black AP tips vanish on a dark HUD. JSON {@code ammo_glyph}; list signal via
 * {@code Signals.items(.., VARIANT)}.
 */
public record AmmoGlyph(float size, String alpha) implements WidgetDef {
    public static final Subject<AmmoVariant> VARIANT = Subject.register("ammo_variant", AmmoVariant.class);
    private static final Map<AmmoVariant, AmmoKind> KINDS = new IdentityHashMap<>();
    private static final AmmoKind[] KIND_VALUES = AmmoKind.values();
    private static final int CHIP_RGB = 0x141412;
    private static final int EDGE_RGB = 0xE8E8E0;

    static {
        Signals.num(VARIANT, "kind", v -> KINDS.computeIfAbsent(v, AmmoKind::of).ordinal());
        Signals.num(VARIANT, "tip", AmmoVariant::tip);
    }

    @Override
    public Widget create(Binder binder) {
        NumProp kind = binder.num("ammo_variant.kind");
        NumProp tip = binder.num("ammo_variant.tip");
        NumProp opacity = binder.num(alpha);
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                width = size;
                height = size;
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                float a = opacity.getFloat(ev);
                int rgb = (int) tip.getFloat(ev);
                drawChip(ev.canvas, KIND_VALUES[(int) kind.getFloat(ev)], rgb, x, y, size, a);
            }
        };
    }

    public static void drawChip(Canvas c, AmmoVariant variant, float x, float y, float s, float alpha) {
        drawChip(c, KINDS.computeIfAbsent(variant, AmmoKind::of), variant.tip(), x, y, s, alpha);
    }

    /** {@code tip} rgb, {@link AmmoVariant#NONE} => dark chip. */
    public static void drawChip(Canvas c, AmmoKind kind, int tip, float x, float y, float s, float alpha) {
        int a = Math.round(alpha * 255) << 24;
        boolean tipped = tip != AmmoVariant.NONE;
        int back = tipped ? tip & 0xFFFFFF : CHIP_RGB;
        c.roundRect(x, y, x + s, y + s, s * 0.15F, tipped ? a | back : Math.round(alpha * 224) << 24 | back);
        c.roundFrame(x, y, x + s, y + s, s * 0.15F, s * 0.06F, Math.round(alpha * 112) << 24 | EDGE_RGB);
        float luma = 0.299F * (back >> 16 & 255) + 0.587F * (back >> 8 & 255) + 0.114F * (back & 255);
        drawGlyph(c, kind, x, y, s / 8, a | (luma > 150 ? 0x101010 : 0xF2F2EA));
    }

    /** Cell grid 8 x 8, {@code u} canvas units per cell. */
    private static void drawGlyph(Canvas c, AmmoKind kind, float x, float y, float u, int argb) {
        switch (kind) {
            case BALL -> bullet(c, x, y, u, 1.2F, argb);
            case TRACER -> {
                c.line(x + 1.4F * u, y + 6.6F * u, x + 5 * u, y + 3 * u, 0.8F * u, argb);
                c.line(x + 1.2F * u, y + 4.4F * u, x + 3.2F * u, y + 2.4F * u, 0.6F * u, argb);
                c.line(x + 3.6F * u, y + 6.8F * u, x + 5.6F * u, y + 4.8F * u, 0.6F * u, argb);
                c.disc(x + 5.6F * u, y + 2.4F * u, 1.2F * u, argb);
            }
            case AP -> {
                c.triangle(x + 4 * u, y + u, x + 3 * u, y + 3.2F * u, x + 5 * u, y + 3.2F * u, argb);
                c.fill(x + 3.6F * u, y + 3.2F * u, x + 4.4F * u, y + 7 * u, argb);
                c.triangle(x + 3.6F * u, y + 5 * u, x + 3.6F * u, y + 7 * u, x + 2.2F * u, y + 7 * u, argb);
                c.triangle(x + 4.4F * u, y + 5 * u, x + 5.8F * u, y + 7 * u, x + 4.4F * u, y + 7 * u, argb);
            }
            case HE -> {
                c.disc(x + 4 * u, y + 4 * u, 1.3F * u, argb);
                for (int i = 0; i < 8; i++) {
                    float angle = Canvas.TAU * i / 8;
                    float dx = (float) Math.cos(angle);
                    float dy = (float) Math.sin(angle);
                    c.line(x + (4 + 1.3F * dx) * u, y + (4 + 1.3F * dy) * u, x + (4 + 3.2F * dx) * u,
                            y + (4 + 3.2F * dy) * u, 0.6F * u, argb);
                }
            }
            case HEAT -> {
                c.triangle(x + 1.6F * u, y + 3 * u, x + 6.4F * u, y + 3 * u, x + 4 * u, y + 7 * u, argb);
                c.disc(x + 4 * u, y + 1.5F * u, 0.8F * u, argb);
            }
            case TANDEM -> {
                vee(c, x, y, u, 1.6F, argb);
                vee(c, x, y, u, 4, argb);
            }
            case INCENDIARY -> {
                c.disc(x + 4 * u, y + 5 * u, 2.1F * u, argb);
                c.triangle(x + 2 * u, y + 4.5F * u, x + 6 * u, y + 4.5F * u, x + 4.6F * u, y + 0.8F * u, argb);
            }
            case SHOT -> {
                c.disc(x + 4 * u, y + 4 * u, 0.85F * u, argb);
                c.disc(x + 2.1F * u, y + 2.1F * u, 0.85F * u, argb);
                c.disc(x + 5.9F * u, y + 2.1F * u, 0.85F * u, argb);
                c.disc(x + 2.1F * u, y + 5.9F * u, 0.85F * u, argb);
                c.disc(x + 5.9F * u, y + 5.9F * u, 0.85F * u, argb);
            }
            case MISSILE -> {
                c.triangle(x + 4 * u, y + 0.8F * u, x + 3.1F * u, y + 2.6F * u, x + 4.9F * u, y + 2.6F * u, argb);
                c.fill(x + 3.1F * u, y + 2.6F * u, x + 4.9F * u, y + 6 * u, argb);
                c.triangle(x + 3.1F * u, y + 4 * u, x + 3.1F * u, y + 6 * u, x + 1.6F * u, y + 6.8F * u, argb);
                c.triangle(x + 4.9F * u, y + 4 * u, x + 6.4F * u, y + 6.8F * u, x + 4.9F * u, y + 6 * u, argb);
                c.line(x + 4 * u, y + 6 * u, x + 4 * u, y + 7.4F * u, 0.7F * u, argb);
            }
            case SUBSONIC -> {
                bullet(c, x, y, u, 2.6F, argb);
                c.line(x + 1.4F * u, y + 2.2F * u, x + 1.4F * u, y + 6 * u, 0.6F * u, argb);
                c.line(x + 6.6F * u, y + 2.2F * u, x + 6.6F * u, y + 6 * u, 0.6F * u, argb);
            }
        }
    }

    private static void bullet(Canvas c, float x, float y, float u, float top, int argb) {
        float shoulder = top + 2.2F;
        c.fill(x + 2.9F * u, y + shoulder * u, x + 5.1F * u, y + 6.9F * u, argb);
        c.triangle(x + 2.9F * u, y + shoulder * u, x + 5.1F * u, y + shoulder * u, x + 4 * u, y + top * u, argb);
    }

    /** Liner opening up, apex {@code top + 2.6} cells down. */
    private static void vee(Canvas c, float x, float y, float u, float top, int argb) {
        c.line(x + 1.8F * u, y + top * u, x + 4 * u, y + (top + 2.6F) * u, 0.8F * u, argb);
        c.line(x + 6.2F * u, y + top * u, x + 4 * u, y + (top + 2.6F) * u, 0.8F * u, argb);
    }
}
