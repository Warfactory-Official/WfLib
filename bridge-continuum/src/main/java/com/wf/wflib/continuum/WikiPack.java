package com.wf.wflib.continuum;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Writes Continuum's manual as a client resource pack of WFLib wiki articles ({@link WikiConverter}), under {@code <game dir>/wflib_continuum/resources}, every launch. */
final class WikiPack {
    private WikiPack() {
    }

    /** Continuum's manual pages by path, from its own jar. */
    static Map<String, JsonObject> read() throws IOException {
        Map<String, JsonObject> pages = new LinkedHashMap<>();
        var info = ModList.get().getModFileById("hbm");
        if (info == null) return pages;
        Path manual = info.getFile().findResource("assets/hbm/manual");
        if (!Files.isDirectory(manual)) return pages;
        try (Stream<Path> walk = Files.walk(manual)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".json")).sorted()::iterator) {
                try (Reader in = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    var parsed = JsonParser.parseReader(in);
                    if (parsed.isJsonObject()) pages.put(manual.relativize(p).toString().replace('\\', '/').replaceAll("\\.json$", ""), parsed.getAsJsonObject());
                } catch (RuntimeException e) {
                    // a malformed page is Continuum's to report
                }
            }
        }
        return pages;
    }

    static Path build() throws IOException {
        Path root = FMLPaths.GAMEDIR.get().resolve(ContinuumBridge.ID).resolve("resources");
        if (Files.exists(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
            }
        }
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":34,\"description\":\"WFLib: Continuum manual as wiki articles (generated at launch)\"}}", StandardCharsets.UTF_8);
        Map<String, String> articles = WikiConverter.convert(read());
        for (Map.Entry<String, String> e : articles.entrySet()) {
            Path file = root.resolve("assets/" + WikiConverter.NAMESPACE + "/wiki/" + e.getKey() + ".md");
            Files.createDirectories(file.getParent());
            Files.writeString(file, e.getValue(), StandardCharsets.UTF_8);
        }
        ContinuumBridge.LOG.info("Wiki: {} Continuum manual page(s) written as WFLib articles", articles.size());
        return root;
    }
}
