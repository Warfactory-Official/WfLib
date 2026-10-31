package com.wf.wflib.packs.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.wf.wflib.core.api.Hook;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Packs of one dependent. Jar roots fixed at {@link PackHosts#register}; {@code scan} rereads the game dir.
 * Read on both sides from disk; the handshake keeps client and server identical.
 */
public final class PackHost {
    private static final Logger LOG = LogUtils.getLogger();

    /** {@code root}: a mod file's {@link PackSpec#jarDir()}; each entry = one pack dir. */
    public record Provider(String mod, Path root) {
    }

    /** {@code <manifestKey>/<ns>/<type>/<path>.json} => {@code <ns>:<path>}. */
    public record Def(ResourceLocation id, Pack pack, JsonObject json) {
    }

    private final PackSpec spec;
    private final List<Provider> providers;
    private final Path dir;
    private final Hook<PackReconciler> reconciler;
    private volatile List<Pack> packs = List.of();
    private List<FileSystem> archives = List.of();

    PackHost(PackSpec spec, List<Provider> providers, Path dir) {
        this.spec = spec;
        this.providers = List.copyOf(providers);
        this.dir = dir;
        this.reconciler = Hook.of(spec.modId() + ":pack_reconciler", server -> mismatch(spec, server, entries()));
    }

    /** No mod list or game dir: tests. Not reachable by the handshake or the pack finder. */
    public static PackHost detached(PackSpec spec, List<Provider> providers, Path dir) {
        return new PackHost(spec, providers, dir);
    }

    public PackSpec spec() {
        return spec;
    }

    public Path packsDir() {
        return dir;
    }

    /** Jar pack sources found at registration, mod id order. */
    public List<Provider> providers() {
        return providers;
    }

    /** Last successful scan. */
    public List<Pack> packs() {
        return packs;
    }

    public List<PackEntry> entries() {
        return packs.stream().map(PackEntry::of).toList();
    }

    /** Client: replaces the default {@link #mismatch} diff against {@link #entries()}; set in the dependent's ctor. */
    public Hook<PackReconciler> reconciler() {
        return reconciler;
    }

    public PackException failure(List<String> problems) {
        return new PackException(spec.displayName() + " packs", problems);
    }

    /**
     * Discovers, validates and hashes every pack, then runs {@code reader} on them: scan or reader throws =>
     * previous packs stay published. {@code reader} must do every fallible step; without {@link PackSpec#holdArchives}
     * zip packs are unreadable once it returns.
     */
    public synchronized <T> T scan(Function<List<Pack>, T> reader) {
        long t0 = System.nanoTime();
        if (spec.createGameDir()) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw failure(List.of(dir + ": " + e));
            }
        }
        PackScanner.Result r = PackScanner.scan(spec, providers, dir);
        T out;
        try {
            out = reader.apply(r.packs());
        } catch (RuntimeException | Error e) {
            close(r.archives());
            throw e;
        }
        if (spec.holdArchives()) {
            close(archives);
            archives = r.archives();
        } else {
            close(r.archives());
        }
        packs = r.packs();
        LOG.info("{} packs ({} jar providers + {}): {} in {} ms", spec.displayName(), providers.size(), dir,
                packs.size(), (System.nanoTime() - t0) / 1_000_000);
        for (Pack p : packs) {
            LOG.info("  {} ({}{} {}): sha256 {}", p.id(), p.origin(), p.mod().isEmpty() ? "" : " " + p.mod(),
                    p.source().getFileName(), p.sha256());
        }
        return out;
    }

    public void rescan() {
        scan(p -> null);
    }

    static void close(List<FileSystem> systems) {
        for (FileSystem fs : systems) {
            try {
                fs.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** {@code assets/<ns>/<path>} of the first pack that has it; empty if none. */
    public Optional<byte[]> asset(ResourceLocation id) throws IOException {
        for (Pack pack : packs) {
            Path file = pack.root().resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
            if (Files.isRegularFile(file)) {
                return Optional.of(Files.readAllBytes(file));
            }
        }
        return Optional.empty();
    }

    /** Raw file of one pack: {@code relative} under its root. */
    @Nullable
    public byte[] file(Pack pack, String relative) throws IOException {
        Path file = pack.root().resolve(relative);
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    /** Duplicate id across packs => throws. */
    public Map<ResourceLocation, Def> defs(String type) {
        Map<ResourceLocation, Def> out = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Pack pack : packs) {
            Path base = pack.root().resolve(spec.manifestKey());
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> namespaces = Files.list(base)) {
                for (Path ns : namespaces.filter(Files::isDirectory).sorted().toList()) {
                    Path typeDir = ns.resolve(type);
                    if (!Files.isDirectory(typeDir)) {
                        continue;
                    }
                    try (Stream<Path> walk = Files.walk(typeDir)) {
                        for (Path file : walk.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                            String path = typeDir.relativize(file).toString().replace('\\', '/');
                            path = path.substring(0, path.length() - ".json".length());
                            ResourceLocation id = ResourceLocation.tryBuild(PackScanner.name(ns), path);
                            if (id == null) {
                                problems.add(pack.id() + ": " + file + ": invalid id");
                                continue;
                            }
                            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                            Def prev = out.putIfAbsent(id, new Def(id, pack, json));
                            if (prev != null) {
                                problems.add(type + " " + id + " in " + prev.pack().id() + " and " + pack.id());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                problems.add(pack.id() + ": " + e);
            }
        }
        if (!problems.isEmpty()) {
            throw new PackException(spec.displayName() + " " + type, problems);
        }
        return out;
    }

    /** Null => identical sets. */
    @Nullable
    public static String mismatch(PackSpec spec, List<PackEntry> server, List<PackEntry> client) {
        // Duplicate client id => "" => reported as different, never a merge exception.
        Map<String, String> c = client.stream().collect(Collectors.toMap(PackEntry::id, PackEntry::sha256, (a, b) -> ""));
        Map<String, String> s = server.stream().collect(Collectors.toMap(PackEntry::id, PackEntry::sha256));
        List<String> missing = new ArrayList<>(), differ = new ArrayList<>(), extra = new ArrayList<>();
        for (PackEntry e : server) {
            String h = c.get(e.id());
            if (h == null) {
                missing.add(e.id());
            } else if (!h.equals(e.sha256())) {
                differ.add(e.id());
            }
        }
        c.keySet().stream().filter(id -> !s.containsKey(id)).sorted().forEach(extra::add);
        if (missing.isEmpty() && differ.isEmpty() && extra.isEmpty()) {
            return null;
        }
        Map<String, String> mods = new TreeMap<>();
        Stream.concat(server.stream(), client.stream()).filter(e -> !e.mod().isEmpty())
                .forEach(e -> mods.put(e.id(), e.mod()));
        List<String> all = Stream.of(missing, differ, extra).flatMap(List::stream).toList();
        StringBuilder b = new StringBuilder(spec.displayName()).append(" packs differ from the server.");
        Map<String, List<String>> parts = new LinkedHashMap<>();
        parts.put("Missing", missing);
        parts.put("Different contents", differ);
        parts.put("Not on the server", extra);
        parts.forEach((k, v) -> {
            if (!v.isEmpty()) {
                b.append('\n').append(k).append(": ").append(String.join(", ", v));
            }
        });
        List<String> jar = all.stream().filter(mods::containsKey).map(id -> id + " (mod " + mods.get(id) + ")")
                .toList();
        if (!jar.isEmpty()) {
            b.append("\nFrom mod jars: ").append(String.join(", ", jar))
                    .append(". Use the server's versions of those mods.");
        }
        if (jar.size() < all.size()) {
            b.append("\nInstall exactly the server's packs into <game dir>/").append(spec.gameDirName())
                    .append("/packs.");
        }
        return b.toString();
    }
}
