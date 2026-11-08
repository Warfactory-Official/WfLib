package com.wf.wflib.soldier;

import net.minecraft.world.phys.Vec3;

/** Acquisition uses a view cone; remembered or reported contacts are locations, never live targets. */
final class BotSenses {
    private BotSenses() {}

    static boolean inView(Vec3 eye, Vec3 look, Vec3 at) {
        Vec3 delta = at.subtract(eye);
        return delta.lengthSqr() < 0.25 || look.dot(delta.normalize()) >= Math.cos(Math.toRadians(70));
    }

    static boolean hears(Vec3 ear, Vec3 shot, boolean clear) {
        double range = clear ? 64 : 20;
        return ear.distanceToSqr(shot) <= range * range;
    }

    /** Someone this close is noticed at once, however still and quiet they are. */
    static final double ALWAYS_SEEN = 4;

    /**
     * How much a sighting adds to a soldier's awareness of someone (1 = noticed). Like the X-Ray stalkers' visibility
     * value, it builds over time instead of switching on: faster when they are close, moving or in the open, slower when
     * they are far, crouched or the soldier is under fire. A glimpse that ends early is not noticed, and a second look
     * from the same place finds them already half-noticed (see {@link #fade}).
     */
    static float detectionGain(double distance, double range, double speedPerTick, boolean crouching, float suppression) {
        return detectionGain(distance, range, speedPerTick, crouching, suppression, 1f);
    }

    /** {@code exposure}: see {@link #exposure}; it scales how fast a person is noticed. */
    static float detectionGain(double distance, double range, double speedPerTick, boolean crouching, float suppression,
                               float exposure) {
        if (distance <= ALWAYS_SEEN) return 1f;
        double near = Math.max(0.15, 1 - (distance - ALWAYS_SEEN) / Math.max(1, range - ALWAYS_SEEN));
        double motion = 1 + 1.5 * Math.min(1, speedPerTick / 0.2);
        double gain = 0.14 * near * motion * (crouching ? 0.7 : 1) * (1 - 0.5 * suppression) * exposure;
        return (float) gain;
    }

    /** Light level at a person (0-15) below which they are harder to see, and what a lit lamp does for them. */
    static final float DARK_FLOOR = 0.4F, LAMP = 2.5F;

    /**
     * How visible someone is to whoever looks at them: in full light 1; in the dark down to {@link #DARK_FLOOR}; a lamp
     * switched on makes them stand out at any distance whatever the dark.
     */
    static float exposure(int brightness, boolean lamp) {
        return lamp ? LAMP : DARK_FLOOR + (1 - DARK_FLOOR) * Math.min(15, Math.max(0, brightness)) / 15f;
    }

    /** Awareness lost per tick while the person is out of sight; slow, so a returning peek is noticed quickly. */
    static final float FADE = 0.012f;

    static float scan(float baseYaw, long tick, int seed) {
        return baseYaw + (float) Math.sin((tick + Math.floorMod(seed, 200)) * 0.018) * 75;
    }
}
