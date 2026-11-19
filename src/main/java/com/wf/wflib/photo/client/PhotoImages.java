package com.wf.wflib.photo.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.photo.PhotoChunkPacket;
import com.wf.wflib.photo.PhotoFetchPacket;
import com.wf.wflib.photo.PhotoLimits;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Client cache of photographs: fetched by id, decoded off the render thread, kept as dynamic textures (LRU). */
final class PhotoImages {
    private static final int CACHE = 24;

    record Tex(ResourceLocation location, DynamicTexture texture, int width, int height) {
    }

    private static final class Download {
        final UUID id;
        final byte[][] parts;
        int received;

        Download(UUID id, int total) {
            this.id = id;
            this.parts = new byte[total][];
        }
    }

    private static final Map<UUID, Tex> TEX = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Integer, UUID> REQUESTED = new HashMap<>();
    private static final Map<Integer, Download> DOWNLOADS = new HashMap<>();
    private static final Set<UUID> LOADING = new HashSet<>();
    private static final Set<UUID> FAILED = new HashSet<>();
    private static int next = 1;

    private PhotoImages() {
    }

    /** The texture for {@code id} if loaded; else null and a fetch is started (once). */
    @Nullable
    static Tex get(UUID id) {
        Tex tex = TEX.get(id);
        if (tex != null || FAILED.contains(id) || !LOADING.add(id)) {
            return tex;
        }
        int transfer = next++;
        REQUESTED.put(transfer, id);
        PacketDistributor.sendToServer(new PhotoFetchPacket(transfer, id));
        return null;
    }

    static boolean failed(UUID id) {
        return FAILED.contains(id);
    }

    /** Drops a cached entry (deleted photo) so a re-use of the id would fetch again. */
    static void forget(UUID id) {
        Tex tex = TEX.remove(id);
        if (tex != null) {
            release(tex);
        }
        FAILED.remove(id);
    }

    static void chunk(PhotoChunkPacket p) {
        UUID id = REQUESTED.get(p.transfer());
        if (id == null) {
            return;
        }
        if (p.total() <= 0 || p.total() > PhotoLimits.MAX_DOWN_CHUNKS || p.index() < 0 || p.index() >= p.total()
                || p.data().length > PhotoLimits.DOWN_CHUNK) {
            finish(p.transfer(), id, false);
            return;
        }
        Download d = DOWNLOADS.computeIfAbsent(p.transfer(), t -> new Download(id, p.total()));
        if (d.parts.length != p.total() || d.parts[p.index()] != null) {
            finish(p.transfer(), id, false);
            return;
        }
        d.parts[p.index()] = p.data();
        if (++d.received < d.parts.length) {
            return;
        }
        int size = 0;
        for (byte[] part : d.parts) {
            size += part.length;
        }
        byte[] whole = new byte[size];
        int at = 0;
        for (byte[] part : d.parts) {
            System.arraycopy(part, 0, whole, at, part.length);
            at += part.length;
        }
        int transfer = p.transfer();
        DOWNLOADS.remove(transfer);
        Util.backgroundExecutor().execute(() -> {
            NativeImage image = decode(whole);
            Minecraft.getInstance().execute(() -> {
                if (image == null) {
                    finish(transfer, id, false);
                    return;
                }
                install(id, image);
                finish(transfer, id, true);
            });
        });
    }

    private static void finish(int transfer, UUID id, boolean ok) {
        REQUESTED.remove(transfer);
        DOWNLOADS.remove(transfer);
        LOADING.remove(id);
        if (!ok) {
            FAILED.add(id);
        }
    }

    private static void install(UUID id, NativeImage image) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo/" + id);
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        TEX.put(id, new Tex(location, texture, image.getWidth(), image.getHeight()));
        while (TEX.size() > CACHE) {
            UUID eldest = TEX.keySet().iterator().next();
            release(TEX.remove(eldest));
        }
    }

    private static void release(Tex tex) {
        Minecraft.getInstance().getTextureManager().release(tex.location());
    }

    /** Any thread. null = not a decodable image within the size limits. */
    @Nullable
    static NativeImage decode(byte[] bytes) {
        ByteBuffer in = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            in.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(in, w, h, c) || w.get(0) < 1 || h.get(0) < 1
                    || w.get(0) > PhotoLimits.MAX_WIDTH || h.get(0) > PhotoLimits.MAX_HEIGHT) {
                return null;
            }
            ByteBuffer px = STBImage.stbi_load_from_memory(in, w, h, c, 4);
            if (px == null) {
                return null;
            }
            try {
                NativeImage out = new NativeImage(NativeImage.Format.RGBA, w.get(0), h.get(0), false);
                px.order(ByteOrder.nativeOrder());
                for (int y = 0; y < h.get(0); y++) {
                    for (int x = 0; x < w.get(0); x++) {
                        out.setPixelRGBA(x, y, px.getInt((y * w.get(0) + x) * 4));
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

    static void clear() {
        for (Tex tex : TEX.values()) {
            release(tex);
        }
        TEX.clear();
        REQUESTED.clear();
        DOWNLOADS.clear();
        LOADING.clear();
        FAILED.clear();
    }
}
