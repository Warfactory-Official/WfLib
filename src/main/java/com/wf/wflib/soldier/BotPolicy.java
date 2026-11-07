package com.wf.wflib.soldier;

/** The numbers behind a soldier's judgement calls (reloading, pushing, grenades), apart from the world they act in. */
final class BotPolicy {
    /** A soldier throws a frag at most this often (ticks), and its team at most this often between any two throws. */
    static final long FRAG_COOLDOWN = 900, TEAM_FRAG_COOLDOWN = 500;
    static final long SMOKE_COOLDOWN = 2400, TEAM_SMOKE_COOLDOWN = 1200;
    /** Ticks a contact stays unseen before a soldier considers pushing to it. */
    static final long PUSH_AFTER = 80;
    static final double PUSH_RANGE = 30, PUSH_LEASH = 18;

    private BotPolicy() {
    }

    static boolean ammoLow(int left, int capacity) {
        return left * 10 < capacity * 3;
    }

    /**
     * Reloading is worth the time only when the best spare magazine holds clearly more than the one in the gun;
     * otherwise a soldier would swap one half-empty magazine for another for ever.
     */
    static boolean worthReloading(int left, int capacity, int bestSpare) {
        return bestSpare > 0 && (left == 0 || bestSpare >= left + Math.max(3, capacity / 5));
    }

    /** Out of ammunition for good: an empty gun and nothing better to put in it. */
    static boolean dry(int left, int bestSpare) {
        return left == 0 && bestSpare <= 0;
    }

    static boolean cooledDown(long now, long mine, long team, long myCooldown, long teamCooldown) {
        return now - mine >= myCooldown && now - team >= teamCooldown;
    }

    /** Push on a contact that has not shown itself for a while, when healthy, loaded, and the squad allows a move. */
    static boolean shouldPush(long now, long lastSeen, double distance, float suppression, boolean ammoOk,
                              boolean mayAdvance, double fromAnchor, int holdRadius) {
        return mayAdvance && ammoOk && suppression < 0.3f && now - lastSeen >= PUSH_AFTER
                && distance <= PUSH_RANGE && fromAnchor <= holdRadius + PUSH_LEASH;
    }
}
