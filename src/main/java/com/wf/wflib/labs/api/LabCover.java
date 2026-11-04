package com.wf.wflib.labs.api;

import com.wf.wflib.round.api.BlockPen;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.Ricochet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * One round meeting an infinite wall of {@code thickness} m (full blocks of 1 m, remainder last) at {@code angle}
 * deg from the normal, through the game's own {@link Ricochet}, {@link BlockPen} and drill rule. Ricochet is a seeded
 * roll: {@code samples} seeds give {@code p_ricochet}.
 */
public final class LabCover {
    private static final long STRIDE = 0x9E3779B97F4A7C15L;
    private static final double WIDE = 50.0;

    private LabCover() {
    }

    /** A cover material: the values the game reads for the block's default state. */
    public record Material(String block, float resistance, float toughnessScaled, float ricochet) {
    }

    /** @throws IllegalArgumentException for an unknown block id */
    public static Material material(String blockId) {
        net.minecraft.world.level.block.Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.ResourceLocation.parse(blockId))
                .orElseThrow(() -> new IllegalArgumentException("no block " + blockId));
        net.minecraft.world.level.block.state.BlockState s = b.defaultBlockState();
        return new Material(blockId, com.wf.wflib.round.api.PenValues.resistance(s),
                com.wf.wflib.round.api.PenValues.toughnessScaled(s), com.wf.wflib.round.api.PenValues.ricochet(s));
    }

    public static Result evaluate(KineticPreset p, double speedMs, Material m, double thickness, double angleDeg,
                                  int samples, long seed) {
        return evaluate(p, speedMs, m.resistance(), m.toughnessScaled(), m.ricochet(), thickness, angleDeg, samples,
                seed);
    }

    public enum Verdict {
        STOP,
        PERFORATE,
        RICOCHET,
        DETONATE,
        DRILL
    }

    /**
     * @param spent       sum of {@code c/C} over the blocks entered
     * @param depth       m travelled inside the wall when stopped
     * @param impulse     kg m/s on the first block hit
     * @param damageFrac  {@code impulse / scaled toughness} of the first block; >= 1 destroys it
     */
    public record Result(Verdict verdict, double pRicochet, double pPerforate, double pStop, double spent,
                         double exitSpeed, double exitPen, double depth, double impulse, double damageFrac,
                         double ricochetSpeed) {
    }

    /**
     * @param speedMs  m/s on arrival
     * @param toughnessScaled {@code PenTable.toughness x RoundConfig.blockToughnessScale}
     */
    public static Result evaluate(KineticPreset p, double speedMs, float resistance, float toughnessScaled,
                                  float ricochetDeg, double thickness, double angleDeg, int samples, long seed) {
        double vb = speedMs / 20.0;
        double a = Math.toRadians(angleDeg);
        Vec3 v = new Vec3(Math.sin(a) * vb, 0.0, -Math.cos(a) * vb);
        List<Double> segs = new ArrayList<>();
        for (double left = thickness; left > 1.0e-9; left -= 1.0) {
            segs.add(Math.min(1.0, left));
        }
        boolean drills = p.penetration() > 0 && resistance <= p.penetrationResistance();
        if (drills) {
            for (int i = 0; i < p.penetration() && !segs.isEmpty(); i++) {
                segs.remove(0);
            }
            if (segs.isEmpty()) {
                return new Result(Verdict.DRILL, 0, 1, 0, 0, speedMs, penAt(p, vb), 0, 0, 0, 0);
            }
        }
        if (p.blockPen() <= 0.0f) {
            return new Result(Verdict.DETONATE, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0);
        }
        double pRic = 0.0;
        double ricSpeed = 0.0;
        if (p.penetration() == 0) {
            VoxelShape shape0 = wall(segs.get(0));
            double cap0 = BlockPen.capacity(p.blockPen(), p.muzzleSpeed(), vb);
            if (Ricochet.holds(cap0, resistance, shape0, Direction.SOUTH, v)) {
                Vec3 n = new Vec3(0.0, 0.0, 1.0);
                int bounces = 0;
                for (int i = 0; i < samples; i++) {
                    Vec3 out = Ricochet.bounce(v, n, ricochetDeg, false,
                            BlockPen.seed(seed + i * STRIDE, BlockPos.ZERO));
                    if (out != null) {
                        bounces++;
                        ricSpeed += out.length() * 20.0;
                    }
                }
                pRic = (double) bounces / samples;
                ricSpeed = bounces == 0 ? 0.0 : ricSpeed / bounces;
            }
        }
        Vec3 cur = v;
        double spent = 0.0;
        double depth = 0.0;
        double impulse = 0.0;
        boolean exited = true;
        for (int i = 0; i < segs.size(); i++) {
            double t = segs.get(i);
            double cap = BlockPen.capacity(p.blockPen(), p.muzzleSpeed(), cur.length());
            BlockPen.Pass pass = BlockPen.pass(wall(t), BlockPos.ZERO, new Vec3(0.0, 0.0, t), cur, resistance, cap,
                    BlockPen.seed(seed, new BlockPos(0, 0, -i)));
            spent += pass.spent();
            if (i == 0) {
                Vec3 after = pass.exited() ? pass.velocity() : Vec3.ZERO;
                impulse = p.mass() * cur.subtract(after).length() * 20.0;
            }
            if (!pass.exited()) {
                exited = false;
                depth += new Vec3(0.0, 0.0, t).distanceTo(pass.point());
                break;
            }
            depth += t / Math.max(Math.cos(a), 1.0e-6);
            cur = pass.velocity();
        }
        double exitSpeed = exited ? cur.length() * 20.0 : 0.0;
        double through = 1.0 - pRic;
        double pPerf = exited ? through : 0.0;
        double pStop = exited ? 0.0 : through;
        Verdict verdict = pRic > Math.max(pPerf, pStop) ? Verdict.RICOCHET
                : pPerf >= pStop ? Verdict.PERFORATE : Verdict.STOP;
        return new Result(verdict, pRic, pPerf, pStop, spent, exitSpeed,
                exited ? penAt(p, cur.length()) : 0.0, exited ? 0.0 : depth, impulse,
                toughnessScaled > 0.0f ? impulse / toughnessScaled : 0.0, ricSpeed);
    }

    private static double penAt(KineticPreset p, double blocksPerTick) {
        return BlockPen.capacity(p.blockPen(), p.muzzleSpeed(), blocksPerTick);
    }

    private static VoxelShape wall(double t) {
        return Shapes.box(-WIDE, -WIDE, 0.0, WIDE, WIDE, t);
    }
}
