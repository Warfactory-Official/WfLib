package com.wf.wflib.soldier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Picks which idle line a quiet soldier says. A pack may carry any of the {@link #VARIANTS} next to the plain {@code idle};
 * the situation decides which of the ones it has are in the running. Pure: no world, no clock.
 */
final class IdleVoice {
    /** What a soldier is doing and where, as far as a line could care. */
    record Context(boolean night, boolean raining, boolean hurt, boolean mateNear, long quietTicks) {}

    /** Every idle event a pack may supply, besides {@code idle} itself, which stays the line of last resort. */
    static final List<String> VARIANTS = List.of("idle_alert", "idle_hurt", "idle_night", "idle_rain", "idle_chat", "idle_bored");
    /** Just after a fight: still jumpy, for this long (game ticks). */
    static final long JUMPY = 1200;
    /** Standing around this long without anything happening: bored. */
    static final long BORED = 6000;

    private IdleVoice() {}

    static boolean idle(String event) {
        return event.equals("idle") || event.startsWith("idle_");
    }

    /** The event to play from those {@code available}, or null when the speaker has no idle line at all. */
    static String pick(Set<String> available, Context c, int random) {
        List<String> events = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        add(events, weights, available, "idle_alert", c.quietTicks() < JUMPY ? 6 : 0);
        add(events, weights, available, "idle_hurt", c.hurt() ? 5 : 0);
        add(events, weights, available, "idle_night", c.night() ? 3 : 0);
        add(events, weights, available, "idle_rain", c.raining() ? 3 : 0);
        add(events, weights, available, "idle_chat", c.mateNear() ? 4 : 0);
        add(events, weights, available, "idle_bored", c.quietTicks() >= BORED ? 4 : 0);
        // The plain line stays in the running so a pack with variants still says it sometimes.
        add(events, weights, available, "idle", 2);
        if (events.isEmpty()) return null;
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        int roll = Math.floorMod(random, total);
        for (int i = 0; i < events.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return events.get(i);
        }
        return events.get(events.size() - 1);
    }

    private static void add(List<String> events, List<Integer> weights, Set<String> available, String event, int weight) {
        if (weight > 0 && available.contains(event)) {
            events.add(event);
            weights.add(weight);
        }
    }
}
