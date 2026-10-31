package com.wf.wflib.packs.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Discovery, {@code pack.mcmeta} envelope, hash. Problems of one stage across all packs => one {@link PackException}. */
final class PackScanner {
    private static final Pattern PACK_ID = Pattern.compile("[a-z0-9_.-]+");

    record Result(List<Pack> packs, List<FileSystem> archives) {
    }

    private record Source(Path source, Path root, Pack.Origin origin, String mod) {
        String where() {
            return origin == Pack.Origin.JAR ? "pack " + name(source) + " in mod " + mod + "'s jar" : name(source);
        }
    }

    private PackScanner() {
    }

    static Result scan(PackSpec spec, List<PackHost.Provider> providers, Path dir) {
        List<String> problems = new ArrayList<>();
        List<FileSystem> archives = new ArrayList<>();
        try {
            List<Source> sources = new ArrayList<>();
            for (PackHost.Provider j : providers) {
                for (Path e : entries(j.root(), problems)) {
                    if (Files.isDirectory(e)) {
                        sources.add(new Source(e, e, Pack.Origin.JAR, j.mod()));
                    } else {
                        problems.add("mod " + j.mod() + " jar: " + name(e) + ": not a pack directory");
                    }
                }
            }
            for (Path e : entries(dir, problems)) {
                String name = name(e);
                if (Files.isDirectory(e)) {
                    sources.add(new Source(e, e, Pack.Origin.DIRECTORY, ""));
                } else if (name.endsWith(".zip")) {
                    try {
                        FileSystem fs = FileSystems.newFileSystem(e);
                        archives.add(fs);
                        sources.add(new Source(e, fs.getPath("/"), Pack.Origin.ARCHIVE, ""));
                    } catch (IOException ex) {
                        problems.add(name + ": unreadable zip: " + ex);
                    }
                } else {
                    problems.add(name + ": not a pack (directory or .zip)");
                }
            }
            check(spec, problems);
            List<Pack> packs = new ArrayList<>();
            List<Source> kept = new ArrayList<>();
            for (Source s : sources) {
                Pack p = read(spec, s, problems);
                if (p != null) {
                    packs.add(p);
                    kept.add(s);
                }
            }
            check(spec, problems);
            Map<String, Source> byId = new HashMap<>();
            for (int i = 0; i < packs.size(); i++) {
                Pack p = packs.get(i);
                Source other = byId.putIfAbsent(p.id(), kept.get(i));
                if (other != null) {
                    problems.add(p.id() + ": pack.mcmeta: id " + p.id() + " also used by " + other.where()
                            + (other.origin() == Pack.Origin.JAR ? " (remove " + kept.get(i).where() + ")" : ""));
                }
            }
            check(spec, problems);
            for (Pack p : packs) {
                for (String r : p.requires()) {
                    if (!byId.containsKey(r)) {
                        problems.add(p.id() + ": pack.mcmeta: requires " + r + " not installed");
                    }
                }
            }
            check(spec, problems);
            return new Result(List.copyOf(packs), List.copyOf(archives));
        } catch (RuntimeException e) {
            PackHost.close(archives);
            throw e;
        }
    }

    private static void check(PackSpec spec, List<String> problems) {
        if (!problems.isEmpty()) {
            throw new PackException(spec.displayName() + " packs", problems);
        }
    }

    private static List<Path> entries(Path dir, List<String> problems) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> !name(p).startsWith(".")).sorted().toList();
        } catch (IOException e) {
            problems.add(dir + ": " + e);
            return List.of();
        }
    }

    /** Union / zip file system paths may end in '/'. */
    static String name(Path p) {
        return p.getFileName().toString().replace("/", "");
    }

    private static Pack read(PackSpec spec, Source s, List<String> problems) {
        Path meta = s.root().resolve("pack.mcmeta");
        String label = name(s.source());
        if (!Files.isRegularFile(meta)) {
            problems.add(label + ": pack.mcmeta: missing");
            return null;
        }
        JsonElement m;
        try {
            m = JsonParser.parseString(Files.readString(meta));
        } catch (Exception e) {
            problems.add(label + ": pack.mcmeta: invalid JSON: " + e.getMessage());
            return null;
        }
        int before = problems.size();
        if (!(m instanceof JsonObject root)) {
            problems.add(label + ": pack.mcmeta: not a JSON object");
            return null;
        }
        String key = spec.manifestKey();
        JsonObject section = object(root, key, label, problems);
        String id = null;
        List<String> requires = new ArrayList<>();
        if (section != null) {
            if (section.get("id") instanceof JsonPrimitive p && p.isString() && PACK_ID.matcher(p.getAsString()).matches()) {
                id = p.getAsString();
                label = id;
            } else {
                problems.add(label + ": pack.mcmeta: " + key + ".id: " + (section.has("id") ? "invalid pack id "
                        + section.get("id") : "missing"));
            }
            for (var e : section.entrySet()) {
                if (!e.getKey().equals("id") && !e.getKey().equals("requires") && !spec.keys().contains(e.getKey())) {
                    problems.add(label + ": pack.mcmeta: " + key + "." + e.getKey() + ": unknown field");
                }
            }
            if (section.has("requires")) {
                if (section.get("requires") instanceof JsonArray a) {
                    for (JsonElement r : a) {
                        if (r instanceof JsonPrimitive p && p.isString()) {
                            requires.add(p.getAsString());
                        } else {
                            problems.add(label + ": pack.mcmeta: " + key + ".requires: not a string: " + r);
                        }
                    }
                } else {
                    problems.add(label + ": pack.mcmeta: " + key + ".requires: not an array");
                }
            }
        }
        JsonObject pack = object(root, "pack", label, problems);
        String description = "";
        if (pack != null) {
            if (spec.packFormat().isPresent()) {
                int want = spec.packFormat().getAsInt();
                JsonElement f = pack.get("pack_format");
                if (f == null) {
                    problems.add(label + ": pack.mcmeta: pack.pack_format: missing");
                } else if (!(f instanceof JsonPrimitive p && p.isNumber() && p.getAsString().equals("" + want))) {
                    problems.add(label + ": pack.mcmeta: pack.pack_format: " + f + " != " + want);
                }
            }
            if (pack.get("description") instanceof JsonPrimitive d && d.isString()) {
                description = d.getAsString();
            } else {
                problems.add(label + ": pack.mcmeta: pack.description: " + (pack.has("description")
                        ? "not a string" : "missing"));
            }
        }
        if (problems.size() > before) {
            return null;
        }
        String sha;
        try {
            sha = PackHash.walk(s.root(), spec.hashed());
        } catch (IOException e) {
            problems.add(label + ": " + e);
            return null;
        }
        return new Pack(id, description, List.copyOf(requires), s.source(), s.root(), s.origin(), s.mod(), sha, section);
    }

    private static JsonObject object(JsonObject parent, String key, String label, List<String> problems) {
        if (parent.get(key) instanceof JsonObject o) {
            return o;
        }
        problems.add(label + ": pack.mcmeta: " + key + ": " + (parent.has(key) ? "not an object" : "missing"));
        return null;
    }
}
