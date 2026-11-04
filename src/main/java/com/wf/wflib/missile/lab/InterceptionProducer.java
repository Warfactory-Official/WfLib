package com.wf.wflib.missile.lab;

import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabDefender;
import com.wf.wflib.labs.api.LabDefenders;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.missile.api.MissilePreset;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Every registered air defence x threat missile x target offset: the defence placed and left on its own, the threat
 * launched along +x at a ground target {@code offset} to its side, real ticks until the threat ends.
 */
public final class InterceptionProducer implements LabProducer {
    public static final String ID = "wflib:interception";

    private static final LabTable TABLE = LabTable.of("interception")
            .dimStr("defender").dimStr("missile").dimStr("content_origin").dimNum("offset_m", "m")
            .dimNum("approach_m", "m")
            .measure("cruise_ms", "m/s").measure("health", "hp").measure("evasion", "").measure("rcs", "")
            .measure("p_kill", "").measure("p_leak", "").measure("p_fail", "").measure("p_timeout", "")
            .measure("kill_range_m", "m").measure("t_kill_s", "s").measure("spent", "")
            .measure("closest_intercept_m", "m")
            .measure("leak_miss_m", "m").measure("samples", "")
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
        double[] offsets = nums(ctx.param("offsets", "0,100"));
        double approach = ctx.paramNum("approach", 1200);
        int samples = (int) ctx.paramNum("missile_samples", 4);
        int radius = (int) ctx.paramNum("defense_chunks", 14);
        boolean trace = "1".equals(ctx.param("trace", "0"));
        List<LabDefender> defenders = defenders(ctx.param("defenders", null));
        List<Engagement> runs = new ArrayList<>();
        for (LabDefender d : defenders) {
            for (MissilePreset p : MissileLanes.threats(ctx)) {
                for (double off : offsets) {
                    for (int s = 0; s < samples; s++) {
                        runs.add(new Engagement(d, p, off, approach, radius, ctx.seed() + runs.size(), trace));
                    }
                }
            }
        }
        MissileLanes.run(ctx.level(), runs, (int) ctx.paramNum("missile_lanes", 4),
                (int) ctx.paramNum("missile_max_ticks", 6000), ctx.seed());
        for (int i = 0; i < runs.size(); i += samples) {
            row(sink, runs.subList(i, i + samples));
        }
    }

    private static double[] nums(String csv) {
        return Arrays.stream(csv.split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    private static List<LabDefender> defenders(String globs) {
        List<LabDefender> all = LabDefenders.all();
        if (globs == null) {
            return all;
        }
        List<Pattern> want = Arrays.stream(globs.split(","))
                .map(g -> Pattern.compile(Pattern.quote(g).replace("*", "\\E.*\\Q"))).toList();
        return all.stream().filter(d -> want.stream().anyMatch(w -> w.matcher(d.id()).matches())).toList();
    }

    private static void row(LabSink sink, List<Engagement> cell) {
        Engagement e0 = cell.get(0);
        MissilePreset p = e0.preset;
        int n = cell.size();
        double kill = 0;
        double leak = 0;
        double fail = 0;
        double timeout = 0;
        MissileFlightProducer.Mean range = new MissileFlightProducer.Mean();
        MissileFlightProducer.Mean t = new MissileFlightProducer.Mean();
        MissileFlightProducer.Mean spent = new MissileFlightProducer.Mean();
        MissileFlightProducer.Mean miss = new MissileFlightProducer.Mean();
        MissileFlightProducer.Mean closest = new MissileFlightProducer.Mean();
        for (Engagement e : cell) {
            spent.add(e.spent);
            if (e.closest < Double.POSITIVE_INFINITY) {
                closest.add(e.closest);
            }
            switch (e.outcome) {
                case KILL -> {
                    kill++;
                    range.add(e.probe.end().distanceTo(e.defender));
                    t.add(e.probe.endTick() / 20.0);
                }
                case LEAK -> {
                    leak++;
                    miss.add(e.probe.end().subtract(e.target).horizontalDistance());
                }
                case FAIL -> fail++;
                case TIMEOUT -> timeout++;
            }
        }
        sink.row(e0.site.id(), p.id().toString(), MissileLanes.origin(p), e0.offset, e0.approach,
                p.cruiseSpeed() * 20.0, p.health(), p.evasion(), p.rcs(), kill / n, leak / n, fail / n, timeout / n,
                range.get(), t.get(), spent.get(), closest.get(), miss.get(), n);
    }

    enum Outcome {
        KILL, LEAK, FAIL, TIMEOUT
    }

    private static final class Engagement implements MissileLanes.Lane {
        final LabDefender site;
        final MissilePreset preset;
        final double offset;
        final double approach;
        final int radius;
        final long seed;
        final boolean trace;
        Vec3 defender;
        Vec3 target;
        MissileProbe probe;
        LabDefender.Site placed;
        Outcome outcome;
        int spent;
        double closest = Double.POSITIVE_INFINITY;
        private final java.util.Map<UUID, com.wf.wflib.missile.api.WFTelemetry> interceptors = new java.util.LinkedHashMap<>();
        private net.minecraft.world.phys.AABB lane;

        Engagement(LabDefender site, MissilePreset preset, double offset, double approach, int radius, long seed,
                   boolean trace) {
            this.trace = trace;
            this.site = site;
            this.preset = preset;
            this.offset = offset;
            this.approach = approach;
            this.radius = radius;
            this.seed = seed;
        }

        /** Own sides per engagement: lanes never share a recon net. */
        private UUID side(String role) {
            return UUID.nameUUIDFromBytes(("wflabs-" + role + "-" + seed).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public List<ChunkPos> chunks(BlockPos o) {
            Set<ChunkPos> out = new LinkedHashSet<>();
            Vec3 c = Vec3.atCenterOf(o);
            out.addAll(MissileLanes.around(c, c, radius));
            Vec3 tgt = c.add(0.0, 0.0, offset);
            out.addAll(MissileLanes.around(tgt.subtract(approach, 0.0, 0.0), tgt, 1));
            return new ArrayList<>(out);
        }

        @Override
        public void start(ServerLevel level, BlockPos o) {
            defender = Vec3.atBottomCenterOf(o);
            target = new Vec3(o.getX() + 0.5, o.getY(), o.getZ() + 0.5 + offset);
            placed = site.place(level, o, side("defender"));
            lane = new net.minecraft.world.phys.AABB(o).inflate(approach + 64.0, 1024.0, Math.abs(offset) + approach + 64.0);
            Vec3 launch = target.add(-approach, 2.0, 0.0);
            MissileEntity m = MissileProbe.build(level, preset, target, launch, side("attacker"), seed);
            probe = new MissileProbe(m, level.getGameTime());
            level.addFreshEntity(m);
            probe.step(level);
        }

        @Override
        public boolean step(ServerLevel level, int tick) {
            placed.tick();
            for (MissileEntity m : level.getEntitiesOfClass(MissileEntity.class, lane,
                    m -> m.interceptor().isActive() && !interceptors.containsKey(m.getUUID()))) {
                com.wf.wflib.missile.api.WFTelemetry t = com.wf.wflib.missile.api.WFTelemetryService.open(m.getUUID(),
                        level.getGameTime());
                m.attachTelemetry(t);
                interceptors.put(m.getUUID(), t);
            }
            if (level.getEntity(probe.id()) instanceof MissileEntity threat && threat.isAlive()) {
                Vec3 at = threat.getBoundingBox().getCenter();
                for (UUID id : interceptors.keySet()) {
                    if (level.getEntity(id) instanceof MissileEntity i && i.isAlive()) {
                        closest = Math.min(closest, i.position().distanceTo(at));
                    }
                }
            }
            return probe.step(level);
        }

        @Override
        public void finish(ServerLevel level, boolean timedOut) {
            probe.finish(timedOut);
            if (trace) {
                com.mojang.logging.LogUtils.getLogger().info("[labs-trace] {} {} off={} seed={} events={}", site.id(), preset.id(),
                        offset, seed, probe.trace());
            }
            spent = Math.max(placed.spent(), interceptors.size());
            placed.remove();
            Set<Long> intercepts = new java.util.HashSet<>();
            for (Map.Entry<UUID, com.wf.wflib.missile.api.WFTelemetry> e : interceptors.entrySet()) {
                for (com.wf.wflib.missile.api.WFTelemetryEvent ev : e.getValue().events()) {
                    if (ev.type() == com.wf.wflib.missile.api.WFEventType.INTERCEPTED) {
                        intercepts.add(ev.gameTime());
                    }
                }
                com.wf.wflib.missile.api.WFTelemetryService.close(e.getKey());
            }
            if (timedOut) {
                outcome = Outcome.TIMEOUT;
            } else if (probe.downed() || probe.endTick() >= 0 && intercepts.contains(probe.endGameTime())) {
                outcome = Outcome.KILL;
            } else if (probe.fuelOutTick() >= 0 && probe.fuelOutTick() <= probe.endTick()) {
                outcome = Outcome.FAIL;
            } else {
                outcome = Outcome.LEAK;
            }
        }
    }
}
