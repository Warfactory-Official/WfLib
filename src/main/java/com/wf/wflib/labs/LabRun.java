package com.wf.wflib.labs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabLauncher;
import com.wf.wflib.labs.api.LabLaunchers;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabProducers;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** One lab run: producers over one grid into {@code <gameDir>/lab/<timestamp>/}. */
public final class LabRun {
    public static final int SCHEMA = 1;
    public static final long DEFAULT_SEED = 20261103L;
    static final double[] DEFAULT_RANGES = {0, 10, 25, 50, 100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000};
    private static final Logger LOGGER = LogUtils.getLogger();

    private LabRun() {
    }

    /** Grid arguments: {@code ranges=a,b,c | a:b:step}, {@code rounds=glob,glob}, {@code launchers=glob,glob}, any other {@code k=v} = param. */
    public static Map<String, String> parse(String line) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String tok : line.trim().split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            int eq = tok.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("grid argument is key=value: " + tok);
            }
            out.put(tok.substring(0, eq), tok.substring(eq + 1));
        }
        return out;
    }

    public static List<LabProducer> select(String what) {
        List<LabProducer> all = new ArrayList<>(LabProducers.REGISTRY.entries().stream()
                .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList());
        if (what.equals("all")) {
            return all.stream().filter(p -> !p.ticksWorld() || com.wf.wflib.labs.api.LabTicks.available()).toList();
        }
        List<LabProducer> out = new ArrayList<>();
        for (String want : what.split(",")) {
            List<LabProducer> hits = all.stream().filter(p -> p.id().equals(want)
                    || ResourceLocation.parse(p.id()).getPath().equals(want)).toList();
            if (hits.isEmpty()) {
                throw new IllegalArgumentException("no lab producer " + want + "; have "
                        + all.stream().map(LabProducer::id).toList());
            }
            for (LabProducer hit : hits) {
                if (!out.contains(hit)) {
                    out.add(hit);
                }
            }
        }
        return out;
    }

    /** Live view of one run: table headers and row lines as written. Callbacks on producer threads (rows under the writer's monitor) or the run thread; must not block. */
    interface Observer {
        /** @param header table manifest entry (rows, millis zeroed) */
        void table(String id, JsonObject header, int index, int total);

        void row(String id, CharSequence line);

        void tableDone(String id, long rows);

        boolean cancelled();
    }

    record Result(Path dir, JsonObject manifest) {
    }

    /** @return output directory */
    public static Path execute(MinecraftServer server, String what, Map<String, String> args) {
        return run(server, what, args, null, null).dir();
    }

    /**
     * @param observer null = none
     * @param overrides config overrides in effect, recorded as manifest {@code overrides}; null or empty = none
     */
    static Result run(MinecraftServer server, String what, Map<String, String> args, Observer observer,
                      JsonObject overrides) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            throw new IllegalStateException("overworld not loaded");
        }
        List<LabProducer> producers = select(what);
        java.util.Map<String, String> owners = new java.util.HashMap<>();
        for (LabProducer p : producers) {
            String prev = owners.putIfAbsent(p.table().name(), p.id());
            if (prev != null) {
                throw new IllegalStateException("table " + p.table().name() + " written by " + prev + " and " + p.id());
            }
        }
        Path dir = newDir();
        int threads = LabsConfig.snapshot().threads() > 0 ? LabsConfig.snapshot().threads()
                : Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService pool = Executors.newWorkStealingPool(threads);
        long t0 = System.nanoTime();
        JsonArray tables = new JsonArray();
        try {
            Context ctx = new Context(level, ranges(args), roundFilter(args.get("rounds")), args, pool, dir);
            int i = 0;
            for (LabProducer p : producers) {
                tables.add(runOne(p, ctx, dir, observer, i++, producers.size()));
            }
        } catch (RuntimeException | Error e) {
            if (observer != null && observer.cancelled()) {
                deleteTree(dir);
            }
            throw e;
        } finally {
            pool.shutdownNow();
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schema", SCHEMA);
        manifest.addProperty("created", java.time.Instant.now().toString());
        manifest.addProperty("millis", (System.nanoTime() - t0) / 1_000_000L);
        manifest.add("tables", tables);
        manifest.add("grid", grid(args));
        manifest.add("mods", mods());
        manifest.add("repos", repos());
        manifest.add("content", LabsContent.describe(server));
        if (overrides != null && !overrides.isEmpty()) {
            manifest.add("overrides", overrides);
        }
        write(dir, manifest);
        LOGGER.info("[labs] {} tables in {} ms -> {}", tables.size(), (System.nanoTime() - t0) / 1_000_000L, dir);
        return new Result(dir, manifest);
    }

    private static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            LOGGER.warn("[labs] could not remove abandoned run {}: {}", dir, e.toString());
        }
    }

    /** Fresh {@code <gameDir>/wflabs/<yyyyMMdd-HHmmss>[-n]}; never an existing run. */
    static Path newDir() {
        Path base = FMLPaths.GAMEDIR.get().resolve("wflabs");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        try {
            Files.createDirectories(base);
            for (int n = 1; ; n++) {
                Path dir = base.resolve(n == 1 ? stamp : stamp + "-" + n);
                try {
                    return Files.createDirectory(dir);
                } catch (java.nio.file.FileAlreadyExistsException e) {
                    continue;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonObject runOne(LabProducer p, Context ctx, Path dir, Observer observer, int index, int total) {
        LabTable table = p.table();
        long t = System.nanoTime();
        long rows;
        LabWriter.Tap tap = null;
        if (observer != null) {
            JsonObject header = tableJson(p.id(), table, 0, 0);
            header.remove("rows");
            header.remove("millis");
            header.addProperty("domain", p.domain());
            observer.table(p.id(), header, index, total);
            tap = new LabWriter.Tap() {
                @Override
                public void row(CharSequence line) {
                    observer.row(p.id(), line);
                }

                @Override
                public boolean cancelled() {
                    return observer.cancelled();
                }
            };
        }
        try (LabWriter w = new LabWriter(table, dir.resolve(table.name() + ".ndjson.gz"), tap)) {
            p.produce(ctx, w);
            rows = w.rows();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (observer != null) {
            observer.tableDone(p.id(), rows);
        }
        long ms = (System.nanoTime() - t) / 1_000_000L;
        LOGGER.info("[labs] {}: {} rows, {} ms ({} rows/s)", p.id(), rows, ms, ms == 0 ? rows : rows * 1000L / ms);
        JsonObject j = tableJson(p.id(), table, rows, ms);
        j.addProperty("domain", p.domain());
        return j;
    }

    static JsonObject tableJson(String id, LabTable table, long rows, long millis) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", table.name());
        o.addProperty("file", table.name() + ".ndjson.gz");
        JsonArray cols = new JsonArray();
        for (LabTable.Column c : table.columns()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", c.name());
            j.addProperty("unit", c.unit());
            j.addProperty("role", c.role().name().toLowerCase(java.util.Locale.ROOT));
            j.addProperty("kind", c.kind().name().toLowerCase(java.util.Locale.ROOT));
            cols.add(j);
        }
        o.add("columns", cols);
        o.addProperty("rows", rows);
        o.addProperty("millis", millis);
        return o;
    }

    static void write(Path dir, JsonObject manifest) {
        try {
            Files.writeString(dir.resolve("manifest.json"), new GsonBuilder().setPrettyPrinting().create()
                    .toJson(manifest));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonObject grid(Map<String, String> args) {
        JsonObject g = new JsonObject();
        g.addProperty("seed", seedOf(args));
        g.add("ranges", new com.google.gson.Gson().toJsonTree(ranges(args)));
        args.forEach(g::addProperty);
        g.add("armor_exclude", new com.google.gson.Gson().toJsonTree(LabsConfig.snapshot().armorExclude()));
        return g;
    }

    static JsonObject mods() {
        JsonObject o = new JsonObject();
        ModList.get().getMods().forEach(m -> o.addProperty(m.getModId(), m.getVersion().toString()));
        return o;
    }

    static JsonArray repos() {
        List<Path> dirs = new ArrayList<>();
        Path root = GitInfo.root(FMLPaths.GAMEDIR.get());
        String listed = System.getProperty("wflib.lab.repos", "");
        for (String s : listed.split(",")) {
            if (!s.isBlank()) {
                dirs.add(Path.of(s.trim()));
            }
        }
        if (root != null) {
            dirs.add(root);
            Path parent = root.getParent();
            if (parent != null) {
                try (DirectoryStream<Path> kids = Files.newDirectoryStream(parent, Files::isDirectory)) {
                    for (Path k : kids) {
                        if (Files.exists(k.resolve(".git"))) {
                            dirs.add(k);
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warn("[labs] repo scan failed: {}", e.toString());
                }
            }
        }
        List<Path> distinct = dirs.stream().map(p -> p.toAbsolutePath().normalize()).distinct()
                .sorted(Comparator.naturalOrder()).toList();
        JsonArray out = new JsonArray();
        List<Future<JsonObject>> futures = new ArrayList<>();
        ExecutorService ex = Executors.newFixedThreadPool(4);
        try {
            for (Path p : distinct) {
                futures.add(ex.submit(() -> GitInfo.of(p)));
            }
            for (Future<JsonObject> f : futures) {
                out.add(f.get());
            }
        } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e);
        } finally {
            ex.shutdownNow();
        }
        return out;
    }

    static long seedOf(Map<String, String> args) {
        return args.containsKey("seed") ? Long.parseLong(args.get("seed")) : DEFAULT_SEED;
    }

    static double[] ranges(Map<String, String> args) {
        String r = args.get("ranges");
        if (r == null) {
            return DEFAULT_RANGES;
        }
        if (r.contains(":")) {
            String[] p = r.split(":");
            double a = Double.parseDouble(p[0]);
            double b = Double.parseDouble(p[1]);
            double step = Double.parseDouble(p[2]);
            return java.util.stream.DoubleStream.iterate(a, x -> x <= b + 1.0e-9, x -> x + step).toArray();
        }
        return java.util.Arrays.stream(r.split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    static Predicate<String> roundFilter(String spec) {
        return glob(spec);
    }

    static Predicate<String> glob(String spec) {
        if (spec == null || spec.isBlank()) {
            return s -> true;
        }
        List<Pattern> ps = new ArrayList<>();
        for (String g : spec.split(",")) {
            ps.add(Pattern.compile(Pattern.quote(g.trim()).replace("*", "\\E.*\\Q")));
        }
        return id -> {
            String path = id.substring(id.indexOf(':') + 1);
            for (Pattern p : ps) {
                if (p.matcher(id).matches() || p.matcher(path).matches()) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Context implementation. */
    public static final class Context implements LabContext {
        private final ServerLevel level;
        private final double[] ranges;
        private final Predicate<String> rounds;
        private final Map<String, String> params;
        private final ExecutorService executor;
        private final Path dir;
        private volatile List<LabLauncher> launchers;

        public Context(ServerLevel level, double[] ranges, Predicate<String> rounds, Map<String, String> params,
                       ExecutorService executor, Path dir) {
            this.dir = dir;
            this.level = level;
            this.ranges = ranges;
            this.rounds = rounds;
            this.params = params;
            this.executor = executor != null ? executor : java.util.concurrent.ForkJoinPool.commonPool();
        }

        @Override
        public ServerLevel level() {
            return level;
        }

        @Override
        public double[] ranges() {
            return ranges.clone();
        }

        @Override
        public Predicate<String> rounds() {
            return rounds;
        }

        @Override
        public List<LabLauncher> launchers() {
            List<LabLauncher> l = launchers;
            if (l == null) {
                l = launchers = resolveLaunchers();
            }
            return l;
        }

        private List<LabLauncher> resolveLaunchers() {
            Predicate<String> idFilter = glob(params.get("launchers"));
            List<LabLauncher> out = new ArrayList<>();
            for (KineticPreset p : KineticPresets.all()) {
                if (rounds.test(p.id().toString())) {
                    out.add(new Default(p));
                }
            }
            List<LabLauncher> registered = new ArrayList<>(LabLaunchers.REGISTRY.values());
            LabLaunchers.LAZY.values().forEach(sup -> registered.addAll(sup.get()));
            for (LabLauncher l : registered) {
                if (KineticPresets.exists(ResourceLocation.parse(l.roundId())) && rounds.test(l.roundId())) {
                    out.add(l);
                }
            }
            out.removeIf(l -> !idFilter.test(l.id()));
            if (!includeDev()) {
                out.removeIf(l -> com.wf.wflib.labs.api.LabContents.DEV_ORIGIN.equals(com.wf.wflib.labs.api.LabRows.origin(l)));
            }
            out.sort(Comparator.comparing(LabLauncher::id));
            return List.copyOf(out);
        }

        @Override
        public boolean includeDev() {
            return "1".equals(params.get("dev"));
        }

        @Override
        public Path outputDir() {
            if (dir != null) {
                return dir;
            }
            try {
                return Files.createTempDirectory("wflabs");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public long seed() {
            return seedOf(params);
        }

        @Override
        public String param(String key, String fallback) {
            String v = params.get(key);
            return v != null ? v : "samples".equals(key) ? Integer.toString(LabsConfig.snapshot().samples()) : fallback;
        }

        @Override
        public double paramNum(String key, double fallback) {
            String v = param(key, null);
            return v == null ? fallback : Double.parseDouble(v);
        }

        @Override
        public ExecutorService executor() {
            return executor;
        }
    }

    /** The round's own muzzle values, id {@code wflib:<round path>}. */
    record Default(KineticPreset preset) implements LabLauncher {
        @Override
        public String id() {
            return "wflib:" + preset.id().getPath();
        }

        @Override
        public String roundId() {
            return preset.id().toString();
        }

        @Override
        public double muzzleVelocity() {
            return preset.muzzleSpeed() * 20.0;
        }

        @Override
        public double dispersionMoa() {
            return preset.dispersion() * 60.0;
        }

        @Override
        public double damageScale() {
            return 1.0;
        }
    }
}
