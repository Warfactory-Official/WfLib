package com.wf.wflib.pda.client.theme;

import com.wf.wflib.WFLib;
import com.wf.wflib.WFSounds;
import com.wf.wflib.config.WFClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

/** PDA-owned UI audio, through Minecraft's normal sound engine/master volume. No asset registration or copying. */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class PdaSounds {
    public enum Event { HOVER, CLICK, TAB, OPEN, CLOSE, ALERT }
    // One-line mapping switch: false = hit.wav click / beep.wav hover; true swaps them.
    private static final boolean SWAP_CLICK_AND_HOVER = false;
    private static Event pending;
    private static boolean alert;
    private static long lastUi, lastHover, lastAlert, checked;
    private static String directory = "";
    private static Map<String, Path> files = Map.of();
    private static final List<SoundInstance> owned = new ArrayList<>();
    private static final boolean AUDIT = Boolean.getBoolean("wflib.pda.soundAudit");
    private static final List<Map<String, Object>> receipts = new ArrayList<>();
    private static final AtomicInteger decoded = new AtomicInteger(), failed = new AtomicInteger();
    private PdaSounds() { }

    public static void request(Event event) {
        if (!PdaThemeState.sounds() || Minecraft.getInstance().player == null) return;
        if (event == Event.ALERT) alert = true;
        else if (pending == null || event.ordinal() >= pending.ordinal()) pending = event;
    }
    public static void tab() { request(Event.TAB); }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var manager = Minecraft.getInstance().getSoundManager();
        if (!PdaThemeState.sounds() || Minecraft.getInstance().player == null) {
            pending = null; alert = false;
            owned.forEach(manager::stop); owned.clear(); return;
        }
        owned.removeIf(sound -> !manager.isActive(sound));
        long now = Util.getMillis();
        if (pending != null) {
            Event next = pending; pending = null;
            if (next == Event.HOVER) {
                // Dropped entries are never replayed later while the pointer rests.
                if (now - lastHover >= 140 && now - lastUi >= 140) { lastHover = now; play(next); }
            } else if (next == Event.OPEN || next == Event.CLOSE || now - lastUi >= 50) { lastUi = now; play(next); }
        }
        if (alert) {
            alert = false;
            if (now - lastAlert >= 250) { lastAlert = now; play(Event.ALERT); }
        }
    }
    private static Map<String, Path> files() {
        String selected = System.getProperty("wflib.pda.sounds");
        if (selected == null) {
            try { selected = WFClientConfig.PDA_SOUND_DIRECTORY.get(); }
            catch (IllegalStateException early) { selected = ""; }
        }
        long now = Util.getMillis();
        if (!Objects.equals(selected, directory) || now - checked > 1000) {
            directory = selected; checked = now;
            try { files = selected.isBlank() ? Map.of() : PdaSoundFiles.read(Path.of(selected)); }
            catch (IOException | RuntimeException missing) { files = Map.of(); }
        }
        return files;
    }
    private static SimpleSoundInstance fallback(Event event) {
        // Permanent user-provided mapping: hit.wav = click; beep.wav = hover (sounds.json).
        if (event == Event.HOVER || event == Event.CLICK || event == Event.TAB) {
            boolean hover = (event == Event.HOVER) != SWAP_CLICK_AND_HOVER;
            return SimpleSoundInstance.forUI((hover ? WFSounds.PDA_HOVER : WFSounds.PDA_CLICK).get(), 1, .35f);
        }
        return event == Event.ALERT ? SimpleSoundInstance.forUI(WFSounds.PDA_BUZZ.get(), 1, .8f)
                : SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), event == Event.CLICK ? 1.6f : 1.2f, .35f);
    }
    private static void play(Event event) {
        String key = event.name().toLowerCase(Locale.ROOT);
        // External GAMMA configuration cannot replace permanent click/hover assets.
        boolean builtIn = event == Event.CLICK || event == Event.HOVER;
        Path path = builtIn ? null : files().get(key);
        var fallback = fallback(event);
        SoundInstance instance = path == null ? fallback : new Local(event, path, fallback);
        owned.add(instance);
        Minecraft.getInstance().getSoundManager().play(instance);
        if (AUDIT) {
            if (receipts.size() == 256) receipts.removeFirst();
            receipts.add(Map.of("event", key, "time_ms", Util.getMillis(), "route", builtIn ? "builtin" : path == null ? "fallback" : "local", "path", path == null ? "" : path.toString()));
        }
    }
    public static void resetDiagnostics() { if (AUDIT) { receipts.clear(); decoded.set(0); failed.set(0); } }
    public static Map<String, Object> diagnostics() {
        return Map.of("events", List.copyOf(receipts), "decoded", decoded.get(), "decode_fallbacks", failed.get());
    }
    /** NeoForge's per-instance stream hook loads the external Ogg, without a resource pack or mixin. */
    private static final class Local extends AbstractSoundInstance {
        private final Path file;
        private final SimpleSoundInstance fallback;
        private Sound fallbackClip;
        Local(Event event, Path file, SimpleSoundInstance fallback) {
            super(ResourceLocation.fromNamespaceAndPath("wflib", "local_pda/" + event.name().toLowerCase(Locale.ROOT)),
                    SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.file = file; this.fallback = fallback;
            volume = event == Event.CLICK || event == Event.TAB ? .35f : .5f;
            relative = true; attenuation = Attenuation.NONE;
        }
        @Override public WeighedSoundEvents resolve(SoundManager manager) {
            fallback.resolve(manager); fallbackClip = fallback.getSound();
            sound = new Sound(location, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
            return new WeighedSoundEvents(location, null);
        }
        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return CompletableFuture.<AudioStream>supplyAsync(() -> {
                try {
                    var input = Files.newInputStream(file);
                    try { var stream = new JOrbisAudioStream(input); if (AUDIT) decoded.incrementAndGet(); return stream; }
                    catch (Throwable failure) { input.close(); throw failure; }
                } catch (IOException | RuntimeException failure) { throw new CompletionException(failure); }
            }, Util.nonCriticalIoPool()).exceptionallyCompose(failure -> {
                if (AUDIT) failed.incrementAndGet();
                return buffers.getStream(fallbackClip.getPath(), false);
            });
        }
    }
}
