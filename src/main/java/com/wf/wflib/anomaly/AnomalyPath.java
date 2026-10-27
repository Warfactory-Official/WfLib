package com.wf.wflib.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A wandering anomaly's loop over the ground round its block, one point per tick (so a position is the game time
 * modulo its length), relative to the block's bottom centre. Made once on the server from the terrain, saved with the
 * block and sent to clients, so both sides put it in the same place.
 */
public final class AnomalyPath {
    /** Comet: blocks/tick along its loop (STALKER's torrid lines cover 3-8 m/s). */
    static final double COMET_SPEED = 0.2D;
    /** Comet: the fireball's centre above the ground. */
    static final double COMET_HOVER = 1.2D;
    /** Ghost: ticks per loop; 2.25 degrees a tick per Lissajous step, about DAO's 1-3 degrees per 22 ms. */
    static final int GHOST_TICKS = 160;

    private final float[] xyz;

    AnomalyPath(float[] xyz) {
        this.xyz = xyz;
    }

    public int ticks() {
        return xyz.length / 3;
    }

    /** Offset from the block's bottom centre at game time {@code t} (ticks, fractional). */
    public Vec3 at(double t) {
        int n = ticks();
        double wrapped = t - Math.floor(t / n) * n;
        int i = (int) wrapped;
        int j = (i + 1) % n;
        double f = wrapped - i;
        return new Vec3(Mth.lerp(f, xyz[i * 3], xyz[j * 3]), Mth.lerp(f, xyz[i * 3 + 1], xyz[j * 3 + 1]),
                Mth.lerp(f, xyz[i * 3 + 2], xyz[j * 3 + 2]));
    }

    public void save(CompoundTag tag) {
        int[] bits = new int[xyz.length];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = Float.floatToIntBits(xyz[i]);
        }
        tag.putIntArray("path", bits);
    }

    @org.jetbrains.annotations.Nullable
    public static AnomalyPath load(CompoundTag tag) {
        int[] bits = tag.getIntArray("path");
        if (bits.length < 6 || bits.length % 3 != 0) {
            return null;
        }
        float[] xyz = new float[bits.length];
        for (int i = 0; i < bits.length; i++) {
            xyz[i] = Float.intBitsToFloat(bits[i]);
        }
        return new AnomalyPath(xyz);
    }

    /**
     * Ghost ({@code anomalies_spawn_functions.zone_mine_ghost}): a Lissajous figure 0.5-3 blocks out on each axis,
     * its steps 1-3 to 1-4, along the ground; it keeps its height over drops or rises too sudden to follow (DAO: 0.5 m,
     * here 1.5 blocks, smoothed so a step is climbed, not jumped).
     */
    static AnomalyPath ghost(Level level, BlockPos pos) {
        RandomSource r = RandomSource.create(pos.asLong());
        double ax = (0.5D + 2.5D * r.nextDouble()) * (r.nextBoolean() ? 1 : -1);
        double az = (0.5D + 2.5D * r.nextDouble()) * (r.nextBoolean() ? 1 : -1);
        int sx = 1 + r.nextInt(3);
        int sz = sx == 1 ? 2 + r.nextInt(2) : sx == 2 ? 1 + 2 * r.nextInt(2) : 2 + 2 * r.nextInt(2);
        double phase = Math.toRadians(45.0D * r.nextInt(5));
        float[] xyz = new float[GHOST_TICKS * 3];
        double y = 0.0D;
        for (int k = 0; k < GHOST_TICKS; k++) {
            double x = Math.sin(phase + Mth.TWO_PI * sx * k / GHOST_TICKS) * ax;
            double z = Math.sin(Mth.TWO_PI * sz * k / GHOST_TICKS) * az;
            double ground = ground(level, pos, x, z, y);
            y = Math.abs(ground - y) <= 1.5D ? ground : y;
            xyz[k * 3] = (float) x;
            xyz[k * 3 + 1] = (float) y;
            xyz[k * 3 + 2] = (float) z;
        }
        smoothHeights(xyz, 4, 0.0D);
        return new AnomalyPath(xyz);
    }

    /**
     * Comet ({@code CTorridZone}, whose levels draw its path): a closed loop through 10-14 points 6-14 blocks out,
     * each pulled in until it can see the last one (round walls, over hills), smoothed into a curve that rolls along
     * the ground at chest height.
     */
    static AnomalyPath comet(Level level, BlockPos pos) {
        RandomSource r = RandomSource.create(pos.asLong() ^ 0x5DEECE66DL);
        Vec3 base = Vec3.atBottomCenterOf(pos);
        int count = 10 + r.nextInt(5);
        List<Vec3> points = new ArrayList<>();
        double angle0 = r.nextDouble() * Mth.TWO_PI;
        double y = 0.0D;
        for (int k = 0; k < count; k++) {
            double a = angle0 + Mth.TWO_PI * (k + 0.3D * (r.nextDouble() - 0.5D)) / count;
            double out = 6.0D + 8.0D * r.nextDouble();
            Vec3 point = null;
            for (int tries = 0; tries < 4 && point == null; tries++, out *= 0.65D) {
                double x = Math.cos(a) * out;
                double z = Math.sin(a) * out;
                double ground = ground(level, pos, x, z, y);
                if (Math.abs(ground - y) > 6.0D) {
                    continue;
                }
                Vec3 candidate = new Vec3(x, ground + COMET_HOVER, z);
                Vec3 from = points.isEmpty() ? new Vec3(0.0D, COMET_HOVER, 0.0D) : points.get(points.size() - 1);
                if (clear(level, base.add(from), base.add(candidate))) {
                    point = candidate;
                    y = ground;
                }
            }
            if (point != null) {
                points.add(point);
            }
        }
        if (points.size() < 3) {
            // Boxed in: a small circle on the spot.
            points.clear();
            for (int k = 0; k < 8; k++) {
                double a = Mth.TWO_PI * k / 8;
                points.add(new Vec3(Math.cos(a) * 2.0D, COMET_HOVER, Math.sin(a) * 2.0D));
            }
        }
        List<Vec3> curve = catmullRom(points, 24);
        float[] xyz = resample(curve, COMET_SPEED);
        double last = 0.0D;
        for (int k = 0; k < xyz.length / 3; k++) {
            double ground = ground(level, pos, xyz[k * 3], xyz[k * 3 + 2], last);
            last = ground;
            xyz[k * 3 + 1] = (float) (ground + COMET_HOVER);
        }
        smoothHeights(xyz, 8, COMET_HOVER * 0.6D);
        return new AnomalyPath(xyz);
    }

    /**
     * Top of the ground under {@code (x, z)} (block-relative), searched from {@code nearY} 3 up to 8 down, so a path
     * stays on its own floor in caves and buildings; {@code nearY} when there is none.
     */
    static double ground(Level level, BlockPos pos, double x, double z, double nearY) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(pos.getX() + 0.5D + x);
        int bz = Mth.floor(pos.getZ() + 0.5D + z);
        int top = pos.getY() + Mth.floor(nearY) + 3;
        for (int by = top; by > top - 11; by--) {
            boolean air = level.getBlockState(at.set(bx, by, bz)).getCollisionShape(level, at).isEmpty();
            boolean floor = !level.getBlockState(at.set(bx, by - 1, bz)).getCollisionShape(level, at).isEmpty();
            if (air && floor) {
                return by - pos.getY();
            }
        }
        return nearY;
    }

    private static boolean clear(Level level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    /** A closed Catmull-Rom curve through {@code points}, {@code per} samples a span. */
    static List<Vec3> catmullRom(List<Vec3> points, int per) {
        int n = points.size();
        List<Vec3> out = new ArrayList<>(n * per);
        for (int k = 0; k < n; k++) {
            Vec3 p0 = points.get((k - 1 + n) % n);
            Vec3 p1 = points.get(k);
            Vec3 p2 = points.get((k + 1) % n);
            Vec3 p3 = points.get((k + 2) % n);
            for (int s = 0; s < per; s++) {
                double t = (double) s / per;
                double t2 = t * t;
                double t3 = t2 * t;
                out.add(p1.scale(2.0D).add(p2.subtract(p0).scale(t))
                        .add(p0.scale(2.0D).subtract(p1.scale(5.0D)).add(p2.scale(4.0D)).subtract(p3).scale(t2))
                        .add(p1.scale(3.0D).subtract(p0).subtract(p2.scale(3.0D)).add(p3).scale(t3)).scale(0.5D));
            }
        }
        return out;
    }

    /** The closed polyline {@code curve} as points {@code step} apart along it (a tick's travel each). */
    static float[] resample(List<Vec3> curve, double step) {
        int n = curve.size();
        double[] at = new double[n + 1];
        for (int k = 0; k < n; k++) {
            at[k + 1] = at[k] + curve.get(k).distanceTo(curve.get((k + 1) % n));
        }
        int samples = Math.max(2, (int) Math.round(at[n] / step));
        float[] xyz = new float[samples * 3];
        int seg = 0;
        for (int s = 0; s < samples; s++) {
            double d = at[n] * s / samples;
            while (seg < n - 1 && at[seg + 1] < d) {
                seg++;
            }
            double len = at[seg + 1] - at[seg];
            double f = len <= 1.0E-9D ? 0.0D : (d - at[seg]) / len;
            Vec3 p = curve.get(seg).lerp(curve.get((seg + 1) % n), f);
            xyz[s * 3] = (float) p.x;
            xyz[s * 3 + 1] = (float) p.y;
            xyz[s * 3 + 2] = (float) p.z;
        }
        return xyz;
    }

    /** Heights averaged over {@code reach} points either side (a loop), never more than {@code sag} under the raw. */
    static void smoothHeights(float[] xyz, int reach, double sag) {
        int n = xyz.length / 3;
        float[] y = new float[n];
        for (int k = 0; k < n; k++) {
            double sum = 0.0D;
            for (int d = -reach; d <= reach; d++) {
                sum += xyz[Math.floorMod(k + d, n) * 3 + 1];
            }
            // Raw is ground + hover: over a crest the average may dip, but only by sag, so it never digs in.
            y[k] = (float) Math.max(sum / (2 * reach + 1), xyz[k * 3 + 1] - sag);
        }
        for (int k = 0; k < n; k++) {
            xyz[k * 3 + 1] = y[k];
        }
    }
}
