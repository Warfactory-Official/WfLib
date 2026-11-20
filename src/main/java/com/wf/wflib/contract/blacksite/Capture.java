package com.wf.wflib.contract.blacksite;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The uplink's capture and payout rules, one step per second, with no Minecraft in them. A holder keeps control with
 * whoever of their group stands in range; a neutral site takes {@link #NEUTRAL_SECONDS} of uncontested presence, a held
 * one {@link #HELD_SECONDS}; both groups in range freezes the capture. Each second a co-holder is in range counts toward
 * the day's payout.
 */
public final class Capture {
    public static final int NEUTRAL_SECONDS = 60, HELD_SECONDS = 120;
    /** Seconds in a day of 24000 ticks. */
    public static final int DAY_SECONDS = 1200;

    @Nullable
    private UUID holder;
    @Nullable
    private UUID challenger;
    private int progress;
    private final Map<UUID, Integer> presence = new HashMap<>();

    @Nullable
    public UUID holder() {
        return holder;
    }

    public int progress() {
        return progress;
    }

    public Map<UUID, Integer> presence() {
        return presence;
    }

    /**
     * One second.
     *
     * @param inRange the players within range of the uplink
     * @param groupOf the player and their group (never null; a lone player is their own group)
     * @return true when control changed hands this second
     */
    public boolean step(Set<UUID> inRange, java.util.function.Function<UUID, Set<UUID>> groupOf) {
        Set<UUID> coHolders = new LinkedHashSet<>();
        if (holder != null) {
            Set<UUID> group = groupOf.apply(holder);
            for (UUID p : inRange) if (p.equals(holder) || group.contains(p)) coHolders.add(p);
        }
        for (UUID p : coHolders) presence.merge(p, 1, Integer::sum);

        Set<UUID> others = new HashSet<>(inRange);
        others.removeAll(coHolders);
        if (others.isEmpty()) {                                     // nobody contesting: a half-done capture decays
            challenger = null;
            progress = Math.max(0, progress - 1);
            return false;
        }
        UUID first = others.iterator().next();
        Set<UUID> side = groupOf.apply(first);
        for (UUID p : others) {
            if (!p.equals(first) && !side.contains(p)) return false;     // two rival groups in range: nobody makes progress
        }
        if (!coHolders.isEmpty()) return false;                      // the holder's group is there: contested, frozen
        if (challenger == null || !groupOf.apply(challenger).contains(first) && !challenger.equals(first)) {
            challenger = first;
            progress = 0;
        }
        progress++;
        int need = holder == null ? NEUTRAL_SECONDS : HELD_SECONDS;
        if (progress < need) return false;
        holder = first;
        challenger = null;
        progress = 0;
        presence.clear();
        presence.merge(first, 1, Integer::sum);
        return true;
    }

    /**
     * Ends a day: splits {@code pool} evenly among the holder's group members who were in range for at least
     * {@code minFraction} of the day, remainder one unit each to the first; the presence counts reset. Empty when nobody qualified.
     */
    public Map<UUID, Integer> closeDay(int pool, double minFraction) {
        int need = (int) Math.ceil(minFraction * DAY_SECONDS);
        Map<UUID, Integer> out = new java.util.LinkedHashMap<>();
        java.util.List<UUID> eligible = presence.entrySet().stream().filter(e -> e.getValue() >= need).map(Map.Entry::getKey).sorted().toList();
        if (!eligible.isEmpty() && pool > 0) {
            int each = pool / eligible.size(), extra = pool % eligible.size();
            for (int i = 0; i < eligible.size(); i++) out.put(eligible.get(i), each + (i < extra ? 1 : 0));
        }
        presence.clear();
        return out;
    }

    /** Forgets everything: the site ended. */
    public void reset() {
        holder = null;
        challenger = null;
        progress = 0;
        presence.clear();
    }

    public void restore(@Nullable UUID holder, int progress, Map<UUID, Integer> presence) {
        this.holder = holder;
        this.progress = progress;
        this.presence.clear();
        this.presence.putAll(presence);
    }
}
