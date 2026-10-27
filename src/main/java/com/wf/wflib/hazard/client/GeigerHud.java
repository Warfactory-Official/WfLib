package com.wf.wflib.hazard.client;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.WFLib;
import com.wf.wflib.hazard.HazardContent;
import com.wf.wflib.hazard.LivingHazardProps;
import com.wf.wflib.ui.Binder;
import com.wf.wflib.ui.UiEval;
import com.wf.wflib.ui.UiHud;
import com.wf.wflib.ui.UiInstance;
import com.wf.wflib.ui.UiLayout;
import com.wf.wflib.ui.Widget;
import com.wf.wflib.ui.WidgetDef;
import com.wf.wflib.ui.widget.Point;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import static com.wf.wflib.ui.Ui.layout;
import static com.wf.wflib.ui.Ui.place;

/**
 * Geiger LCD, bottom-left (under chat's bottom line, h - 40). Replaces NTM {@code RadiationHudRenderer}.
 * Rate = synced {@code radBuf} (field, unshielded); dose = {@code radiation}.
 */
public final class GeigerHud {
    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "geiger");
    private static UiInstance instance;

    private GeigerHud() {
    }

    public static void register() {
        UiLayout layout = layout(LAYOUT, place("4", "ui.height - 4", Point.BOTTOM_LEFT, new Lcd()));
        UiHud.addSource(UiHud.Layer.MAIN, out -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || !player.getInventory().contains(HazardContent.GEIGER_COUNTER)) return;
            if (instance == null) instance = layout.instantiate();
            out.add(instance);
        });
    }

    /** Bits a..g = top, upper right, lower right, bottom, lower left, upper left, middle. */
    static final int[] DIGIT = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
    static final int SEG_O = 0x3F, SEG_L = 0x38;
    private static final double[] POW10 = {1, 10, 100, 1000};

    /**
     * 4 digits, autoranged: {@code d.ddd / dd.dd / ddd.d / dddd}, >= 9999.5 => {@code OL}.
     * Returns digit index carrying the point, 3 = none.
     */
    static int format(double v, int[] masks) {
        for (int dp = 0; dp < 4; dp++) {
            long n = Math.round(Math.max(0, v) * POW10[3 - dp]);
            if (n >= 10000) continue;
            for (int i = 3; i >= 0; i--) {
                masks[i] = DIGIT[(int) (n % 10)];
                n /= 10;
            }
            return dp;
        }
        masks[0] = 0;
        masks[1] = SEG_O;
        masks[2] = SEG_L;
        masks[3] = 0;
        return 3;
    }

    /** Bar segments lit: log10 rate over [0.1, 10^4). */
    static int bars(double rate, int segments) {
        if (!(rate > 0)) return 0;
        double f = (Math.log10(rate) + 1) / 5;
        return Math.max(1, Math.min(segments, (int) Math.ceil(f * segments)));
    }

    private record Lcd() implements WidgetDef {
        static final float W = 96, H = 32, BEZEL = 2;
        static final int CASE_EDGE = 0xFF151613, CASE = 0xFF33352F;
        static final int GLASS_TOP = 0xFFA8B499, GLASS_BOTTOM = 0xFF8D9A7F, GLASS_SHADE = 0x38000000;
        static final int INK = 0xE0182012, GHOST = 0x18182012, INK_SHADOW = 0x28182012;
        static final float SLANT = 0.1f;
        static final int BAR_SEGMENTS = 14;

        @Override
        public Widget create(Binder binder) {
            return new Widget() {
                final int[] rate = new int[4];
                final int[] dose = new int[4];
                final float[] hex = new float[12];

                @Override
                public void measure(UiEval ev) {
                    width = W;
                    height = H;
                }

                @Override
                public void draw(UiEval ev, float x, float y, float bw, float bh) {
                    Canvas c = ev.canvas;
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player == null) return;
                    LivingHazardProps props = LivingHazardProps.getData(player);
                    double r = props.radBuf;

                    c.roundRect(x, y, x + W, y + H, 2.5f, CASE_EDGE);
                    c.roundRect(x + 0.6f, y + 0.6f, x + W - 0.6f, y + H - 0.6f, 2, CASE);
                    float gx = x + BEZEL, gy = y + BEZEL, gw = W - 2 * BEZEL, gh = H - 2 * BEZEL;
                    c.gradient(gx, gy, gx + gw, gy + gh, GLASS_TOP, GLASS_BOTTOM);
                    c.fill(gx, gy, gx + gw, gy + 0.7f, GLASS_SHADE);
                    c.fill(gx, gy, gx + 0.7f, gy + gh, GLASS_SHADE);

                    int rdp = format(r, rate);
                    number(c, gx + 3, gy + 3, 9, 14, 1.8f, 11, rate, rdp);

                    float tx = gx + 56, ty = gy + 9;
                    // Steady >= 1 RAD/s, 2 Hz blink >= 10.
                    boolean alarm = r >= 10;
                    boolean lit = r >= 1 && (!alarm || Util.getMillis() / 250 % 2 == 0);
                    trefoil(c, tx, ty, 5.5f, lit ? INK : GHOST);
                    legend(c, "RAD/s", gx + 65, gy + 4);
                    legend(c, "DOSE", gx + 52, gy + 20);

                    int lit2 = bars(r, BAR_SEGMENTS);
                    float bx = gx + 3, by = gy + 21;
                    for (int i = 0; i < BAR_SEGMENTS; i++) {
                        float sx = bx + i * 3;
                        c.fill(sx, by - (i * 0.25f), sx + 2, by + 4, i < lit2 ? INK : GHOST);
                    }

                    int ddp = format(props.radiation, dose);
                    number(c, gx + 66, gy + 17, 4.5f, 8, 1.1f, 5.5f, dose, ddp);
                }

                private void legend(Canvas c, String s, float x, float y) {
                    c.push().translate(x, y).scale(0.5f);
                    c.text(s, 0, 0, INK, false);
                    c.pop();
                }

                private void number(Canvas c, float x, float y, float w, float h, float t, float pitch, int[] masks,
                                    int dp) {
                    for (int i = 0; i < 4; i++) {
                        float dx = x + i * pitch;
                        digit(c, dx + t * 0.3f, y + t * 0.3f, w, h, t, masks[i], INK_SHADOW, 0);
                        digit(c, dx, y, w, h, t, masks[i], INK, GHOST);
                        float px = dx + w + (pitch - w) * 0.5f - t * 0.5f, py = y + h - t;
                        c.fill(px, py, px + t, py + t, i == dp && dp < 3 ? INK : GHOST);
                    }
                }

                /** {@code off} 0 => unlit segments skipped. */
                private void digit(Canvas c, float x, float y, float w, float h, float t, int mask, int on, int off) {
                    float g = t * 0.2f, half = t * 0.5f, mid = y + h * 0.5f;
                    float x0 = x + half, x1 = x + w - half;
                    float y0 = y + half, y1 = y + h - half;
                    seg(c, x0 + g, y0, x1 - g, y0, half, y + h, (mask & 1) != 0 ? on : off);
                    seg(c, x1, y0 + g, x1, mid - g, half, y + h, (mask & 2) != 0 ? on : off);
                    seg(c, x1, mid + g, x1, y1 - g, half, y + h, (mask & 4) != 0 ? on : off);
                    seg(c, x0 + g, y1, x1 - g, y1, half, y + h, (mask & 8) != 0 ? on : off);
                    seg(c, x0, mid + g, x0, y1 - g, half, y + h, (mask & 16) != 0 ? on : off);
                    seg(c, x0, y0 + g, x0, mid - g, half, y + h, (mask & 32) != 0 ? on : off);
                    seg(c, x0 + g, mid, x1 - g, mid, half, y + h, (mask & 64) != 0 ? on : off);
                }

                /** Pointed hexagon (ax,ay)-(bx,by), axis-aligned, sheared by {@link #SLANT} about {@code base}. */
                private void seg(Canvas c, float ax, float ay, float bx, float by, float half, float base, int argb) {
                    if (argb == 0) return;
                    float ux = ax == bx ? 0 : half, uy = ax == bx ? half : 0;
                    float nx = uy, ny = ux;
                    point(0, ax, ay, base);
                    point(1, ax + ux + nx, ay + uy - ny, base);
                    point(2, bx - ux + nx, by - uy - ny, base);
                    point(3, bx, by, base);
                    point(4, bx - ux - nx, by - uy + ny, base);
                    point(5, ax + ux - nx, ay + uy + ny, base);
                    c.fan(hex, 6, argb);
                }

                private void point(int i, float px, float py, float base) {
                    hex[i * 2] = px + (base - py) * SLANT;
                    hex[i * 2 + 1] = py;
                }

                private void trefoil(Canvas c, float cx, float cy, float r, int argb) {
                    float inner = r * 0.3f;
                    for (int i = 0; i < 3; i++) {
                        float mid = (float) Math.toRadians(90 + i * 120);
                        c.arc(cx, cy, (r + inner) * 0.5f, r - inner, mid - (float) Math.PI / 6, (float) Math.PI / 3,
                                argb);
                    }
                    c.disc(cx, cy, inner * 0.6f, argb);
                }
            };
        }
    }
}
