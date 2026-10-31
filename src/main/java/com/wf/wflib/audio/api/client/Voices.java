package com.wf.wflib.audio.api.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayDeque;

/** Per-owner one-shot voices: past {@code cap} live, the oldest {@link Emitter#fadeOut}s. Client thread. */
public final class Voices {
    /** Ticks of the displaced voice's fade. */
    public static final int FADE_TICKS = 2;

    private final Int2ObjectOpenHashMap<ArrayDeque<Emitter>> live = new Int2ObjectOpenHashMap<>();

    /** {@code e} of {@code owner} just started. */
    public void started(int owner, Emitter e, int cap) {
        ArrayDeque<Emitter> q = live.computeIfAbsent(owner, k -> new ArrayDeque<>());
        q.removeIf(Emitter::isStopped);
        q.addLast(e);
        while (q.size() > cap) {
            q.pollFirst().fadeOut(FADE_TICKS);
        }
    }

    public void clear() {
        live.clear();
    }
}
