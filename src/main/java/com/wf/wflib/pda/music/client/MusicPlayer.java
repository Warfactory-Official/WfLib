package com.wf.wflib.pda.music.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.wf.wflib.WFLib;
import com.wf.wflib.pda.client.PdaClient;
import com.wf.wflib.pda.music.OggAudio;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client-only playback controller: queue (shuffle / repeat), load -> {@link OggAudio#probe} -> {@link PcmSource} ->
 * {@link MusicSound}. Nothing is sent to other players; stops when the PDA leaves the inventory or on logout.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
final class MusicPlayer {
    enum State {
        IDLE, LOADING, PLAYING, PAUSED
    }

    private record Loaded(MusicTrack track, PcmSource source, OggAudio.Probe probe) {
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long PREV_RESTART = 3;
    private static final int SCRUB_TICKS = 6;
    private static final int SAVE_TICKS = 20;
    /** Server tracks kept so replay / re-queue skips the download. */
    private static final int BYTES_CACHE = 2;
    private static final Map<String, byte[]> CACHE = new LinkedHashMap<>(4, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > BYTES_CACHE;
        }
    };

    private static State state = State.IDLE;
    private static MusicTrack current;
    private static Loaded loaded;
    private static MusicSound sound;
    private static int sinceStart;
    private static long pausedAt;
    private static String error = "";
    private static int loadId;
    private static int failures;

    private static List<MusicTrack> queue = List.of();
    private static List<Integer> order = List.of();
    private static int cursor;

    private static float scrub = -1;
    private static int scrubTicks;
    private static int saveTicks;

    private MusicPlayer() {
    }

    static State state() {
        return state;
    }

    @Nullable
    static MusicTrack current() {
        return current;
    }

    @Nullable
    static OggAudio.Probe probe() {
        return loaded == null ? null : loaded.probe;
    }

    static String error() {
        return error;
    }

    static float volume() {
        return MusicPlaylists.volume();
    }

    static void setVolume(float v) {
        MusicPlaylists.setVolume(Math.clamp(v, 0, 1));
        saveTicks = SAVE_TICKS;
    }

    static int queuePosition() {
        return order.isEmpty() ? 0 : cursor + 1;
    }

    static int queueSize() {
        return order.size();
    }

    static long lengthSamples() {
        return loaded == null ? 0 : loaded.source.length();
    }

    /** Playhead as 0..1; the scrub target while the bar is dragged. */
    static float progress() {
        if (scrub >= 0) {
            return scrub;
        }
        long length = lengthSamples();
        return length <= 0 ? 0 : Math.clamp((float) ((double) positionSamples() / length), 0, 1);
    }

    static long positionSamples() {
        return switch (state) {
            case PLAYING -> sound == null ? 0 : sound.position();
            case PAUSED -> pausedAt;
            default -> 0;
        };
    }

    static long positionMillis() {
        return loaded == null ? 0 : (scrub >= 0 ? (long) (scrub * lengthSamples()) : positionSamples()) * 1000
                / loaded.source.rate();
    }

    static long lengthMillis() {
        return loaded == null ? 0 : lengthSamples() * 1000 / loaded.source.rate();
    }

    /** Server download share while loading, else -1. */
    static int loadingPercent() {
        return state == State.LOADING ? MusicNet.AUDIO.percent() : -1;
    }

    // ---- queue ------------------------------------------------------------------------------------------

    /** Plays {@code tracks[index]} and continues through {@code tracks}. */
    static void playFrom(List<MusicTrack> tracks, int index) {
        if (index < 0 || index >= tracks.size() || tracks.get(index).missing()) {
            return;
        }
        queue = List.copyOf(tracks);
        failures = 0;
        buildOrder(index);
        start(queue.get(index));
    }

    private static void buildOrder(int first) {
        List<Integer> o = new ArrayList<>(queue.size());
        for (int i = 0; i < queue.size(); i++) {
            o.add(i);
        }
        if (MusicPlaylists.shuffle()) {
            java.util.Collections.shuffle(o);
            o.remove(Integer.valueOf(first));
            o.addFirst(first);
        }
        order = o;
        cursor = o.indexOf(first);
    }

    static void setShuffle(boolean on) {
        MusicPlaylists.setShuffle(on);
        if (!order.isEmpty()) {
            buildOrder(order.get(cursor));
        }
    }

    static void cycleRepeat() {
        MusicPlaylists.setRepeat(switch (MusicPlaylists.repeat()) {
            case "OFF" -> "ALL";
            case "ALL" -> "ONE";
            default -> "OFF";
        });
    }

    static void next() {
        advance(false);
    }

    static void previous() {
        if (order.isEmpty()) {
            return;
        }
        if (state == State.PLAYING || state == State.PAUSED) {
            long rate = loaded.source.rate();
            if (positionSamples() > PREV_RESTART * rate) {
                seekSamples(0);
                return;
            }
        }
        for (int i = 1; i <= order.size(); i++) {
            int c = Math.floorMod(cursor - i, order.size());
            if (!queue.get(order.get(c)).missing()) {
                cursor = c;
                start(queue.get(order.get(c)));
                return;
            }
        }
    }

    /** @param auto track ended by itself: repeat OFF stops at the end, ONE replays */
    private static void advance(boolean auto) {
        if (order.isEmpty()) {
            stop();
            return;
        }
        String repeat = MusicPlaylists.repeat();
        if (auto && repeat.equals("ONE") && failures == 0) {
            start(current);
            return;
        }
        for (int i = 1; i <= order.size(); i++) {
            int c = cursor + i;
            boolean wrapped = c >= order.size();
            if (wrapped && auto && repeat.equals("OFF")) {
                stop();
                return;
            }
            c %= order.size();
            MusicTrack candidate = queue.get(order.get(c));
            if (!candidate.missing()) {
                if (wrapped && MusicPlaylists.shuffle()) {
                    buildOrder(order.get(c));
                } else {
                    cursor = c;
                }
                start(candidate);
                return;
            }
        }
        stop();
    }

    // ---- transport --------------------------------------------------------------------------------------

    static void toggle() {
        switch (state) {
            case PLAYING -> {
                pausedAt = sound.position();
                endSound();
                state = State.PAUSED;
            }
            case PAUSED -> {
                startSound(pausedAt);
                state = State.PLAYING;
            }
            default -> {
            }
        }
    }

    static void stop() {
        loadId++;
        MusicNet.AUDIO.cancel();
        endSound();
        release();
        CoverArt.show(null);
        current = null;
        state = State.IDLE;
        scrub = -1;
    }

    /** Debounced: the bar fires per frame while dragged; the decoder restarts once it settles. */
    static void scrubTo(float fraction) {
        if (loaded != null) {
            scrub = Math.clamp(fraction, 0, 1);
            scrubTicks = SCRUB_TICKS;
        }
    }

    private static void seekSamples(long sample) {
        if (loaded == null) {
            return;
        }
        long at = Math.clamp(sample, 0, Math.max(0, loaded.source.length() - 1));
        if (state == State.PLAYING) {
            endSound();
            startSound(at);
        } else if (state == State.PAUSED) {
            pausedAt = at;
        }
    }

    // ---- loading ----------------------------------------------------------------------------------------

    private static void start(MusicTrack track) {
        int id = ++loadId;
        MusicNet.AUDIO.cancel();
        endSound();
        release();
        current = track;
        state = State.LOADING;
        error = "";
        scrub = -1;
        CompletableFuture<byte[]> bytes;
        if (track.source() == MusicTrack.Source.LOCAL) {
            bytes = CompletableFuture.supplyAsync(() -> {
                try {
                    return MusicLibrary.readLocal(track.name());
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }, Util.backgroundExecutor());
        } else {
            byte[] cached = CACHE.get(track.name());
            bytes = cached != null ? CompletableFuture.completedFuture(cached) : MusicNet.AUDIO.fetch(track.name());
        }
        bytes.thenApplyAsync(data -> open(track, data), Util.backgroundExecutor())
                .whenComplete((opened, e) -> Minecraft.getInstance().execute(() -> {
                    if (id != loadId) {
                        if (opened != null) {
                            opened.loaded.source.close();
                            discard(opened.cover);
                        }
                        return;
                    }
                    if (e != null) {
                        failed(e);
                        return;
                    }
                    if (track.source() == MusicTrack.Source.SERVER) {
                        CACHE.put(track.name(), opened.bytes);
                    }
                    loaded = opened.loaded;
                    CoverArt.show(opened.cover);
                    failures = 0;
                    startSound(0);
                    state = State.PLAYING;
                }));
    }

    private record Opened(Loaded loaded, byte[] bytes, @Nullable NativeImage cover) {
    }

    /** Background. Every byte goes through {@link OggAudio#probe} before a decoder sees it. */
    private static Opened open(MusicTrack track, byte[] data) {
        try {
            OggAudio.Probe probe = OggAudio.probe(data);
            PcmSource source = PcmSource.open(data, probe);
            NativeImage cover = null;
            if (probe.tags().cover() != null) {
                try {
                    cover = CoverArt.decode(probe.tags().cover());
                } catch (RuntimeException e) {
                    LOGGER.debug("PDA music cover unreadable: {}", e.toString());
                }
            }
            return new Opened(new Loaded(track, source, probe), data, cover);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void discard(@Nullable NativeImage image) {
        if (image != null) {
            image.close();
        }
    }

    private static void failed(Throwable e) {
        Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null
                ? e.getCause() : e;
        if (cause instanceof java.io.UncheckedIOException u && u.getCause() != null) {
            cause = u.getCause();
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return;
        }
        LOGGER.warn("PDA music '{}' failed: {}", current == null ? "?" : current.name(), cause.toString());
        error = "CANNOT PLAY: " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        failures++;
        state = State.IDLE;
        endSound();
        release();
        if (failures < queue.size()) {
            advance(true);
        }
    }

    private static void release() {
        if (loaded != null) {
            loaded.source.close();
            loaded = null;
        }
    }

    private static void startSound(long sample) {
        sound = new MusicSound(loaded.source, sample);
        sinceStart = 0;
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private static void endSound() {
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            sound = null;
        }
    }

    // ---- events -----------------------------------------------------------------------------------------

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (saveTicks > 0 && --saveTicks == 0) {
            MusicPlaylists.save();
        }
        if (state != State.IDLE && !PdaClient.carried()) {
            stop();
            return;
        }
        if (scrub >= 0 && --scrubTicks <= 0) {
            long at = (long) (scrub * lengthSamples());
            scrub = -1;
            seekSamples(at);
        }
        if (state == State.PLAYING && sound != null) {
            sinceStart++;
            boolean active = Minecraft.getInstance().getSoundManager().isActive(sound);
            if (!active && sinceStart > 5) {
                if (sound.ended()) {
                    advance(true);
                } else {
                    failed(new IOException("audio device unavailable"));
                }
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
        queue = List.of();
        order = List.of();
        CACHE.clear();
        MusicLibrary.clearServer();
    }
}
