package com.wf.wflib.labs;

import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabCover;
import com.wf.wflib.labs.api.LabLauncher;
import com.wf.wflib.labs.api.ScenarioStage;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.lab.CoverStage;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioProducerTest {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static KineticPreset round;
    private static LabLauncher launcher;

    @BeforeAll
    static void fixtures() {
        round = KineticPreset.builder(KineticPresets.rl("scenario_test"), null, Warheads.rl("inert"))
                .speed(20.0).drag(0.002).gravity(0.03).life(400).blockPen(100.0).build();
        KineticPresets.registerItemless(round);
        launcher = new LabLauncher() {
            @Override
            public String id() {
                return "wflib:scenario_test";
            }

            @Override
            public String roundId() {
                return round.id().toString();
            }

            @Override
            public double muzzleVelocity() {
                return 400.0;
            }

            @Override
            public double dispersionMoa() {
                return 0.0;
            }

            @Override
            public double damageScale() {
                return 1.0;
            }
        };
    }

    @AfterAll
    static void shutdown() {
        POOL.shutdownNow();
    }

    private static ScenarioProducer producer() {
        CoverStage cover = new CoverStage(id -> new LabCover.Material(id, 5.0f, 100.0f, 0.0f));
        return new ScenarioProducer(() -> Map.of("cover", cover));
    }

    private static LabContext context(String json) {
        Map<String, String> params = Map.of("scenario_json", json, "samples", "16");
        return new LabContext() {
            public ServerLevel level() {
                return null;
            }

            public double[] ranges() {
                return new double[]{0, 100};
            }

            public Predicate<String> rounds() {
                return id -> true;
            }

            public List<LabLauncher> launchers() {
                return List.of(launcher);
            }

            public boolean includeDev() {
                return false;
            }

            public Path outputDir() {
                return Path.of(".");
            }

            public long seed() {
                return 7L;
            }

            public String param(String key, String fallback) {
                return params.getOrDefault(key, fallback);
            }

            public double paramNum(String key, double fallback) {
                String v = params.get(key);
                return v == null ? fallback : Double.parseDouble(v);
            }

            public ExecutorService executor() {
                return POOL;
            }
        };
    }

    private static List<Object[]> run(String json) {
        List<Object[]> rows = new ArrayList<>();
        producer().produce(context(json), values -> rows.add(values.clone()));
        return rows;
    }

    private static final String TWO_WALLS = """
            {"name":"two","launcher":"wflib:scenario_test","layers":[
              {"stage":"cover","block":"test:a","thickness":0.5},
              {"stage":"cover","block":"test:b","thickness":[0.5,1],"angle":[0,30]}]}""";

    @Test
    void sweepsExpandToTheCartesianProduct() {
        ScenarioPlan plan = ScenarioPlan.parse(com.google.gson.JsonParser.parseString("""
                {"launcher":["a","b"],"range":[0,100,200],"layers":[
                  {"stage":"cover","block":"x","thickness":[0.5,1],"angle":0}]}""").getAsJsonObject());
        assertEquals(12, plan.cases().size());
        assertEquals("launcher=a layer1.thickness=0.5 range=0", plan.cases().get(0).label());
        assertEquals("launcher=b layer1.thickness=1 range=200", plan.cases().get(11).label());
        assertEquals(1.0, plan.cases().get(11).layers().get(0).get("thickness").getAsDouble());
        assertEquals("b", plan.cases().get(11).launcher());
        assertEquals(200.0, plan.cases().get(11).range());
    }

    @Test
    void caseCountIsCapped() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ScenarioPlan.parse(
                com.google.gson.JsonParser.parseString("""
                        {"layers":[{"stage":"cover","block":"x","thickness":[%s],"angle":[%s]}]}"""
                        .formatted(seq(500), seq(500))).getAsJsonObject()));
        assertTrue(e.getMessage().contains("200000"), e.getMessage());
    }

    private static String seq(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            sb.append(i == 1 ? "" : ",").append(i);
        }
        return sb.toString();
    }

    @Test
    void unknownStageListsRegisteredIds() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> run("""
                {"layers":[{"stage":"nope"}]}"""));
        assertTrue(e.getMessage().contains("nope") && e.getMessage().contains("[cover]"), e.getMessage());
    }

    @Test
    void unknownCoverKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> run("""
                {"layers":[{"stage":"cover","block":"x","thickness":1,"width":2}]}"""));
    }

    @Test
    void twoWallsReduceSpeedMonotonically() {
        List<Object[]> rows = run(TWO_WALLS);
        assertEquals(2 * 4 * 2, rows.size());
        for (Object[] r : rows) {
            assertTrue((double) r[10] > 0.0 && (double) r[10] < (double) r[9], "wall slows: " + List.of(r));
        }
        for (int i = 0; i < rows.size(); i += 2) {
            Object[] first = rows.get(i);
            Object[] second = rows.get(i + 1);
            assertEquals(1, first[6]);
            assertEquals(2, second[6]);
            assertEquals(first[10], second[9], "speed out of layer 1 is speed in of layer 2");
            assertTrue((double) second[10] < (double) first[9]);
            assertTrue((double) second[12] <= (double) first[12]);
        }
    }

    @Test
    void rowsAreIdenticalAcrossRuns() {
        List<Object[]> a = run(TWO_WALLS);
        List<Object[]> b = run(TWO_WALLS);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertTrue(java.util.Arrays.equals(a.get(i), b.get(i)), "row " + i);
        }
    }

    @Test
    void chainStopsAfterAZeroPass() {
        ScenarioStage wall = new ScenarioStage() {
            public String id() {
                return "wall";
            }

            public Result apply(LabContext ctx, com.wf.wflib.labs.api.ScenarioState in, com.google.gson.JsonObject spec) {
                return new Result(0.0, 0.0, "STOP", "wall", Map.of());
            }
        };
        List<Object[]> rows = new ArrayList<>();
        new ScenarioProducer(() -> Map.of("wall", wall)).produce(context("""
                {"launcher":"wflib:scenario_test","range":0,"layers":[{"stage":"wall"},{"stage":"wall"}]}"""),
                v -> rows.add(v.clone()));
        assertEquals(1, rows.size());
        assertEquals("STOP", rows.get(0)[13]);
    }
}
