package com.wf.wflib.ui.api;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * Layouts on surfaces in the world (gun computers, instrument panels): one {@link UiSurfaces} texture per key.
 * {@link #texture} serves a screen whose geometry is a model mesh; {@link #draw} also adds the quad. A layout with a
 * design size is fitted into the surface (bars where the aspect differs), else it fills it. Beyond {@link #RANGE}
 * nothing is requested => surface freed after {@link UiSurfaces} idle frames.
 */
public final class WorldScreens {
    public static final float RANGE = 24;
    public static final int MAX_PIXELS_PER_UNIT = 4;
    private static final int FULL_BRIGHT = 0xF000F0;

    private WorldScreens() {
    }

    /**
     * Texture for a screen {@code width} x {@code height} blocks, its centre {@code distance} blocks from the camera;
     * null when out of range. The surface keeps the layout's design size (else {@code fallbackUnits}) and takes the
     * screen's aspect.
     */
    @Nullable
    public static ResourceLocation texture(Object key, UiLayout layout, UiContext context, float distance, float width,
                                           float height, float fallbackUnitsWidth, int maxFps) {
        if (distance > RANGE) {
            return null;
        }
        float contentW = layout.designWidth() > 0 ? layout.designWidth() : fallbackUnitsWidth;
        float contentH = layout.designWidth() > 0 ? layout.designHeight() : fallbackUnitsWidth * height / width;
        float aspect = width / height;
        float unitsW = Math.max(contentW, contentH * aspect);
        float unitsH = unitsW / aspect;
        int pixelsPerUnit = pixelsPerUnit(distance, width, unitsW);
        return UiSurfaces.request(key, layout, context, Math.round(unitsW * pixelsPerUnit),
                Math.round(unitsH * pixelsPerUnit), unitsW, unitsH, contentW, contentH, maxFps, 0xFF000000);
    }

    /**
     * Quad centred on the pose origin in the XY plane, front toward +Z, unlit. Layout size as {@link #texture}.
     * @return false when out of range (nothing drawn)
     */
    public static boolean draw(PoseStack pose, MultiBufferSource buffers, Object key, UiLayout layout, UiContext context,
                               float width, float height, float fallbackUnitsWidth, int maxFps) {
        Matrix4f matrix = pose.last().pose();
        float distance = (float) Math.sqrt(matrix.m30() * matrix.m30() + matrix.m31() * matrix.m31() + matrix.m32() * matrix.m32());
        ResourceLocation texture = texture(key, layout, context, distance, width, height, fallbackUnitsWidth, maxFps);
        if (texture == null) {
            return false;
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.text(texture));
        float x = width / 2;
        float y = height / 2;
        buffer.addVertex(matrix, -x, y, 0).setColor(-1).setUv(0, 0).setLight(FULL_BRIGHT);
        buffer.addVertex(matrix, -x, -y, 0).setColor(-1).setUv(0, 1).setLight(FULL_BRIGHT);
        buffer.addVertex(matrix, x, -y, 0).setColor(-1).setUv(1, 1).setLight(FULL_BRIGHT);
        buffer.addVertex(matrix, x, y, 0).setColor(-1).setUv(1, 0).setLight(FULL_BRIGHT);
        return true;
    }

    /** Projected width in framebuffer px / layout units, rounded. */
    private static int pixelsPerUnit(float distance, float width, float unitsWidth) {
        float ndcHalf = RenderSystem.getProjectionMatrix().m00() * width / 2 / Math.max(distance, 0.05f);
        float pixels = ndcHalf * Minecraft.getInstance().getWindow().getWidth();
        return Math.clamp(Math.round(pixels / unitsWidth), 1, MAX_PIXELS_PER_UNIT);
    }
}
