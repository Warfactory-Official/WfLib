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
        if (distance <= ALWAYS_SEEN) return 1f;
        double near = Math.max(0.15, 1 - (distance - ALWAYS_SEEN) / Math.max(1, range - ALWAYS_SEEN));
        double motion = 1 + 1.5 * Math.min(1, speedPerTick / 0.2);
        double gain = 0.14 * near * motion * (crouching ? 0.7 : 1) * (1 - 0.5 * suppression);
        return (float) gain;
    }

    /** Awareness lost per tick while the person is out of sight; slow, so a returning peek is noticed quickly. */
    static final float FADE = 0.012f;

    static float scan(float baseYaw, long tick, int seed) {
        return baseYaw + (float) Math.sin((tick + Math.floorMod(seed, 200)) * 0.018) * 75;
    }
}
