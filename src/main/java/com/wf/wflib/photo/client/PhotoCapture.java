package com.wf.wflib.photo.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.wf.wflib.cam.api.client.OffscreenView;
import com.wf.wflib.photo.PhotoLimits;
import com.wf.wflib.photo.PhotoShutterPacket;
import com.wf.wflib.photo.PhotoUploadPacket;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryUtil;

/**
 * Renders the shot the server asked for through an {@link OffscreenView} (fixed pose, fixed field of view, no HUD),
 * encodes it as JPEG off the render thread, and sends it in fragments, a few per tick.
 */
final class PhotoCapture {
    private static final int WIDTH = PhotoLimits.CAPTURE_WIDTH;
    private static final int HEIGHT = WIDTH * 9 / 16;
    private static final int MAX_TRIES = 30;
    private static final int FRAGMENTS_PER_TICK = 3;
    private static final int[] QUALITY = {82, 65, 48, 32};

    private record Shot(long token, Vec3 eye, float yaw, float pitch) {
    }

    private static Shot shot;
    private static int tries;
    private static final ConcurrentLinkedQueue<PhotoUploadPacket> OUT = new ConcurrentLinkedQueue<>();

    private PhotoCapture() {
    }

    static void arm(PhotoShutterPacket p) {
        shot = new Shot(p.token(), new Vec3(p.x(), p.y(), p.z()), p.yaw(), p.pitch());
        tries = 0;
    }

    static void onFrame(RenderFrameEvent.Pre event) {
        Shot s = shot;
        if (s == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            shot = null;
            return;
        }
        TextureTarget target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        target.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        try (OffscreenView view = OffscreenView.sharedGrid()) {
            if (!view.render(s.eye(), s.yaw(), s.pitch(), 0.0f, PhotoLimits.FOV, target, event.getPartialTick())) {
                if (++tries > MAX_TRIES) {
                    shot = null;
                    fail(mc);
                }
                return;
            }
            NativeImage image = Screenshot.takeScreenshot(target);
            shot = null;
            Util.backgroundExecutor().execute(() -> encode(s.token(), image));
        } finally {
            target.destroyBuffers();
        }
    }

    private static void fail(Minecraft mc) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("wflib.photo.failed"), true);
        }
    }

    /** Background thread: takes ownership of {@code image}. */
    private static void encode(long token, NativeImage image) {
        byte[] jpeg = null;
        try {
            for (int quality : QUALITY) {
                jpeg = jpeg(image, quality);
                if (jpeg != null && jpeg.length <= PhotoLimits.MAX_BYTES) {
                    break;
                }
                jpeg = null;
            }
        } finally {
            image.close();
        }
        if (jpeg == null) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> fail(mc));
            return;
        }
        int total = (jpeg.length + PhotoLimits.UP_CHUNK - 1) / PhotoLimits.UP_CHUNK;
        for (int i = 0; i < total; i++) {
            int from = i * PhotoLimits.UP_CHUNK;
            OUT.add(new PhotoUploadPacket(token, i, total,
                    Arrays.copyOfRange(jpeg, from, Math.min(jpeg.length, from + PhotoLimits.UP_CHUNK))));
        }
    }

    /** RGBA in, JPEG out (stb ignores alpha for JPEG). */
    static byte[] jpeg(NativeImage image, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        STBIWriteCallback sink = STBIWriteCallback.create((context, data, size) -> {
            byte[] chunk = new byte[size];
            MemoryUtil.memByteBuffer(data, size).get(chunk);
            out.write(chunk, 0, size);
        });
        ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
        try {
            pixels.order(ByteOrder.nativeOrder());
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    pixels.putInt(image.getPixelRGBA(x, y)); // ABGR int = R,G,B,A in memory
                }
            }
            pixels.flip();
            boolean ok = STBImageWrite.stbi_write_jpg_to_func(sink, 0L, image.getWidth(), image.getHeight(), 4, pixels,
                    quality) != 0;
            return ok ? out.toByteArray() : null;
        } finally {
            MemoryUtil.memFree(pixels);
            sink.free();
        }
    }

    /** Client tick: a few fragments per tick so one shot is not one burst. */
    static void tickUpload() {
        for (int i = 0; i < FRAGMENTS_PER_TICK; i++) {
            PhotoUploadPacket next = OUT.poll();
            if (next == null) {
                return;
            }
            PacketDistributor.sendToServer(next);
        }
    }

    static void clear() {
        shot = null;
        OUT.clear();
    }
}
