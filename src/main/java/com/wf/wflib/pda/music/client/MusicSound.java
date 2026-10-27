package com.wf.wflib.pda.music.client;

import com.wf.wflib.WFLib;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

/**
 * Head-locked, unattenuated, local-only sound. {@code wflib:pda_music} (sounds.json, stream) only routes to
 * {@link #getStream}; gain = {@link MusicPlayer#volume}, set every tick. Source MASTER.
 */
final class MusicSound extends AbstractTickableSoundInstance {
    private static final ResourceLocation EVENT = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_music");

    private final PcmSource source;
    private final long start;
    private volatile MusicStream stream;

    MusicSound(PcmSource source, long start) {
        super(SoundEvent.createVariableRangeEvent(EVENT), SoundSource.MASTER, RandomSource.create());
        this.source = source;
        this.start = start;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.looping = false;
        this.delay = 0;
        this.volume = MusicPlayer.volume();
    }

    /** Sample being heard; the start until the first buffers are queued. */
    long position() {
        MusicStream s = stream;
        return s == null ? start : s.position();
    }

    boolean ended() {
        MusicStream s = stream;
        return s != null && s.ended();
    }

    void end() {
        stop();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MusicStream s = new MusicStream(source.reader(start), source.rate(), source.channels(), start);
                stream = s;
                return s;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Util.backgroundExecutor());
    }

    /** Volume 0 / master 0 still advances the playhead. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        volume = MusicPlayer.volume();
    }
}
