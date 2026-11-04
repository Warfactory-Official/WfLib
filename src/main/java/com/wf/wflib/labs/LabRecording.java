package com.wf.wflib.labs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wf.wflib.labs.api.LabTable;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code /wflabs record}: tables appended by {@link com.wf.wflib.labs.api.LabRecorder} until stopped. */
public final class LabRecording {
    private static volatile Session session;

    private static final class Session {
        final Path dir;
        final long start = System.nanoTime();
        final Map<String, LabWriter> writers = new LinkedHashMap<>();
        final Map<String, Long> millis = new LinkedHashMap<>();
        final RecorderEvents events = new RecorderEvents();
        final MinecraftServer server;

        Session(Path dir, MinecraftServer server) {
            this.dir = dir;
            this.server = server;
        }
    }

    private LabRecording() {
    }

    public static boolean active() {
        return session != null;
    }

    public static synchronized Path start(MinecraftServer server) {
        if (session != null) {
            throw new IllegalStateException("already recording to " + session.dir);
        }
        Path dir = FMLPaths.GAMEDIR.get().resolve("wflabs")
                .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-shots");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        session = new Session(dir, server);
        NeoForge.EVENT_BUS.register(session.events);
        return dir;
    }

    public static synchronized Path stop() {
        Session s = session;
        if (s == null) {
            throw new IllegalStateException("not recording");
        }
        session = null;
        NeoForge.EVENT_BUS.unregister(s.events);
        JsonArray tables = new JsonArray();
        long ms = (System.nanoTime() - s.start) / 1_000_000L;
        for (Map.Entry<String, LabWriter> e : s.writers.entrySet()) {
            LabWriter w = e.getValue();
            try {
                w.close();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            JsonObject tj = LabRun.tableJson("recorder:" + e.getKey(), w.table(), w.rows(), ms);
            tj.addProperty("domain", "shared");
            tables.add(tj);
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schema", LabRun.SCHEMA);
        manifest.addProperty("created", java.time.Instant.now().toString());
        manifest.addProperty("millis", ms);
        manifest.add("tables", tables);
        manifest.add("mods", LabRun.mods());
        manifest.add("repos", LabRun.repos());
        manifest.add("content", LabsContent.describe(s.server));
        LabRun.write(s.dir, manifest);
        return s.dir;
    }

    public static void record(String table, LabTable schema, Object... row) {
        Session s = session;
        if (s == null) {
            return;
        }
        LabWriter w;
        synchronized (s) {
            w = s.writers.get(table);
            if (w == null) {
                if (!schema.name().equals(table)) {
                    throw new IllegalArgumentException("schema " + schema.name() + " recorded as " + table);
                }
                w = new LabWriter(schema, s.dir.resolve(table + ".ndjson.gz"));
                s.writers.put(table, w);
            } else if (!w.table().equals(schema)) {
                throw new IllegalArgumentException("table " + table + " recorded with two schemas");
            }
        }
        w.row(row);
    }
}
