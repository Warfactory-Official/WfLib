package com.wf.wflib.pda.music.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.wf.wflib.WFLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** Now-playing cover as the fixed texture {@link #TEXTURE}, letterboxed into a square. */
public final class CoverArt {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_music/cover");
    private static final int MAX_DIM = 2048;
    private static boolean present;

    private CoverArt() {
    }

    static boolean present() {
        return present;
    }

    /** Any thread. Bytes already passed the PNG / JPEG magic + size gate in {@link com.wf.wflib.pda.music.OggAudio}. */
    @Nullable
    static NativeImage decode(byte[] image) {
        NativeImage raw = raw(image);
        if (raw == null) {
            return null;
        }
        int width = raw.getWidth();
        int height = raw.getHeight();
        if (width == height) {
            return raw;
        }
        int size = Math.max(width, height);
        try (raw) {
            NativeImage out = new NativeImage(NativeImage.Format.RGBA, size, size, true);
            int ox = (size - width) / 2;
            int oy = (size - height) / 2;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    out.setPixelRGBA(x + ox, y + oy, raw.getPixelRGBA(x, y));
                }
            }
            return out;
        }
    }

    /** Any thread. Decoded at native size, at most {@link #MAX_DIM} per side; null = undecodable. */
    @Nullable
    public static NativeImage raw(byte[] image) {
        ByteBuffer in = MemoryUtil.memAlloc(image.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            in.put(image).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(in, w, h, c) || w.get(0) < 1 || h.get(0) < 1 || w.get(0) > MAX_DIM
                    || h.get(0) > MAX_DIM) {
                return null;
            }
            ByteBuffer px = STBImage.stbi_load_from_memory(in, w, h, c, 4);
            if (px == null) {
                return null;
            }
            try {
                px.order(ByteOrder.nativeOrder());
                int width = w.get(0);
                int height = h.get(0);
                NativeImage out = new NativeImage(NativeImage.Format.RGBA, width, height, true);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        out.setPixelRGBA(x, y, px.getInt((y * width + x) * 4));
                    }
                }
                return out;
            } finally {
                STBImage.stbi_image_free(px);
            }
        } finally {
            MemoryUtil.memFree(in);
        }
    }

    /** Render thread. Takes ownership of {@code image}; null = no cover. */
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
