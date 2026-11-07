package com.wf.wflib.soldier;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.wf.wflib.audio.api.AudioCategory;
import com.wf.wflib.audio.api.SoundSpec;
import com.wf.wflib.audio.api.WfAudio;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Server-owned assignment and callout selection. Audio stays in external ZIPs, never in the mod jar. */
final class SoldierVoices {
    static Path root() { return FMLPaths.GAMEDIR.get().resolve("wflib/voices"); }
    static Path bundle() { return root().resolve("generated/voices-resources.zip"); }
    private static Map<String, VoicePack> packs = Map.of();
    private static Map<String, String> teams = Map.of();
    private static final Map<String, VoiceCadence> squads = new java.util.HashMap<>();

    private SoldierVoices() {}

    /** A binding is a pack id, or {@code pack/speaker} to pin every soldier on the team to one speaker. */
    static final String BINDING = "[a-z0-9_]{1,64}(/[a-z0-9_]{1,64})?";

    static java.util.Set<String> ids() { return packs.keySet(); }

    static Map<String, String> teams() { return teams; }

    /** Pack id -> speaker ids, in manifest order, for menus and tab completion. */
    static Map<String, List<String>> catalog() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        packs.forEach((id, pack) -> out.put(id, List.copyOf(pack.speakers().keySet())));
        return out;
    }

    /** Empty string (no voice) or a binding naming an installed pack and, if given, one of its speakers. */
    static boolean valid(String binding) {
        if (binding.isEmpty()) return true;
        if (!binding.matches(BINDING)) return false;
        int slash = binding.indexOf('/');
        VoicePack pack = packs.get(slash < 0 ? binding : binding.substring(0, slash));
        return pack != null && (slash < 0 || pack.speakers().containsKey(binding.substring(slash + 1)));
    }

    static String binding(String pack, String speaker) {
        return speaker == null || speaker.isEmpty() || speaker.equals("auto") ? pack : pack + "/" + speaker;
    }

    static void reload() throws IOException {
        Files.createDirectories(root().resolve("packs"));
        Map<String, VoicePack> loaded = new LinkedHashMap<>();
        long size = 0;
        try (var files = Files.list(root().resolve("packs"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".zip")).sorted().toList()) {
                var pack = VoicePack.read(file);
                if (loaded.putIfAbsent(pack.id(), pack) != null) throw new IOException("Duplicate voice pack: " + pack.id());
                size += Files.size(file);
                if (loaded.size() > 32 || size > VoicePack.MAX_TOTAL) throw new IOException("Voice library exceeds 32 packs / 256 MiB");
            }
        }
        Map<String, String> bindings = readTeams(root().resolve("teams.json"));
        buildBundle(loaded, bundle());
        packs = java.util.Collections.unmodifiableMap(loaded);
        teams = Map.copyOf(bindings);
        squads.clear();
        LogUtils.getLogger().info("Soldier voices: {} packs, {} team assignments, resources {}", packs.size(), teams.size(), bundle());
    }

    static Map<String, String> readTeams(Path file) throws IOException {
        Map<String, String> bindings = new LinkedHashMap<>();
        if (!Files.exists(file)) return bindings;
        if (Files.size(file) > 64 * 1024) throw new IOException("teams.json exceeds 64 KiB");
        try {
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var entry : json.entrySet()) {
                String id = entry.getValue().getAsString();
                if (entry.getKey().isBlank() || entry.getKey().length() > 64 || !id.matches(BINDING))
                    throw new IOException("Invalid team assignment");
                bindings.put(entry.getKey(), id);
            }
        } catch (RuntimeException e) { throw new IOException("Invalid teams.json", e); }
        return bindings;
    }

    /** Binds a team to a voice ({@link #valid}); an empty binding silences the team. Saved atomically to teams.json. */
    static void team(String team, String binding) throws IOException {
        if (team.isBlank() || team.length() > 64) throw new IOException("Invalid team");
        if (!valid(binding)) throw new IOException("Unknown voice pack or speaker: " + binding);
        var changed = new LinkedHashMap<>(teams);
        if (binding.isEmpty()) changed.remove(team); else changed.put(team, binding);
        Files.createDirectories(root());
        Path tmp = Files.createTempFile(root(), "teams-", ".json");
        try {
            Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(changed));
            Files.move(tmp, root().resolve("teams.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            teams = Map.copyOf(changed);
        } finally { Files.deleteIfExists(tmp); }
    }

    static void buildBundle(Map<String, VoicePack> loaded, Path output) throws IOException {
        Files.createDirectories(output.getParent());
        Path tmp = Files.createTempFile(output.getParent(), "voices-", ".zip");
        try {
            try (var out = new ZipOutputStream(Files.newOutputStream(tmp))) {
                var metadata = new ZipEntry("pack.mcmeta"); metadata.setTime(0);
                out.putNextEntry(metadata);
                out.write("{\"pack\":{\"pack_format\":34,\"description\":\"Warforge NPC voices\"}}".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
                long total = 0;
                for (VoicePack pack : loaded.values()) {
                    try (var zip = new ZipFile(pack.file().toFile())) {
                        for (var entry : zip.stream().filter(e -> !e.isDirectory() && e.getName().startsWith("assets/"))
                                .sorted(java.util.Comparator.comparing(ZipEntry::getName)).toList()) {
                            var copy = new ZipEntry(entry.getName()); copy.setTime(0);
                            out.putNextEntry(copy);
                            try (var input = zip.getInputStream(entry)) {
                                byte[] buffer = new byte[8192]; int n;
                                while ((n = input.read(buffer)) != -1) {
                                    if ((total += n) > VoicePack.MAX_TOTAL) throw new IOException("Expanded voice library exceeds 256 MiB");
                                    out.write(buffer, 0, n);
                                }
                            }
                            out.closeEntry();
                        }
                    }
                }
            }
            if (Files.size(tmp) > VoicePack.MAX_TOTAL) throw new IOException("Voice resources exceed 256 MiB");
            Files.move(tmp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }

    static String profile(SoldierAi.Bot b) {
        return b.voice.isEmpty() ? teams.getOrDefault(b.team, "") : b.voice;
    }

    static boolean say(ServerPlayer p, SoldierAi.Bot b, String event) {
        if ((!p.isAlive() || b.health.downed()) && !event.equals("death")) return false;
        String binding = profile(b);
        int slash = binding.indexOf('/');
        VoicePack pack = packs.get(slash < 0 ? binding : binding.substring(0, slash));
        if (pack == null) return false;
        long now = p.level().getGameTime();
        if (!b.voiceCadence.ready(event, now)) return false;
        var pinned = slash < 0 ? null : pack.speakers().get(binding.substring(slash + 1));
        var speakers = List.copyOf(pack.speakers().values());
        var speaker = pinned != null ? pinned : speakers.get(Math.floorMod(p.getUUID().hashCode(), speakers.size()));
        var clips = speaker.get(event);
        if (clips == null) return false; // Missing lines remain silent; never substitute a misleading callout.
        String squad = p.level().dimension().location() + "/" + b.team + "/" + b.squad;
        boolean urgent = event.equals("grenade") || event.equals("death");
        VoiceCadence conversation = squads.computeIfAbsent(squad, k -> new VoiceCadence());
        if (!conversation.ready(event, now, urgent)) return false;
        int variant = b.voiceCadence.variant(event, clips.size(), p.getRandom().nextInt());
        var clip = clips.get(variant);
        WfAudio.at(p.serverLevel(), p.getEyePosition(), AudioCategory.RADIO,
                List.of(SoundSpec.of(clip.sound(), 1, 40)));
        b.voiceCadence.played(event, variant, clip.ticks(), now);
        conversation.played(event, variant, clip.ticks() + 4, now);
        b.lastCallout = event;
        b.callouts++;
        return true;
    }

    /** Plays one line of a binding at the listener, for the menu. Ignores cadence: it is a sample, not a callout. */
    static boolean sample(ServerPlayer listener, String binding) {
        int slash = binding.indexOf('/');
        VoicePack pack = packs.get(slash < 0 ? binding : binding.substring(0, slash));
        if (pack == null) return false;
        var speaker = slash < 0 ? List.copyOf(pack.speakers().values()).get(listener.getRandom().nextInt(pack.speakers().size()))
                : pack.speakers().get(binding.substring(slash + 1));
        if (speaker == null) return false;
        for (String event : SAMPLE_EVENTS) {
            var clips = speaker.get(event);
            if (clips != null) {
                var clip = clips.get(listener.getRandom().nextInt(clips.size()));
                WfAudio.at(listener.serverLevel(), listener.getEyePosition(), AudioCategory.RADIO, List.of(SoundSpec.of(clip.sound(), 1, 40)));
                return true;
            }
        }
        return false;
    }

    private static final List<String> SAMPLE_EVENTS = List.of("contact", "attack", "enemy_down", "reload", "wounded", "idle");

    static void clear() { squads.clear(); }
}
