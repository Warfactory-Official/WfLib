package com.wf.wflib.pda.music;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import com.wf.wflib.core.api.WFLib;

/**
 * Server library {@code <world>/pda_music/**.{ogg,opus,oga}}. Only files passing {@link OggAudio#probe} are listed
 * or served; served bytes are re-probed per fetch. Symlinks never followed, path never leaves the folder.
 * Playback is client-local: the server only streams the bytes, {@link #CHUNKS_PER_TICK} chunks per tick per player.
 * {@code <world>/pda_backgrounds} (flat) serves {@link BackgroundImage}s over the same chunk path, one transfer
 * per (player, kind).
 */
@EventBusSubscriber(modid = WFLib.MODID)
public final class ServerMusic {
    public static final int MAX_ENTRIES = 512;
    static final String DIR = "pda_music";
    static final String BACKGROUND_DIR = "pda_backgrounds";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_DEPTH = 4;
    private static final int CHUNKS_PER_TICK = 4;

    private record Cached(long size, long modified, OggAudio.Probe probe) {
    }

    private static final class Transfer {
        final int id;
        final byte[] data;
        final int total;
        int next;

        Transfer(int id, byte[] data) {
            this.id = id;
            this.data = data;
            this.total = (data.length + MusicChunkPacket.CHUNK - 1) / MusicChunkPacket.CHUNK;
        }
    }

    private static final Map<String, Cached> CACHE = new HashMap<>();
    private record Key(UUID player, boolean background) {
    }

    private static final Map<Key, Transfer> TRANSFERS = new HashMap<>();
    private static final Map<Key, Long> LAST_LIST = new HashMap<>();
    /** Server thread. */
    private static final Map<Key, Integer> LOADING = new HashMap<>();

    private ServerMusic() {
    }

    private static Path root(MinecraftServer server) {
        return server.getWorldPath(new LevelResource(DIR)).toAbsolutePath().normalize();
    }

    private static Path backgroundRoot(MinecraftServer server) {
        return server.getWorldPath(new LevelResource(BACKGROUND_DIR)).toAbsolutePath().normalize();
    }

    static void list(ServerPlayer player, boolean background) {
        long now = Util.getMillis();
        Long last = LAST_LIST.put(new Key(player.getUUID(), background), now);
        if (last != null && now - last < 1000) {
            return;
        }
        MinecraftServer server = player.server;
        if (background) {
            Path folder = backgroundRoot(server);
            Util.backgroundExecutor().execute(() -> {
                try {
                    Files.createDirectories(folder);
                } catch (IOException e) {
                    LOGGER.warn("PDA background folder {}: {}", folder, e.toString());
                }
                List<String> names = scanBackgrounds(folder);
                server.execute(() -> {
                    if (!player.hasDisconnected()) {
                        PacketDistributor.sendToPlayer(player, new BackgroundListPacket(names));
                    }
                });
            });
            return;
        }
        Path root = root(server);
        Util.backgroundExecutor().execute(() -> {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                LOGGER.warn("PDA music folder {}: {}", root, e.toString());
            }
            List<MusicListPacket.Entry> entries = scan(root);
            server.execute(() -> {
                if (!player.hasDisconnected()) {
                    PacketDistributor.sendToPlayer(player, new MusicListPacket(entries));
                }
            });
        });
    }

    static void fetch(ServerPlayer player, int id, boolean background, String name) {
        Key who = new Key(player.getUUID(), background);
        TRANSFERS.remove(who);
        LOADING.put(who, id);
        if (name.isEmpty()) {
            LOADING.remove(who);
            return;
        }
        MinecraftServer server = player.server;
        Path root = background ? backgroundRoot(server) : root(server);
        Util.backgroundExecutor().execute(() -> {
            byte[] data = null;
            try {
                data = background ? readBackground(root, name) : read(root, name);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("PDA music fetch '{}' refused: {}", name, e.toString());
            }
            byte[] bytes = data;
            server.execute(() -> {
                if (player.hasDisconnected() || !Integer.valueOf(id).equals(LOADING.get(who))) {
                    return;
                }
                LOADING.remove(who);
                if (bytes == null) {
                    PacketDistributor.sendToPlayer(player, new MusicChunkPacket(id, 0, 0, new byte[0]));
                } else {
                    TRANSFERS.put(who, new Transfer(id, bytes));
                }
            });
        });
    }

    /** Whole file, probed. */
    private static byte[] read(Path root, String name) throws IOException {
        if (!OggAudio.validName(name)) {
            throw new IOException("bad name");
        }
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > OggAudio.MAX_BYTES) {
            throw new IOException("not a library file");
        }
        byte[] data = Files.readAllBytes(file);
        OggAudio.probe(data);
        return data;
    }

    private static byte[] readBackground(Path root, String name) throws IOException {
        if (!BackgroundImage.validName(name)) {
            throw new IOException("bad name");
        }
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > BackgroundImage.MAX_BYTES) {
            throw new IOException("not a background file");
        }
        byte[] data = Files.readAllBytes(file);
        BackgroundImage.check(data);
        return data;
    }

    private static List<String> scanBackgrounds(Path root) {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return out;
        }
        try (Stream<Path> list = Files.list(root)) {
            for (Path file : list.sorted().toList()) {
                String name = file.getFileName().toString();
                if (out.size() >= BackgroundImage.MAX_ENTRIES || !BackgroundImage.validName(name)
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try {
                    BackgroundImage.check(Files.readAllBytes(file));
                    out.add(name);
                } catch (IOException e) {
                    LOGGER.warn("PDA background '{}' rejected: {}", name, e.toString());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("PDA background scan failed: {}", e.toString());
        }
        return out;
    }

    private static synchronized List<MusicListPacket.Entry> scan(Path root) {
        List<MusicListPacket.Entry> out = new ArrayList<>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
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
            if (out.size() >= MAX_ENTRIES) {
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
                    c = new Cached(size, modified, OggAudio.probe(Files.readAllBytes(file)));
                }
                seen.put(name, c);
                OggAudio.Tags t = c.probe.tags();
                out.add(new MusicListPacket.Entry(name, t.title(), t.artist(), t.album(), c.probe.millis(),
                        (int) size));
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("PDA music '{}' rejected: {}", name, e.toString());
            }
        }
        CACHE.clear();
        CACHE.putAll(seen);
        return out;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (TRANSFERS.isEmpty()) {
            return;
        }
        for (var it = TRANSFERS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey().player());
            Transfer t = e.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            for (int i = 0; i < CHUNKS_PER_TICK && t.next < t.total; i++) {
                int from = t.next * MusicChunkPacket.CHUNK;
                int to = Math.min(t.data.length, from + MusicChunkPacket.CHUNK);
                PacketDistributor.sendToPlayer(player,
                        new MusicChunkPacket(t.id, t.next++, t.total, Arrays.copyOfRange(t.data, from, to)));
            }
            if (t.next >= t.total) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        for (boolean background : new boolean[]{false, true}) {
            Key key = new Key(id, background);
            TRANSFERS.remove(key);
            LOADING.remove(key);
            LAST_LIST.remove(key);
        }
    }
}
