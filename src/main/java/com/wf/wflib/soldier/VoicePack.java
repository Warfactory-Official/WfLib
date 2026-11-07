package com.wf.wflib.soldier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

/** External, audio-only ZIP. A pack owns its namespace; it cannot replace other resources. */
record VoicePack(String id, Path file, Map<String, Map<String, List<Clip>>> speakers) {
    static final int MAX_PACK = 64 * 1024 * 1024, MAX_TOTAL = 256 * 1024 * 1024;
    static final Set<String> EVENTS = Set.of("contact", "lost_contact", "search", "attack", "covering",
            "flank", "retreat", "grenade", "throw_grenade", "reload", "wounded", "help", "treat",
            "revive", "revived", "enemy_down", "idle", "death");

    record Clip(ResourceLocation sound, int ticks) {}

    static VoicePack read(Path file) throws IOException {
        if (java.nio.file.Files.size(file) > MAX_PACK) throw new IOException("ZIP exceeds 64 MiB");
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var manifest = zip.getEntry("voices.json");
            if (manifest == null || manifest.getSize() < 0 || manifest.getSize() > 1024 * 1024)
                throw new IOException("Missing or oversized voices.json");
            JsonObject json;
            try (var input = zip.getInputStream(manifest)) {
                json = JsonParser.parseString(new String(input.readNBytes(1024 * 1024 + 1), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            if (json.get("format").getAsInt() != 1) throw new IOException("Unsupported voice pack format");
            String id = json.get("id").getAsString();
            if (!id.matches("[a-z0-9_]{1,64}")) throw new IOException("Invalid pack id");
            long total = 0;
            Set<String> names = new HashSet<>();
            if (zip.size() > 20_000) throw new IOException("Voice pack exceeds 20,000 ZIP entries");
            for (var entry : zip.stream().toList()) {
                String name = entry.getName();
                if (!names.add(name) || name.contains("\\") || name.startsWith("/")
                        || List.of(name.split("/")).contains("..")) throw new IOException("Unsafe or duplicate ZIP path: " + name);
                if (entry.isDirectory()) continue;
                boolean resource = name.startsWith("assets/" + id + "/");
                if (!(name.equals("voices.json") || name.equals("pack.mcmeta") || name.equals("NOTICE.txt") || resource))
                    throw new IOException("Unexpected voice pack entry: " + name);
                if (resource && !(name.equals("assets/" + id + "/sounds.json")
                        || name.matches("assets/" + id + "/sounds/[a-z0-9_./-]+\\.ogg")))
                    throw new IOException("Only sounds are allowed: " + name);
                if (entry.getSize() < 0 || entry.getSize() > MAX_PACK || (total += entry.getSize()) > MAX_PACK)
                    throw new IOException("Expanded ZIP exceeds 64 MiB");
            }
            var soundsEntry = zip.getEntry("assets/" + id + "/sounds.json");
            if (soundsEntry == null || soundsEntry.getSize() > 1024 * 1024) throw new IOException("Missing or oversized sounds.json");
            JsonObject sounds;
            try (var input = zip.getInputStream(soundsEntry)) {
                sounds = JsonParser.parseString(new String(input.readNBytes(1024 * 1024 + 1), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            Map<String, Map<String, List<Clip>>> speakers = new LinkedHashMap<>();
            for (var speaker : json.getAsJsonObject("speakers").entrySet()) {
                if (!speaker.getKey().matches("[a-z0-9_]{1,64}")) throw new IOException("Invalid speaker id");
                Map<String, List<Clip>> events = new LinkedHashMap<>();
                for (var event : speaker.getValue().getAsJsonObject().entrySet()) {
                    if (!EVENTS.contains(event.getKey())) throw new IOException("Unknown callout: " + event.getKey());
                    List<Clip> clips = new ArrayList<>();
                    for (var value : event.getValue().getAsJsonArray()) {
                        var clip = value.getAsJsonObject();
                        var sound = ResourceLocation.parse(clip.get("sound").getAsString());
                        int ticks = clip.get("ticks").getAsInt();
                        if (!sound.getNamespace().equals(id) || ticks < 1 || ticks > 600 || clips.size() >= 128)
                            throw new IOException("Invalid sound, duration or variant count: " + sound);
                        // Each event refers directly to one file: the server chooses the variant for all listeners.
                        var definition = sounds.getAsJsonObject(sound.getPath());
                        if (definition == null || definition.getAsJsonArray("sounds").size() != 1
                                || !definition.getAsJsonArray("sounds").get(0).isJsonPrimitive()
                                || !definition.getAsJsonArray("sounds").get(0).getAsString().equals(sound.toString()))
                            throw new IOException("Sound must reference its own single file: " + sound);
                        var audio = zip.getEntry("assets/" + id + "/sounds/" + sound.getPath() + ".ogg");
                        if (audio == null || audio.getSize() > 4 * 1024 * 1024) throw new IOException("Missing or oversized audio: " + sound);
                        try (var input = zip.getInputStream(audio)) {
                            if (!java.util.Arrays.equals(input.readNBytes(4), new byte[]{'O', 'g', 'g', 'S'}))
                                throw new IOException("Not an OGG stream: " + sound);
                        }
                        clips.add(new Clip(sound, ticks));
                    }
                    if (clips.isEmpty()) throw new IOException("Empty callout: " + event.getKey());
                    events.put(event.getKey(), List.copyOf(clips));
                }
                if (events.isEmpty()) throw new IOException("Empty speaker");
                speakers.put(speaker.getKey(), Map.copyOf(events));
            }
            if (speakers.isEmpty() || speakers.size() > 64) throw new IOException("Expected 1–64 speakers");
            return new VoicePack(id, file, java.util.Collections.unmodifiableMap(speakers));
        } catch (RuntimeException ex) {
            throw new IOException("Invalid voice pack: " + ex.getMessage(), ex);
        }
    }
}
