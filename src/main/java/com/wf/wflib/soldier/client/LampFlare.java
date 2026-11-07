package com.wf.wflib.soldier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wf.wflib.cam.api.client.FeedPass;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Glare on a soldier's headlamp shining at the camera: a hot core, a halo and star / anamorphic streaks, additive
 * billboards at the lamp drawn in the level pass (so exports and replays have them), strongest looking straight down the
 * beam and gone past the cone. Depth-tested: anything in front of the lamp hides it per pixel. The lamps are the ones
 * {@link SoldierLightClient} submits, one frame late.
 */
final class LampFlare {
    /** Core / halo / streak reach as angles from the camera, rad; the world size clamps keep far lamps seen, near ones sane. */
    private static final float CORE_RAD = 0.006F, HALO_RAD = 0.05F, STREAK_RAD = 0.2F;
    private static final float CORE_MIN = 0.04F, CORE_MAX = 0.35F, HALO_MIN = 0.25F, HALO_MAX = 3.0F, STREAK_MIN = 0.8F,
            STREAK_MAX = 9.0F;
    /** Quad lift toward the camera off the lamp, blocks. */
    private static final float LIFT = 0.12F;
    /** The flare stays on this far past the outer cone half angle (the bulb's own halo), as a multiple. */
    private static final float CONE_SLACK = 1.5F;
    private static final int RINGS = 12, SEGMENTS = 24;
    /** Brightness: below ~2 the flare is a pinpoint that vanishes past 10 blocks, at 5 it is a screen-wide cross. */
    private static final float GAIN = 2.2F;
    /** Daylight washes a flare out. */
    private static final float DAY = 0.45F;

    private record Source(double x, double y, double z, Vector3f dir) {}

    private static final List<Source> SOURCES = new ArrayList<>();

    private LampFlare() {}

    /** Start of a main-pass light collection: the lamps of the frame about to be drawn. */
    static void clear() {
        if (!FeedPass.active()) SOURCES.clear();
    }

    static void add(double x, double y, double z, Vector3f dir) {
        if (!FeedPass.active()) SOURCES.add(new Source(x, y, z, new Vector3f(dir)));
    }

    /** How much of the flare shows: 1 inside the inner cone, 0 at {@code CONE_SLACK} x the outer half angle. */
    static float visibility(Vector3f dir, Vector3f toCamera, float innerDeg, float outerDeg) {
        float cos = dir.dot(toCamera);
        float outer = (float) Math.cos(Math.toRadians(Math.min(outerDeg * CONE_SLACK, 85)));
        float inner = (float) Math.cos(Math.toRadians(innerDeg));
        if (inner <= outer) return cos >= inner ? 1 : 0;
        return smooth((cos - outer) / (inner - outer));
    }

    /** Full out to the light's range, gone by 2.5 x it: a lamp far down a street still glints. */
    static float distanceFade(float dist, float range) {
        return 1 - smooth((dist - range) / (range * 1.5F));
    }

    private static float smooth(float t) {
        float c = Mth.clamp(t, 0, 1);
        return c * c * (3 - 2 * c);
    }

    private static float size(float dist, float angle, float min, float max) {
        return Mth.clamp(dist * angle, min, max);
    }

    static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FeedPass.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SOURCES.isEmpty()) return;
        Camera cam = e.getCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(LampFlareTypes.ADDITIVE);
        PoseStack.Pose pose = e.getPoseStack().last();
        float day = mc.level.isDay() ? DAY : 1;
        Vector3f right = new Vector3f(1, 0, 0).rotate(cam.rotation());
        Vector3f up = new Vector3f(0, 1, 0).rotate(cam.rotation());
        float r = (SoldierLightClient.COLOR >> 16 & 255) / 255F, g = (SoldierLightClient.COLOR >> 8 & 255) / 255F,
                b = (SoldierLightClient.COLOR & 255) / 255F;
        for (Source s : SOURCES) {
            Vector3f to = new Vector3f((float) (cam.getPosition().x - s.x), (float) (cam.getPosition().y - s.y),
                    (float) (cam.getPosition().z - s.z));
            float dist = to.length();
            if (dist < 0.3F) continue;
            to.div(dist);
            float k = visibility(s.dir, to, SoldierLightClient.INNER, SoldierLightClient.OUTER)
                    * distanceFade(dist, SoldierLightClient.RANGE) * day * GAIN;
            if (k <= 0.01F) continue;
            // Camera-relative, lifted toward the camera.
            Vector3f c = new Vector3f((float) (s.x - cam.getPosition().x), (float) (s.y - cam.getPosition().y),
                    (float) (s.z - cam.getPosition().z)).add(new Vector3f(to).mul(LIFT));
            float halo = size(dist, HALO_RAD, HALO_MIN, HALO_MAX), core = size(dist, CORE_RAD, CORE_MIN, CORE_MAX),
                    streak = size(dist, STREAK_RAD, STREAK_MIN, STREAK_MAX);
            glow(vc, pose, c, right, up, halo, r, g, b, 0.5F * Math.min(k, 1), 2.2F);
            // Looking straight down the beam adds the star; off to the side only the halo.
            float star = Math.max(0, k - 0.35F) / 0.65F;
            if (star > 0) streaks(vc, pose, c, right, up, streak, r, g, b, star);
            glow(vc, pose, c, right, up, core, 1, 1, 1, Math.min(k * 1.4F, 1), 1.4F);
        }
        buffers.endBatch(LampFlareTypes.ADDITIVE);
    }

    /** Radial falloff disc: rings of vertex colour, alpha {@code peak * (1 - t)^exp}. */
    private static void glow(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, Vector3f right, Vector3f up, float radius,
                             float r, float g, float b, float peak, float exp) {
        for (int ring = 0; ring < RINGS; ring++) {
            float t0 = ring / (float) RINGS, t1 = (ring + 1) / (float) RINGS;
            float a0 = peak * (float) Math.pow(1 - t0, exp), a1 = peak * (float) Math.pow(1 - t1, exp);
            for (int i = 0; i < SEGMENTS; i++) {
                double u0 = Math.PI * 2 * i / SEGMENTS, u1 = Math.PI * 2 * (i + 1) / SEGMENTS;
                point(vc, pose, c, right, up, radius * t0, u0, r, g, b, a0);
                point(vc, pose, c, right, up, radius * t1, u0, r, g, b, a1);
                point(vc, pose, c, right, up, radius * t1, u1, r, g, b, a1);
                point(vc, pose, c, right, up, radius * t0, u1, r, g, b, a0);
            }
        }
    }

    private static void point(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, Vector3f right, Vector3f up,
                              float radius, double angle, float r, float g, float b, float a) {
        edge(vc, pose, c, right, up, (float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius, r, g, b, a);
    }

    /** Four-ray star (axes + diagonals, the diagonals shorter) and a long thin horizontal anamorphic streak. */
    private static void streaks(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, Vector3f right, Vector3f up, float len,
                                float r, float g, float b, float k) {
        float[][] rays = {{0, 1, 0.55F}, {(float) (Math.PI / 2), 1, 0.55F}, {(float) (Math.PI / 4), 0.6F, 0.4F},
                {(float) (3 * Math.PI / 4), 0.6F, 0.4F}};
        for (float[] ray : rays) {
            ray(vc, pose, c, right, up, ray[0], len * ray[1], len * 0.018F, r, g, b, 0.55F * k * ray[2]);
        }
        ray(vc, pose, c, right, up, 0, len * 2.2F, len * 0.012F, r * 0.85F, g * 0.92F, b, 0.45F * k);
    }

    /** One streak at {@code angle} in the billboard plane: both halves fade to a point at the tips. */
    private static void ray(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, Vector3f right, Vector3f up,
                            float angle, float len, float halfWidth, float r, float g, float b, float a) {
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        for (int dir = -1; dir <= 1; dir += 2) {
            float tx = ca * dir * len, ty = sa * dir * len, wx = -sa * halfWidth, wy = ca * halfWidth;
            edge(vc, pose, c, right, up, wx, wy, r, g, b, a);
            edge(vc, pose, c, right, up, -wx, -wy, r, g, b, a);
            edge(vc, pose, c, right, up, tx, ty, r, g, b, 0);
            edge(vc, pose, c, right, up, tx, ty, r, g, b, 0);
        }
    }

    private static void edge(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, Vector3f right, Vector3f up, float x,
                             float y, float r, float g, float b, float a) {
        vc.addVertex(pose, c.x + right.x * x + up.x * y, c.y + right.y * x + up.y * y, c.z + right.z * x + up.z * y)
                .setColor(r, g, b, a);
    }
}
