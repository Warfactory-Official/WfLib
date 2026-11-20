package com.wf.wflib.ui.api.widget;

import com.mojang.blaze3d.platform.NativeImage;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/**
 * Film look over {@code w x h}: grain (a fresh random window of one noise texture per draw), a stepped vignette and
 * faint scanlines every second row. Size = {@code w x h}.
 */
public record FilmFx(String w, String h) implements WidgetDef {
    private static final int GRAIN_SIZE = 512;
    private static final ResourceLocation GRAIN_ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "film_grain");
    private static final Random RANDOM = new Random();
    private static DynamicTexture grain;

    @Override
    public Widget create(Binder binder) {
        NumProp pw = binder.num(w);
        NumProp ph = binder.num(h);
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                width = Math.max(0, pw.getFloat(ev));
                height = Math.max(0, ph.getFloat(ev));
            }

            @Override
            public void draw(UiEval ev, float x, float y, float bw, float bh) {
                Canvas canvas = ev.canvas;
                ensureGrain();
                int sw = Math.min(GRAIN_SIZE, (int) (width * 3 / 4));
                int sh = Math.min(GRAIN_SIZE, (int) (height * 3 / 4));
                float u0 = RANDOM.nextInt(GRAIN_SIZE - sw + 1) / (float) GRAIN_SIZE;
                float v0 = RANDOM.nextInt(GRAIN_SIZE - sh + 1) / (float) GRAIN_SIZE;
                canvas.sprite(GRAIN_ID, x, y, x + width, y + height, u0, v0, u0 + sw / (float) GRAIN_SIZE,
                        v0 + sh / (float) GRAIN_SIZE, 0xFFFFFFFF);
                int depth = (int) Math.max(8, Math.min(width, height) / 6);
                for (int i = 0; i < depth; i += 2) {
                    float t = 1.0f - (float) i / depth;
                    int c = (int) (70.0f * t * t) << 24;
                    canvas.fill(x + i, y + i, x + width - i, y + i + 2, c);
                    canvas.fill(x + i, y + height - i - 2, x + width - i, y + height - i, c);
                    canvas.fill(x + i, y + i + 2, x + i + 2, y + height - i - 2, c);
                    canvas.fill(x + width - i - 2, y + i + 2, x + width - i, y + height - i - 2, c);
                }
                for (float row = 0; row < height; row += 2) {
                    canvas.fill(x, y + row, x + width, y + row + 1, 0x14000000);
                }
            }
        };
    }

    private static void ensureGrain() {
        if (grain != null) {
            return;
        }
        NativeImage image = new NativeImage(GRAIN_SIZE, GRAIN_SIZE, false);
        Random r = new Random(7);
        for (int py = 0; py < GRAIN_SIZE; py++) {
            for (int px = 0; px < GRAIN_SIZE; px++) {
                float a = r.nextFloat();
                int alpha = (int) (a * a * 18);
                int v = r.nextBoolean() ? 0xFF : 0x00;
                image.setPixelRGBA(px, py, (alpha << 24) | (v << 16) | (v << 8) | v);
            }
        }
        grain = new DynamicTexture(image);
        grain.setFilter(false, false);
        Minecraft.getInstance().getTextureManager().register(GRAIN_ID, grain);
    }
}
