package com.wf.wflib.labs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabFlight;
import com.wf.wflib.labs.api.LabLauncher;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.labs.api.ScenarioStage;
import com.wf.wflib.labs.api.ScenarioStages;
import com.wf.wflib.labs.api.ScenarioState;
import com.wf.wflib.round.api.KineticPreset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/** Runs on the server thread: stages spawn rigs and read the level, which off-thread waits on the thread blocked here. Launcher x range x ordered layers: one row per (case, layer), speed in / out and cumulative probability. Params {@code scenario=<file>} | {@code scenario_json=}. */
public final class ScenarioProducer implements LabProducer {
    public static final String ID = "wflib:scenario";
    static final String EXPIRED = "EXPIRED";

    private static final LabTable TABLE = LabTable.of("wflib:scenario")
            .dimStr("scenario").dimNum("case_id", "").dimStr("case").dimStr("launcher").dimStr("round")
            .dimNum("range_m", "m").dimNum("step", "").dimStr("stage").dimStr("layer")
            .measure("speed_in_ms", "m/s").measure("speed_out_ms", "m/s").measure("p_pass", "").measure("p_reach", "")
            .measureStr("verdict").measureStr("detail")
            .build();

    private final Supplier<Map<String, ScenarioStage>> stages;

    public ScenarioProducer() {
        this(() -> {
            Map<String, ScenarioStage> out = new TreeMap<>();
            ScenarioStages.REGISTRY.entries().forEach(e -> out.put(e.getKey().getPath(), e.getValue()));
            return out;
        });
    }

    public ScenarioProducer(Supplier<Map<String, ScenarioStage>> stages) {
        this.stages = stages;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    @Override
    public String domain() {
        return "shared";
    }

    @Override
    public boolean explicitOnly() {
        return true;
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        ScenarioPlan plan = ScenarioPlan.parse(load(ctx));
        Map<String, ScenarioStage> byId = stages.get();
        for (ScenarioPlan.Case c : plan.cases()) {
            for (JsonObject layer : c.layers()) {
                stage(byId, layer.get("stage").getAsString());
            }
        }
        List<LabLauncher> all = ctx.launchers();
        for (ScenarioPlan.Case c : plan.cases()) {
            if (c.launcher() != null && all.stream().noneMatch(l -> l.id().equals(c.launcher()))) {
                throw new IllegalArgumentException("scenario launcher " + c.launcher() + " not selected; have "
                        + all.size() + " launchers, e.g. " + all.stream().map(LabLauncher::id).limit(5).toList());
            }
        }
        LabContext sc = plan.samples() < 0 ? ctx : new WithSamples(ctx, plan.samples());
        List<List<Object[]>> perLauncher = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            perLauncher.add(new ArrayList<>());
        }
        for (int i = 0; i < all.size(); i++) {
            launcher(sc, plan, byId, all.get(i), perLauncher.get(i));
        }
        perLauncher.forEach(rows -> rows.forEach(sink::row));
    }

    private static JsonObject load(LabContext ctx) {
        String file = ctx.param("scenario", null);
        String inline = ctx.param("scenario_json", null);
        if ((file == null) == (inline == null)) {
            throw new IllegalArgumentException("wflib:scenario needs exactly one of scenario=<file> or scenario_json=<json>");
        }
        try {
            String text = inline != null ? inline : Files.readString(Path.of(file));
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read scenario " + file + ": " + e.getMessage(), e);
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException("scenario is not a JSON object: " + e.getMessage(), e);
        }
    }

    private static ScenarioStage stage(Map<String, ScenarioStage> byId, String id) {
        ScenarioStage s = byId.get(id);
        if (s == null) {
            throw new IllegalArgumentException("unknown scenario stage " + id + "; registered " + new TreeSet<>(byId.keySet()));
        }
        return s;
    }

    private static void launcher(LabContext ctx, ScenarioPlan plan, Map<String, ScenarioStage> byId, LabLauncher l,
                                 List<Object[]> out) {
        KineticPreset p = LabRows.preset(l);
        String round = p.id().toString();
        TreeSet<Double> needed = new TreeSet<>();
        for (double r : ctx.ranges()) {
            needed.add(r);
        }
        plan.cases().forEach(c -> {
            if (c.range() != null) {
                needed.add(c.range());
            }
        });
        double[] rs = needed.stream().mapToDouble(Double::doubleValue).toArray();
        Map<Double, LabFlight.Sample> flight = new java.util.HashMap<>();
        LabFlight.Sample[] fly = LabFlight.fly(p, l.muzzleVelocity(), rs);
        for (LabFlight.Sample s : fly) {
            flight.put(s.range(), s);
        }
        for (ScenarioPlan.Case c : plan.cases()) {
            if (c.launcher() != null && !c.launcher().equals(l.id())) {
                continue;
            }
            double[] ranges = c.range() != null ? new double[]{c.range()} : ctx.ranges();
            for (double range : ranges) {
                LabFlight.Sample s = flight.get(range);
                if (!s.reached()) {
                    out.add(row(plan, c, l, round, range, 0, "flight", "", l.muzzleVelocity(), 0.0, 0.0, 0.0, EXPIRED, null));
                    continue;
                }
                ScenarioState state = new ScenarioState(l.id(), round, p, s.speed(), 1.0, s.range());
                int step = 0;
                for (JsonObject spec : c.layers()) {
                    step++;
                    String stageId = spec.get("stage").getAsString();
                    ScenarioStage.Result r = stage(byId, stageId).apply(ctx, state, withoutStage(spec));
                    ScenarioState next = state.next(r.speedOut(), r.pPass());
                    out.add(row(plan, c, l, round, range, step, stageId, r.label(), state.speedMs(), r.speedOut(),
                            r.pPass(), next.pReach(), r.verdict(), LabRows.json(new TreeMap<>(r.detail()))));
                    if (r.pPass() == 0.0) {
                        break;
                    }
                    state = next;
                }
            }
        }
    }

    private static Object[] row(ScenarioPlan plan, ScenarioPlan.Case c, LabLauncher l, String round, double range,
                                int step, String stage, String label, double in, double out, double pPass,
                                double pReach, String verdict, String detail) {
        return new Object[]{plan.name(), c.id(), c.label(), l.id(), round, range, step, stage, label, in, out, pPass,
                pReach, verdict, detail};
    }

    private record WithSamples(LabContext d, int samples) implements LabContext {
        @Override
        public net.minecraft.server.level.ServerLevel level() {
            return d.level();
        }

        @Override
        public double[] ranges() {
            return d.ranges();
        }

        @Override
        public java.util.function.Predicate<String> rounds() {
            return d.rounds();
        }

        @Override
        public List<LabLauncher> launchers() {
            return d.launchers();
        }

        @Override
        public boolean includeDev() {
            return d.includeDev();
        }

        @Override
        public Path outputDir() {
            return d.outputDir();
        }

        @Override
        public long seed() {
            return d.seed();
        }

        @Override
        public String param(String key, String fallback) {
            return key.equals("samples") ? Integer.toString(samples) : d.param(key, fallback);
        }

        @Override
        public double paramNum(String key, double fallback) {
            return key.equals("samples") ? samples : d.paramNum(key, fallback);
        }

        @Override
        public java.util.concurrent.ExecutorService executor() {
            return d.executor();
        }
    }

    private static JsonObject withoutStage(JsonObject spec) {
        JsonObject o = spec.deepCopy();
        o.remove("stage");
        return o;
    }
}
