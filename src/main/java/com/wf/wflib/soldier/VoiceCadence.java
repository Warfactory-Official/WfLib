package com.wf.wflib.soldier;

import java.util.HashMap;
import java.util.Map;

/** Game ticks, not wall time: pauses and replay tick rates preserve the conversation. */
final class VoiceCadence {
    private long speakingUntil = Long.MIN_VALUE;
    private final Map<String, Long> next = new HashMap<>();
    private final Map<String, Integer> last = new HashMap<>();

    boolean ready(String event, long now) {
        return ready(event, now, false);
    }

    boolean ready(String event, long now, boolean urgent) {
        return (urgent || now >= speakingUntil) && now >= next.getOrDefault(event, Long.MIN_VALUE);
    }

    int variant(String event, int count, int random) {
        int pick = Math.floorMod(random, count);
        return count > 1 && pick == last.getOrDefault(event, -1) ? (pick + 1) % count : pick;
    }

    void played(String event, int variant, int ticks, long now) {
        speakingUntil = Math.max(speakingUntil, now + ticks + 8);
        next.put(event, now + Math.max(ticks + 8, switch (event) {
            case "grenade", "throw_grenade", "wounded", "death" -> 100;
            case "idle", "search" -> 600;
            default -> 200;
        }));
        last.put(event, variant);
    }
}
