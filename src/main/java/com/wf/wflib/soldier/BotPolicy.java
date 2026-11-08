package com.wf.wflib.soldier;

/** The numbers behind a soldier's judgement calls (reloading, pushing, grenades), apart from the world they act in. */
final class BotPolicy {
    /** A soldier throws a frag at most this often (ticks), and its team at most this often between any two throws. */
    static final long FRAG_COOLDOWN = 900, TEAM_FRAG_COOLDOWN = 500;
    static final long SMOKE_COOLDOWN = 2400, TEAM_SMOKE_COOLDOWN = 1200;
    /** Ticks a contact stays unseen before a soldier considers pushing to it. */
    static final long PUSH_AFTER = 80;
    static final double PUSH_RANGE = 30, PUSH_LEASH = 18;
    /**
     * Ticks a soldier remembers where an enemy was after last seeing or hearing it: a glimpse or a rumour for 15 s, an
     * enemy it was really fighting for a minute. Smoke, cover and a lost sight line all end a firefight without ending
     * it, so the fight must outlast the first quiet moments instead of being forgotten into idling.
     */
    static final long GLIMPSE_MEMORY = 300, CONTACT_MEMORY = 1200;

    static boolean forgotten(long now, long lastSeen, boolean confirmed) {
        return now - lastSeen > (confirmed ? CONTACT_MEMORY : GLIMPSE_MEMORY);
    }

    /**
     * Light (0-15, block and sky together, so night, storms and caves all count) at a soldier's eyes at or below which
     * it switches its light on, and at or above which it switches it off again; the gap stops dusk flickering it.
     */
    static final int LIGHT_ON_AT = 6, LIGHT_OFF_AT = 9;

    static boolean wantsLight(boolean lit, int brightness) {
        return lit ? brightness < LIGHT_OFF_AT : brightness <= LIGHT_ON_AT;
    }

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

    /** Ticks a sound / report must have been known before a soldier goes to look, how close counts as having looked. */
    static final long INVESTIGATE_AFTER = 30;
    static final double INVESTIGATE_MIN = 6, INVESTIGATE_RANGE = 64, INVESTIGATE_LEASH = 30;

    /**
     * Go and look at a contact known only by sound or an ally's report: soon after first hearing it (the shooting need
     * not stop), healthy and loaded, not pinned, the squad allows a move, and not too far from the post. Close enough
     * to have looked is the end of it.
     */
    static boolean shouldInvestigate(long now, long knownSince, double distance, float suppression, boolean ammoOk,
                                     boolean mayAdvance, double fromAnchor, int holdRadius) {
        return mayAdvance && ammoOk && suppression < 0.3f && now - knownSince >= INVESTIGATE_AFTER
                && distance >= INVESTIGATE_MIN && distance <= INVESTIGATE_RANGE
                && fromAnchor <= holdRadius + INVESTIGATE_LEASH;
    }

    /** Push on a contact that has not shown itself for a while, when healthy, loaded, and the squad allows a move. */
    static boolean shouldPush(long now, long lastSeen, double distance, float suppression, boolean ammoOk,
                              boolean mayAdvance, double fromAnchor, int holdRadius) {
        return mayAdvance && ammoOk && suppression < 0.3f && now - lastSeen >= PUSH_AFTER
                && distance <= PUSH_RANGE && fromAnchor <= holdRadius + PUSH_LEASH;
    }
}
