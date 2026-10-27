package com.wf.wflib.pda.music.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** {@code config/wflib_pda_music.json}: playlists + player settings. Entries are (source, file name) refs. */
final class MusicPlaylists {
    static final int MAX_LISTS = 64;
    static final int MAX_TRACKS = 1000;
    static final int MAX_NAME = 32;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class Ref {
        String src;
        String file;

        Ref(MusicTrack.Source src, String file) {
            this.src = src.name();
            this.file = file;
        }

        MusicTrack.Source source() {
            return MusicTrack.Source.valueOf(src);
        }
    }

    static final class Playlist {
        String name;
        List<Ref> tracks = new ArrayList<>();

        Playlist(String name) {
            this.name = name;
        }
    }

    private static final class Data {
        List<Playlist> playlists = new ArrayList<>();
        float volume = 0.8F;
        boolean shuffle;
        String repeat = "OFF";
    }

    private static Data data;
    private static volatile int version;

    private MusicPlaylists() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("wflib_pda_music.json");
    }

    private static Data data() {
        if (data == null) {
            data = new Data();
            try {
                Path f = file();
                if (Files.isRegularFile(f) && Files.size(f) < (4 << 20)) {
                    Data read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
                    if (read != null) {
                        data = sanitised(read);
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("PDA music settings unreadable, starting empty: {}", e.toString());
            }
        }
        return data;
    }

    private static Data sanitised(Data in) {
        Data out = new Data();
        out.volume = Float.isNaN(in.volume) ? 0.8F : Math.clamp(in.volume, 0, 1);
        out.shuffle = in.shuffle;
        out.repeat = "ONE".equals(in.repeat) || "ALL".equals(in.repeat) ? in.repeat : "OFF";
        if (in.playlists != null) {
            for (Playlist p : in.playlists) {
                if (p == null || p.name == null || out.playlists.size() >= MAX_LISTS) {
                    continue;
                }
                Playlist clean = new Playlist(p.name.length() > MAX_NAME ? p.name.substring(0, MAX_NAME) : p.name);
                if (p.tracks != null) {
                    for (Ref r : p.tracks) {
                        if (r != null && r.file != null && com.wf.wflib.pda.music.OggAudio.validName(r.file)
                                && ("SERVER".equals(r.src) || "LOCAL".equals(r.src))
                                && clean.tracks.size() < MAX_TRACKS) {
                            clean.tracks.add(r);
                        }
                    }
                }
                out.playlists.add(clean);
            }
        }
        return out;
    }

    static void save() {
        version++;
        String json = GSON.toJson(data());
        Util.ioPool().execute(() -> {
            try {
                Path f = file();
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOGGER.warn("PDA music settings not saved: {}", e.toString());
            }
        });
    }

    static int version() {
        return version;
    }

    static List<Playlist> lists() {
        return data().playlists;
    }

    static Playlist create(String name) {
        String clean = name.strip();
        if (clean.isEmpty() || data().playlists.size() >= MAX_LISTS) {
            return null;
        }
        Playlist p = new Playlist(clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean);
        data().playlists.add(p);
        save();
        return p;
    }

    static void delete(Playlist p) {
        if (data().playlists.remove(p)) {
            save();
        }
    }

    /** @return false if already in or full */
    static boolean add(Playlist p, MusicTrack t) {
        for (Ref r : p.tracks) {
            if (r.source() == t.source() && r.file.equals(t.name())) {
                return false;
            }
        }
        if (p.tracks.size() >= MAX_TRACKS) {
            return false;
        }
        p.tracks.add(new Ref(t.source(), t.name()));
        save();
        return true;
    }

    static void remove(Playlist p, int index) {
        if (index >= 0 && index < p.tracks.size()) {
            p.tracks.remove(index);
            save();
        }
    }

    /** Tracks in order; entries not in the loaded library come back as {@link MusicTrack#missing}. */
    static List<MusicTrack> resolve(Playlist p) {
        List<MusicTrack> out = new ArrayList<>(p.tracks.size());
        for (Ref r : p.tracks) {
            MusicTrack t = MusicLibrary.find(r.source(), r.file);
            out.add(t != null ? t : MusicTrack.missing(r.source(), r.file));
        }
        return out;
    }

    static float volume() {
        return data().volume;
    }

    static void setVolume(float v) {
        data().volume = v;
    }

    static boolean shuffle() {
        return data().shuffle;
    }

    static void setShuffle(boolean on) {
        data().shuffle = on;
        save();
    }

    static String repeat() {
        return data().repeat;
    }

    static void setRepeat(String mode) {
        data().repeat = mode;
        save();
    }
}
