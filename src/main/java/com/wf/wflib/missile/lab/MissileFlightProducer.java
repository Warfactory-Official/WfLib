package com.wf.wflib.missile.lab;

import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.missile.api.IMissileListener;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.missile.api.MissileListenerData;
import com.wf.wflib.missile.api.MissilePreset;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Every air missile x range: real entity flight from a ground launch to a ground target on real ticks. */
public final class MissileFlightProducer implements LabProducer {
    public static final String ID = "wflib:missile_flight";

    private static final LabTable TABLE = LabTable.of("missile_flight")
            .dimStr("missile").dimStr("content_origin").dimNum("range_m", "m")
            .measureStr("warhead").measureStr("attack_profile").measure("cruise_ms", "m/s").measure("health", "hp")
            .measure("p_detonated", "").measure("p_dud", "").measure("p_timeout", "").measure("p_offloaded", "")
            .measure("t_flight_s", "s").measure("burnout_s", "s").measure("apex_m", "m")
            .measure("impact_ms", "m/s").measure("miss_m", "m").measure("miss_median_m", "m")
            .measure("samples", "")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String domain() {
        return "missile";
    }

    @Override
    public boolean ticksWorld() {
        return true;
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        double[] ranges = Arrays.stream(ctx.param("missile_ranges", "250,500,1000,2000").split(","))
                .mapToDouble(Double::parseDouble).toArray();
        int samples = (int) ctx.paramNum("missile_samples", 4);
        boolean trace = "1".equals(ctx.param("trace", "0"));
        List<Shot> shots = new ArrayList<>();
        for (MissilePreset p : MissileLanes.threats(ctx)) {
            for (double r : ranges) {
                for (int s = 0; s < samples; s++) {
                    shots.add(new Shot(p, r, ctx.seed() + shots.size(), trace));
                }
            }
        }
        MissileLanes.run(ctx.level(), shots, (int) ctx.paramNum("missile_lanes", 16),
                (int) ctx.paramNum("missile_max_ticks", 6000), ctx.seed());
        for (int i = 0; i < shots.size(); i += samples) {
            row(sink, shots.subList(i, i + samples));
        }
    }

    private static void row(LabSink sink, List<Shot> cell) {
        MissilePreset p = cell.get(0).preset;
        int n = cell.size();
        double det = 0;
        double dud = 0;
        double timeout = 0;
        double off = 0;
        Mean t = new Mean();
        Mean burnout = new Mean();
        Mean apex = new Mean();
        Mean impact = new Mean();
        Mean miss = new Mean();
        List<Double> misses = new ArrayList<>();
        for (Shot s : cell) {
            MissileProbe r = s.probe;
            det += r.detonated() ? 1 : 0;
            dud += r.dud() ? 1 : 0;
            timeout += r.timedOut() ? 1 : 0;
            off += r.offloaded() ? 1 : 0;
            if (r.endTick() >= 0) {
                t.add(r.endTick() / 20.0);
                impact.add(r.lastSpeed() * 20.0);
                double m = r.end().subtract(s.target).horizontalDistance();
                miss.add(m);
                misses.add(m);
            }
            if (r.fuelOutTick() >= 0) {
                burnout.add(r.fuelOutTick() / 20.0);
            }
            apex.add(r.apex() - s.ground);
        }
        misses.sort(Double::compare);
        Double median = misses.isEmpty() ? null : misses.size() % 2 == 1 ? misses.get(misses.size() / 2)
                : (misses.get(misses.size() / 2 - 1) + misses.get(misses.size() / 2)) / 2.0;
        sink.row(p.id().toString(), MissileLanes.origin(p), cell.get(0).range, p.warheadId().toString(),
                p.attackProfile().name(), p.cruiseSpeed() * 20.0, p.health(), det / n, dud / n, timeout / n, off / n,
                t.get(), burnout.get(), apex.get(), impact.get(), miss.get(), median, n);
    }

    static final class Mean {
        private double sum;
        private int n;

        void add(double v) {
            sum += v;
            n++;
        }

        Double get() {
            return n == 0 ? null : sum / n;
        }
    }

    private static final class Shot implements MissileLanes.Lane, IMissileListener {
        final MissilePreset preset;
        final double range;
        final long seed;
        Vec3 target;
        double ground;
        MissileProbe probe;
        private Vec3 center;
        private UUID listenerKey;

        private final boolean trace;

        Shot(MissilePreset preset, double range, long seed, boolean trace) {
            this.trace = trace;
            this.preset = preset;
            this.range = range;
            this.seed = seed;
        }

        @Override
        public List<ChunkPos> chunks(BlockPos o) {
            return MissileLanes.around(Vec3.atCenterOf(o), Vec3.atCenterOf(o).add(range, 0.0, 0.0), 1);
        }

        @Override
        public void start(ServerLevel level, BlockPos o) {
            ground = o.getY();
            target = new Vec3(o.getX() + 0.5 + range, ground, o.getZ() + 0.5);
            Vec3 launch = new Vec3(o.getX() + 0.5, ground + 2.0, o.getZ() + 0.5);
            center = launch.lerp(target, 0.5);
            listenerKey = UUID.nameUUIDFromBytes(("wflabs-listener-" + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            MissileListenerData.get(level).register(listenerKey, this);
            MissileEntity m = MissileProbe.build(level, preset, target, launch, null, seed);
            probe = new MissileProbe(m, level.getGameTime());
            level.addFreshEntity(m);
            probe.step(level);
        }

        @Override
        public boolean step(ServerLevel level, int tick) {
            return probe.step(level);
        }

        @Override
        public void finish(ServerLevel level, boolean timedOut) {
            probe.finish(timedOut);
            if (trace) {
                com.mojang.logging.LogUtils.getLogger().info("[labs-trace] {} r={} seed={} events={}", preset.id(), range, seed,
                        probe.trace());
            }
            MissileListenerData.get(level).deregister(listenerKey);
        }

        @Override
        public Vec3 listenerCenter() {
            return center;
        }

        @Override
        public double listenerRange() {
            return range / 2.0 + 256.0;
        }

        @Override
        public boolean listenerValid() {
            return true;
        }
    }
}
