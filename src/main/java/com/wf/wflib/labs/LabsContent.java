package com.wf.wflib.labs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wf.wflib.labs.api.LabContent;
import com.wf.wflib.labs.api.LabContents;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Manifest {@code content}: what exactly was loaded when the rows were computed. */
final class LabsContent {
    private static final Map<Path, String> HASHES = new ConcurrentHashMap<>();

    private LabsContent() {
    }

    static JsonObject describe(MinecraftServer server) {
        JsonObject o = new JsonObject();
        JsonObject env = new JsonObject();
        env.addProperty("production", FMLEnvironment.production);
        env.addProperty("dist", FMLEnvironment.dist.name());
        env.addProperty("whole_catalogue_requested", !FMLEnvironment.production
                && LabsSystem.headlessRequested());
        o.add("environment", env);

        JsonArray jars = new JsonArray();
        ModList.get().getMods().forEach(m -> {
            JsonObject j = new JsonObject();
            j.addProperty("id", m.getModId());
            j.addProperty("version", m.getVersion().toString());
            Path file = m.getOwningFile().getFile().getFilePath();
            j.addProperty("file", file.getFileName().toString());
            j.addProperty("sha256", Files.isRegularFile(file) ? sha256(file) : null);
            jars.add(j);
        });
        o.add("mod_jars", jars);

        JsonObject packs = new JsonObject();
        Set<String> configFiles = new TreeSet<>();
        for (LabContent c : LabContents.REGISTRY.values()) {
            JsonObject p = new JsonObject();
            c.packs().forEach(p::addProperty);
            packs.add(c.id(), p);
            configFiles.addAll(c.configFiles());
        }
        o.add("packs", packs);

        JsonArray data = new JsonArray();
        server.getPackRepository().getSelectedIds().forEach(data::add);
        o.add("datapacks", data);

        JsonObject configs = new JsonObject();
        Map<String, String> files = new TreeMap<>();
        Path dir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        try (var list = Files.list(dir)) {
            for (Path p : (Iterable<Path>) list::iterator) {
                String name = p.getFileName().toString();
                if (Files.isRegularFile(p) && (name.startsWith("wflib-") || configFiles.contains(name))) {
                    files.put(name, Files.readString(p));
                }
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        files.forEach(configs::addProperty);
        o.add("configs", configs);
        return o;
    }

    private static String sha256(Path file) {
        return HASHES.computeIfAbsent(file, f -> {
            try (InputStream in = Files.newInputStream(f)) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) {
                    md.update(buf, 0, n);
                }
                return HexFormat.of().formatHex(md.digest());
            } catch (IOException | java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
