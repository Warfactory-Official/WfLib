package com.wf.wflib.pda.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.wf.wflib.WFLib;
import com.wf.wflib.pda.music.client.CoverArt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** PDA backdrop as the fixed texture {@link #TEXTURE}: centre-cropped to the screen aspect, box-shrunk. */
final class PdaBackground {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_background");
    private static final int MAX_WIDTH = 768;
    private static boolean present;

    private PdaBackground() {
    }

    static boolean present() {
        return present;
    }

    /** Any thread. */
    @Nullable
    static NativeImage prepare(byte[] bytes) {
        NativeImage src = CoverArt.raw(bytes);
        if (src == null) {
            return null;
        }
        try (src) {
            int w = src.getWidth();
            int h = src.getHeight();
            int cw = w;
            int ch = h;
            if (w * PdaShell.HEIGHT > h * PdaShell.WIDTH) {
                cw = Math.max(1, Math.round(h * PdaShell.WIDTH / PdaShell.HEIGHT));
            } else {
                ch = Math.max(1, Math.round(w * PdaShell.HEIGHT / PdaShell.WIDTH));
            }
            int x0 = (w - cw) / 2;
            int y0 = (h - ch) / 2;
            int f = Math.max(1, cw / MAX_WIDTH);
            int ow = Math.max(1, cw / f);
            int oh = Math.max(1, ch / f);
            NativeImage out = new NativeImage(NativeImage.Format.RGBA, ow, oh, false);
            int n = f * f;
            for (int y = 0; y < oh; y++) {
                for (int x = 0; x < ow; x++) {
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    int a = 0;
                    for (int dy = 0; dy < f; dy++) {
                        for (int dx = 0; dx < f; dx++) {
                            int p = src.getPixelRGBA(x0 + x * f + dx, y0 + y * f + dy);
                            a += p >>> 24;
                            b += p >> 16 & 0xFF;
                            g += p >> 8 & 0xFF;
                            r += p & 0xFF;
                        }
                    }
                    out.setPixelRGBA(x, y, a / n << 24 | b / n << 16 | g / n << 8 | r / n);
                }
            }
            return out;
        }
    }

    /** Render thread. Takes ownership of {@code image}; null = no background. */
    static void show(@Nullable NativeImage image) {
        var textures = Minecraft.getInstance().getTextureManager();
        if (image == null) {
            if (present) {
                textures.release(TEXTURE);
                present = false;
            }
            return;
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        textures.register(TEXTURE, texture);
        present = true;
    }
}
