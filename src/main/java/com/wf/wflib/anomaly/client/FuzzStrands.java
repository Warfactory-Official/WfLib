package com.wf.wflib.anomaly.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wf.wflib.anomaly.AnomalyBlockEntity;
import com.wf.wflib.anomaly.AnomalyBlockEntity.Phase;
import com.wf.wflib.anomaly.BlastTuning;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Burnt Fuzz's hair (a mesh in STALKER's levels): rope strands hanging from the block's top, simulated (Verlet, fixed
 * roots, length constraints) so they sway in a breeze and, through a blowout, lash about and reach for whatever is
 * under them. Client only; drawn as camera-facing ribbons.
 */
final class FuzzStrands {
    private static final int NODES = 9;
    private static final float WIDTH = 0.022F;
    private static final float GRAVITY = 0.03F;

    private final int count;
    private final float[] rootX;
    private final float[] rootY;
    private final float[] rootZ;
    private final float[] seg;
    private final float[] phase;
    /** Top of whatever is under each root: strands lie on it rather than pass through. */
    private final float[] floor;
    /** Now and a tick ago, per node ({@code strand * NODES + node}). */
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] px;
    private final float[] py;
    private final float[] pz;

    FuzzStrands(AnomalyBlockEntity be) {
        RandomSource r = RandomSource.create(be.getBlockPos().asLong());
        double spread = be.behaviour().radius() * 0.7D;
        count = Mth.clamp((int) (18.0D * spread * spread), 12, 80);
        rootX = new float[count];
        rootY = new float[count];
        rootZ = new float[count];
        seg = new float[count];
        phase = new float[count];
        floor = new float[count];
        x = new float[count * NODES];
        y = new float[count * NODES];
        z = new float[count * NODES];
        Vec3 top = Vec3.atBottomCenterOf(be.getBlockPos()).add(0.0D, 1.0D, 0.0D);
        for (int s = 0; s < count; s++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            double out = Math.sqrt(r.nextDouble()) * spread;
            rootX[s] = (float) (top.x + Math.cos(a) * out);
            rootY[s] = (float) top.y;
            rootZ[s] = (float) (top.z + Math.sin(a) * out);
            float length = 0.8F + r.nextFloat() * (float) (BlastTuning.HAIRS_DROP - 0.2D);
            seg[s] = length / (NODES - 1);
            phase[s] = r.nextFloat() * Mth.TWO_PI;
            floor[s] = floor(be.getLevel(), rootX[s], rootY[s], rootZ[s]);
            for (int n = 0; n < NODES; n++) {
                int i = s * NODES + n;
                x[i] = rootX[s];
                y[i] = rootY[s] - n * seg[s];
                z[i] = rootZ[s];
            }
        }
        px = x.clone();
        py = y.clone();
        pz = z.clone();
    }

    void tick(AnomalyBlockEntity be, Level level) {
        boolean lash = be.phase() == Phase.BLOWOUT;
        Vec3 prey = lash ? prey(be, level) : null;
        RandomSource random = level.random;
        float t = be.age();
        for (int s = 0; s < count; s++) {
            float windX = 0.004F * Mth.sin(t * 0.09F + phase[s]);
            float windZ = 0.004F * Mth.cos(t * 0.07F + phase[s] * 1.3F);
            for (int n = 1; n < NODES; n++) {
                int i = s * NODES + n;
                float vx = (x[i] - px[i]) * 0.9F + windX;
                float vy = (y[i] - py[i]) * 0.9F - GRAVITY;
                float vz = (z[i] - pz[i]) * 0.9F + windZ;
                if (lash) {
                    float k = (float) n / NODES;
                    vx += (float) random.nextGaussian() * 0.06F * k;
                    vy += (float) random.nextGaussian() * 0.04F * k;
                    vz += (float) random.nextGaussian() * 0.06F * k;
                    if (prey != null) {
                        vx += (float) (prey.x - x[i]) * 0.05F * k;
                        vy += (float) (prey.y - y[i]) * 0.05F * k;
                        vz += (float) (prey.z - z[i]) * 0.05F * k;
                    }
                }
                px[i] = x[i];
                py[i] = y[i];
                pz[i] = z[i];
                x[i] += vx;
                y[i] += vy;
                z[i] += vz;
            }
            int root = s * NODES;
            px[root] = x[root] = rootX[s];
            py[root] = y[root] = rootY[s];
            pz[root] = z[root] = rootZ[s];
            for (int pass = 0; pass < 4; pass++) {
                for (int n = 1; n < NODES; n++) {
                    constrain(root + n - 1, root + n, seg[s], n == 1);
                }
            }
            for (int n = 1; n < NODES; n++) {
                y[root + n] = Math.max(y[root + n], floor[s]);
            }
        }
    }

    /** Keep nodes {@code a} and {@code b} {@code length} apart; {@code a} pinned when it is the root. */
    private void constrain(int a, int b, float length, boolean pinned) {
        float dx = x[b] - x[a];
        float dy = y[b] - y[a];
        float dz = z[b] - z[a];
        float d = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0E-5F) {
            return;
        }
        float k = (d - length) / d;
        float wa = pinned ? 0.0F : 0.5F;
        float wb = 1.0F - wa;
        x[a] += dx * k * wa;
        y[a] += dy * k * wa;
        z[a] += dz * k * wa;
        x[b] -= dx * k * wb;
        y[b] -= dy * k * wb;
        z[b] -= dz * k * wb;
    }

    /** Top of the first solid block below {@code (x, y, z)}, as far as a strand reaches; below that if none. */
    private static float floor(@Nullable Level level, float x, float y, float z) {
        float lowest = y - (float) BlastTuning.HAIRS_DROP - 1.0F;
        if (level == null) {
            return lowest;
        }
        net.minecraft.core.BlockPos.MutableBlockPos at = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int by = Mth.floor(y) - 1; by >= Mth.floor(lowest); by--) {
            at.set(Mth.floor(x), by, Mth.floor(z));
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return by + 1.02F;
            }
        }
        return lowest;
    }

    /** The nearest living thing under it, to reach for. */
    @Nullable
    private static Vec3 prey(AnomalyBlockEntity be, Level level) {
        Vec3 c = be.center();
        double r = be.behaviour().radius();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB box = new AABB(c, c).inflate(r).expandTowards(0.0D, -BlastTuning.HAIRS_DROP, 0.0D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double d = be.reach(e, c);
            if (d <= r && d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best == null ? null : best.getBoundingBox().getCenter();
    }

    /** Ribbons, root dark to tip lighter, lit by {@code light}; world coordinates (the caller translated by -camera). */
    void emit(VertexConsumer out, Matrix4f matrix, Vec3 cam, float partial, int light) {
        for (int s = 0; s < count; s++) {
            for (int n = 0; n < NODES - 1; n++) {
                int a = s * NODES + n;
                int b = a + 1;
                float ax = Mth.lerp(partial, px[a], x[a]);
                float ay = Mth.lerp(partial, py[a], y[a]);
                float az = Mth.lerp(partial, pz[a], z[a]);
                float bx = Mth.lerp(partial, px[b], x[b]);
                float by = Mth.lerp(partial, py[b], y[b]);
                float bz = Mth.lerp(partial, pz[b], z[b]);
                // Side = segment x (to camera), so the ribbon faces the eye.
                float dx = bx - ax;
                float dy = by - ay;
                float dz = bz - az;
                float cx = (float) (cam.x - ax);
                float cy = (float) (cam.y - ay);
                float cz = (float) (cam.z - az);
                float sx = dy * cz - dz * cy;
                float sy = dz * cx - dx * cz;
                float sz = dx * cy - dy * cx;
                float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
                if (len < 1.0E-6F) {
                    continue;
                }
                float w0 = WIDTH * (1.0F - 0.6F * n / NODES) / len;
                float w1 = WIDTH * (1.0F - 0.6F * (n + 1) / NODES) / len;
                float k0 = (float) n / NODES;
                float k1 = (float) (n + 1) / NODES;
                vertex(out, matrix, ax - sx * w0, ay - sy * w0, az - sz * w0, k0, light);
                vertex(out, matrix, ax + sx * w0, ay + sy * w0, az + sz * w0, k0, light);
                vertex(out, matrix, bx + sx * w1, by + sy * w1, bz + sz * w1, k1, light);
                vertex(out, matrix, bx - sx * w1, by - sy * w1, bz - sz * w1, k1, light);
            }
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float z, float k, int light) {
        out.addVertex(matrix, x, y, z).setColor(0.22F + 0.33F * k, 0.13F + 0.22F * k, 0.08F + 0.12F * k, 1.0F)
                .setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
    }
}
