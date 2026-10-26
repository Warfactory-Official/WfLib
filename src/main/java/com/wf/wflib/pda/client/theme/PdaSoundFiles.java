package com.wf.wflib.pda.client.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Local-only paths, never resources or copied assets. Missing/bad entries simply use built-in audio. */
public final class PdaSoundFiles {
    private static final Set<String> EVENTS = Set.of("click", "tab", "open", "close", "alert");
    public static final long MAX_BYTES = 2 * 1024 * 1024;
    private PdaSoundFiles() { }
    public static Map<String, Path> read(Path directory) throws IOException {
        Path base = directory.toAbsolutePath().normalize();
        Path manifest = base.resolve("sounds.properties");
        if (!Files.isRegularFile(manifest) || Files.size(manifest) > 16384) return Map.of();
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) { values.load(reader); }
        var result = new LinkedHashMap<String, Path>();
        for (String event : EVENTS) {
            String value = values.getProperty(event, "").strip();
            if (value.isEmpty()) continue;
            try {
                Path file = base.resolve(value).normalize();
                if (file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg")
                        && Files.isRegularFile(file) && Files.isReadable(file) && Files.size(file) <= MAX_BYTES)
                    result.put(event, file);
            } catch (RuntimeException | IOException invalidEntry) { /* Per-event fallback. */ }
        }
        return Map.copyOf(result);
    }
}
