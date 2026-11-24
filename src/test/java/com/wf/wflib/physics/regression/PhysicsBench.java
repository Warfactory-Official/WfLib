package com.wf.wflib.physics.regression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.management.ThreadMXBean;
import com.wf.wflib.physics.rigid.NarrowNative;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Physics step microbenchmark over {@link HeadlessStep}: ns/step p50/p95 and allocated bytes/step per fixed scene.
 * Run: {@code ./gradlew physicsBench} (sync, single thread). Writes build/physics-bench/latest.json, compares p50 against
 * src/test/resources/physics-bench-baseline.json, exit 1 when a scene regresses past the tolerance.
 * <ul>
 * <li>{@code -PbenchRefresh} (system property physics.bench.refresh): overwrite the baseline with this run.
 * <li>{@code -PbenchTolerance=0.15} (physics.bench.tolerance): allowed p50 growth, default 0.15.
 * <li>{@code -PbenchJava} (physics.bench.java): Java narrowphase instead of native.
 * </ul>
 * Baselines are per machine: refresh on the machine that compares. Absolute numbers across machines are not comparable.
 */
public final class PhysicsBench {

    private static final int WARM_RUNS = 4, RUNS = 8;

    private record Spec(String name, Supplier<Scenes.Scene> scene, int ticks, int skip) {
    }

    private static final List<Spec> SPECS = List.of(
            new Spec("resting_hull_on_floor", Scenes::resting, 300, 80),
            new Spec("islands_50_bodies_5_islands", () -> Scenes.islands(5, 10), 240, 100),
            new Spec("fast_hull_crossing_sections", Scenes::fast, 200, 0));

    private PhysicsBench() {
    }

    public static void main(String[] args) throws IOException {
        boolean nativeNarrow = NarrowNative.available() && !Boolean.getBoolean("physics.bench.java");
        double tolerance = Double.parseDouble(System.getProperty("physics.bench.tolerance", "0.15"));
        Path baselinePath = Path.of(System.getProperty("physics.bench.baseline", "src/test/resources/physics-bench-baseline.json"));
        Path latestPath = Path.of(System.getProperty("physics.bench.latest", "build/physics-bench/latest.json"));
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();

        Map<String, Object> scenes = new LinkedHashMap<>();
        for (Spec spec : SPECS) {
            List<Long> nanos = new ArrayList<>();
            List<Long> bytes = new ArrayList<>();
            for (int run = 0; run < WARM_RUNS + RUNS; run++) {
                Scenes.Scene scene = spec.scene().get();
                HeadlessStep.Options options = new HeadlessStep.Options();
                options.nativeNarrow = nativeNarrow;
                try (HeadlessStep step = new HeadlessStep(scene.world(), options)) {
                    for (int t = 0; t < spec.ticks(); t++) {
                        long allocated = threads.getThreadAllocatedBytes(thread);
                        long start = System.nanoTime();
                        step.step(scene.bodies());
                        long elapsed = System.nanoTime() - start;
                        allocated = threads.getThreadAllocatedBytes(thread) - allocated;
                        if (run >= WARM_RUNS && t >= spec.skip()) {
                            nanos.add(elapsed);
                            bytes.add(allocated);
                        }
                    }
                }
            }
            long[] n = nanos.stream().mapToLong(Long::longValue).sorted().toArray();
            long[] b = bytes.stream().mapToLong(Long::longValue).sorted().toArray();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("p50Ns", n[n.length / 2]);
            entry.put("p95Ns", n[(int) (n.length * 0.95)]);
            entry.put("allocBytesP50", b[b.length / 2]);
            entry.put("allocBytesMean", Arrays.stream(b).sum() / b.length);
            entry.put("samples", n.length);
            scenes.put(spec.name(), entry);
            System.out.printf(Locale.ROOT, "%-32s p50 %8d ns  p95 %8d ns  alloc p50 %7d B/step%n", spec.name(),
                    entry.get("p50Ns"), entry.get("p95Ns"), entry.get("allocBytesP50"));
        }

        Map<String, Object> machine = new LinkedHashMap<>();
        machine.put("cpu", cpuName());
        machine.put("cores", Runtime.getRuntime().availableProcessors());
        machine.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        machine.put("jvm", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("machine", machine);
        result.put("narrowphase", nativeNarrow ? "native" : "java");
        result.put("scenes", scenes);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(result) + "\n";
        Files.createDirectories(latestPath.toAbsolutePath().getParent());
        Files.writeString(latestPath, json);
        System.out.println("wrote " + latestPath);

        if (Boolean.getBoolean("physics.bench.refresh")) {
            Files.createDirectories(baselinePath.toAbsolutePath().getParent());
            Files.writeString(baselinePath, json);
            System.out.println("baseline refreshed: " + baselinePath);
            return;
        }
        if (!Files.exists(baselinePath)) {
            System.out.println("no baseline at " + baselinePath + "; run with -PbenchRefresh to create it");
            System.exit(2);
        }
        JsonObject baseline = gson.fromJson(Files.readString(baselinePath), JsonObject.class);
        if (!baseline.get("narrowphase").getAsString().equals(result.get("narrowphase"))) {
            System.out.println("baseline narrowphase " + baseline.get("narrowphase").getAsString() + " != " + result.get("narrowphase"));
            System.exit(2);
        }
        JsonObject baselineScenes = baseline.getAsJsonObject("scenes");
        boolean failed = false;
        for (Map.Entry<String, Object> scene : scenes.entrySet()) {
            JsonObject base = baselineScenes.getAsJsonObject(scene.getKey());
            if (base == null) {
                System.out.println("FAIL " + scene.getKey() + ": not in baseline");
                failed = true;
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> now = (Map<String, Object>) scene.getValue();
            double p50 = (Long) now.get("p50Ns"), baseP50 = base.get("p50Ns").getAsDouble();
            double alloc = (Long) now.get("allocBytesMean"), baseAlloc = base.get("allocBytesMean").getAsDouble();
            double timeDelta = p50 / baseP50 - 1, allocDelta = baseAlloc == 0 ? 0 : alloc / baseAlloc - 1;
            boolean timeBad = timeDelta > tolerance, allocBad = allocDelta > tolerance + 0.10;
            System.out.printf(Locale.ROOT, "%s %-32s p50 %+.1f%% (tol %.0f%%)  alloc %+.1f%%%n", timeBad || allocBad ? "FAIL" : "ok  ",
                    scene.getKey(), timeDelta * 100, tolerance * 100, allocDelta * 100);
            failed |= timeBad || allocBad;
        }
        if (failed) {
            System.exit(1);
        }
    }

    private static String cpuName() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                if (line.startsWith("model name")) {
                    return line.substring(line.indexOf(':') + 1).trim();
                }
            }
        } catch (IOException ignored) {
            // not Linux
        }
        return System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unknown");
    }
}
