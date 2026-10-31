package com.wf.wflib.sight.api.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Reticle images with a full mip chain (premultiplied box filter): a 256 px holo ring drawn 20 px wide keeps its
 * lines; vanilla {@code SimpleTexture} uploads level 0 only. Linear, transparent border. Freed on resource reload. Render
 * thread.
 */
public final class ReticleTextures {
    private static final Logger LOG = LogUtils.getLogger();
    /** GL id; 0 = unreadable (logged once). */
    private static final Map<ResourceLocation, Integer> IDS = new HashMap<>();

    private ReticleTextures() {
    }

    public static int id(ResourceLocation rl) {
        Integer id = IDS.get(rl);
        if (id == null) {
            id = load(rl);
            IDS.put(rl, id);
        }
        return id;
    }

    private static int load(ResourceLocation rl) {
        var res = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (res.isEmpty()) {
            LOG.warn("reticle {} missing", rl);
            return 0;
        }
        try (InputStream in = res.get().open(); NativeImage base = NativeImage.read(NativeImage.Format.RGBA, in)) {
            int w = base.getWidth(), h = base.getHeight();
            int levels = 31 - Integer.numberOfLeadingZeros(Math.min(w, h));
            int id = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(id, levels, w, h);
            float[] px = new float[w * h * 4];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = base.getPixelRGBA(x, y), i = (y * w + x) * 4;
                    float a = (c >>> 24) / 255F;
                    px[i] = (c & 0xFF) / 255F * a;
                    px[i + 1] = (c >> 8 & 0xFF) / 255F * a;
                    px[i + 2] = (c >> 16 & 0xFF) / 255F * a;
                    px[i + 3] = a;
                }
            }
            for (int l = 0; l <= levels; l++) {
                int lw = w >> l, lh = h >> l;
                if (l > 0) {
                    px = down(px, w >> (l - 1), h >> (l - 1), lw, lh);
                }
                try (NativeImage img = new NativeImage(lw, lh, false)) {
                    for (int y = 0; y < lh; y++) {
                        for (int x = 0; x < lw; x++) {
                            int i = (y * lw + x) * 4;
                            float a = px[i + 3];
                            float k = a > 0 ? 255 / a : 0;
                            img.setPixelRGBA(x, y, Math.round(a * 255) << 24 | channel(px[i + 2] * k) << 16
                                    | channel(px[i + 1] * k) << 8 | channel(px[i] * k));
                        }
                    }
                    GlStateManager._bindTexture(id);
                    img.upload(l, 0, 0, 0, 0, lw, lh, true, false, true, false);
                }
            }
            // Border (transparent) not edge: a mark touching a mip level's edge would streak across the window.
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
            return id;
        } catch (IOException | RuntimeException x) {
            LOG.warn("reticle {} unreadable: {}", rl, x.toString());
            return 0;
        }
    }

    private static int channel(float v) {
        return Math.clamp(Math.round(v), 0, 255);
    }

    /** Premultiplied 2x2 box (odd source edge: last row / column dropped, as {@code w >> 1}). */
    private static float[] down(float[] src, int sw, int sh, int w, int h) {
        float[] out = new float[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int o = (y * w + x) * 4;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int i = (Math.min(2 * y + dy, sh - 1) * sw + Math.min(2 * x + dx, sw - 1)) * 4;
                        for (int c = 0; c < 4; c++) {
                            out[o + c] += src[i + c] / 4;
                        }
                    }
                }
            }
        }
        return out;
    }

    public static void clear() {
        IDS.values().forEach(id -> {
            if (id != 0) {
                TextureUtil.releaseTextureId(id);
            }
        });
        IDS.clear();
    }
}
