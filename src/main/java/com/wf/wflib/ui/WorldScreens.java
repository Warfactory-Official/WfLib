package com.wf.wflib.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Layout on a quad in the world (gun computers, instrument panels): one {@link UiSurfaces} texture per key, drawn
 * unlit. Pose: quad centred on the origin in the XY plane, front toward +Z, {@code width} x {@code height} blocks;
 * layout units {@code unitsWidth} x {@code unitsHeight} (use {@code scaling: absolute}). Beyond {@link #RANGE}
 * nothing is requested => surface freed after {@link UiSurfaces} idle frames.
 */
public final class WorldScreens {
    public static final float RANGE = 24;
    public static final int MAX_PIXELS_PER_UNIT = 4;
    private static final int FULL_BRIGHT = 0xF000F0;

    private WorldScreens() {
    }

    /** @return false when out of range (nothing drawn) */
    public static boolean draw(PoseStack pose, MultiBufferSource buffers, Object key, UiLayout layout, UiContext context,
                               float width, float height, float unitsWidth, float unitsHeight, int maxFps) {
        Matrix4f matrix = pose.last().pose();
        float distance = (float) Math.sqrt(matrix.m30() * matrix.m30() + matrix.m31() * matrix.m31() + matrix.m32() * matrix.m32());
        if (distance > RANGE) {
            return false;
        }
        int pixelsPerUnit = pixelsPerUnit(distance, width, unitsWidth);
        ResourceLocation texture = UiSurfaces.request(key, layout, context, Math.round(unitsWidth * pixelsPerUnit),
                Math.round(unitsHeight * pixelsPerUnit), unitsWidth, unitsHeight, maxFps, 0xFF000000);
        VertexConsumer buffer = buffers.getBuffer(RenderType.text(texture));
        float x = width / 2;
        float y = height / 2;
        buffer.addVertex(matrix, -x, y, 0).setColor(-1).setUv(0, 0).setLight(FULL_BRIGHT);
        buffer.addVertex(matrix, -x, -y, 0).setColor(-1).setUv(0, 1).setLight(FULL_BRIGHT);
        buffer.addVertex(matrix, x, -y, 0).setColor(-1).setUv(1, 1).setLight(FULL_BRIGHT);
        buffer.addVertex(matrix, x, y, 0).setColor(-1).setUv(1, 0).setLight(FULL_BRIGHT);
        return true;
    }

    /** Projected width in framebuffer px / layout units, rounded; 1 at distance. */
    private static int pixelsPerUnit(float distance, float width, float unitsWidth) {
        float ndcHalf = RenderSystem.getProjectionMatrix().m00() * width / 2 / Math.max(distance, 0.05f);
        float pixels = ndcHalf * Minecraft.getInstance().getWindow().getWidth();
        return Math.clamp(Math.round(pixels / unitsWidth), 1, MAX_PIXELS_PER_UNIT);
    }
}
