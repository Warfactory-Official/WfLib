package com.wf.wflib.pda.music.client;

import com.mojang.logging.LogUtils;
import com.wf.wflib.pda.music.MusicListPacket;
import com.wf.wflib.pda.music.MusicListRequestPacket;
import com.wf.wflib.pda.music.OggAudio;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Client view of both sources. LOCAL = {@code <gameDir>/pda_music/**.{ogg,opus,oga}}, each file fully
 * {@link OggAudio#probe probed} (cached by size + mtime); rejects are logged and absent. SERVER = last
 * {@link MusicListPacket}.
 */
final class MusicLibrary {
    static final int MAX_LOCAL = 2000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_DEPTH = 4;

    private record Cached(long size, long modified, MusicTrack track) {
    }

    private static volatile List<MusicTrack> local = List.of();
    private static volatile List<MusicTrack> server = List.of();
    private static volatile boolean scanning;
    private static volatile boolean serverWaiting;
    private static volatile int version;
    private static final Map<String, Cached> CACHE = new HashMap<>();

    private MusicLibrary() {
    }

    static Path localDir() {
        return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().resolve("pda_music");
    }

    static List<MusicTrack> local() {
        return local;
    }

    static List<MusicTrack> server() {
        return server;
    }

    static boolean scanning() {
        return scanning;
    }

    static boolean serverWaiting() {
        return serverWaiting;
    }

    /** Bumped on every change, for cached row lists. */
    static int version() {
        return version;
    }

    static void refreshServer() {
        serverWaiting = true;
        PacketDistributor.sendToServer(MusicListRequestPacket.AUDIO);
    }

    static void acceptServer(MusicListPacket packet) {
        List<MusicTrack> tracks = new ArrayList<>(packet.entries().size());
        for (MusicListPacket.Entry e : packet.entries()) {
            if (OggAudio.validName(e.name()) && e.millis() >= 0) {
                tracks.add(new MusicTrack(MusicTrack.Source.SERVER, e.name(), e.title(), e.artist(), e.album(),
                        e.millis(), e.size()));
            }
        }
        server = List.copyOf(tracks);
        serverWaiting = false;
        version++;
    }

    static void clearServer() {
        server = List.of();
        serverWaiting = false;
        version++;
    }

    static void refreshLocal() {
        if (scanning) {
            return;
        }
        scanning = true;
        Path root = localDir();
        Util.backgroundExecutor().execute(() -> {
            List<MusicTrack> found = scan(root);
            Minecraft.getInstance().execute(() -> {
                local = found;
                scanning = false;
                version++;
            });
        });
    }

    private static List<MusicTrack> scan(Path root) {
        List<MusicTrack> out = new ArrayList<>();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            LOGGER.warn("PDA music folder {}: {}", root, e.toString());
            return out;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
            files = walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        } catch (IOException e) {
            LOGGER.warn("PDA music scan failed: {}", e.toString());
            return out;
        }
        Map<String, Cached> seen = new HashMap<>();
        for (Path file : files) {
            if (out.size() >= MAX_LOCAL) {
                break;
            }
            String name = root.relativize(file).toString().replace('\\', '/');
            if (!OggAudio.validName(name)) {
                continue;
            }
            try {
                long size = Files.size(file);
                long modified = Files.getLastModifiedTime(file).toMillis();
                Cached c = CACHE.get(name);
                if (c == null || c.size != size || c.modified != modified) {
                    if (size > OggAudio.MAX_BYTES) {
                        throw new IOException("over " + OggAudio.MAX_BYTES + " bytes");
                    }
                    c = new Cached(size, modified,
                            MusicTrack.local(name, OggAudio.probe(Files.readAllBytes(file)), size));
                }
                seen.put(name, c);
                out.add(c.track);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("PDA music '{}' rejected: {}", name, e.toString());
            }
        }
        synchronized (CACHE) {
            CACHE.clear();
            CACHE.putAll(seen);
        }
        return out;
    }

    /** Whole local file, size-checked; caller probes. */
    static byte[] readLocal(String name) throws IOException {
        Path root = localDir();
        if (!OggAudio.validName(name)) {
            throw new IOException("bad name");
        }
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > OggAudio.MAX_BYTES) {
            throw new IOException("not a library file");
        }
        return Files.readAllBytes(file);
    }

    static MusicTrack find(MusicTrack.Source source, String name) {
        for (MusicTrack t : source == MusicTrack.Source.LOCAL ? local : server) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return null;
    }
}
