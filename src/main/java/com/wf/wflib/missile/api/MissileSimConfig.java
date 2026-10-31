package com.wf.wflib.missile.api;

/** Tunables for the missile chunk-loading + off-world simulation system. */
public final class MissileSimConfig {
    /**
     * How far ahead of the missile (toward its travel direction) to keep chunks loaded.
     */
    public static final double FAN_LOOKAHEAD = 64.0;

    // --- Stage 1: chunk-loading fan (blocks) ---
    /**
     * Lateral padding around the missile/look-ahead segment.
     */
    public static final double FAN_LATERAL = 32.0;
    /**
     * Within this horizontal distance of the target, a high-altitude missile starts force-loading its look-ahead
     * fan again so the terminal dive/impact has terrain loaded (gives lead time before the ATTACK phase).
     */
    public static final double FAN_TERMINAL_RANGE = 128.0;

    // --- Stage 2: off-world simulation ---
    /**
     * Clamp on the per-tick gametime delta so a long gap (restart/lag) can't teleport a missile.
     */
    public static final int MAX_SIM_STEP = 40;

    // --- Listeners ---
    /**
     * Default range of the debug listener block.
     */
    public static final double DEBUG_LISTENER_RANGE = 512.0;
    /**
     * Blocks between an interceptor and its target at which they are considered to collide.
     */
    public static final double INTERCEPT_DISTANCE = 24.0;

    // --- Simulated interception ---
    /**
     * Probability an intercept roll succeeds (CHANCE_ROLL mode only), when the interceptor carries no per-missile
     * chance of its own.
     */
    public static final float INTERCEPT_CHANCE = 0.5f;
    /**
     * Simulated interceptors close on their target at this speed (blocks/tick).
     */
    public static final double INTERCEPTOR_SPEED = 1.5;
    /**
     * An interceptor self-destructs (fizzles) after this many ticks aloft, so a miss can't loiter forever.
     */
    public static final int INTERCEPTOR_LIFETIME_TICKS = 600;

    // --- Real (in-world) interceptor entities ---
    /**
     * Ticks an interceptor may go without any resolvable target before it gives up and fizzles.
     */
    public static final int INTERCEPTOR_LOST_TARGET_TICKS = 60;
    /**
     * Detection radius the interceptor registers as an {@link IMissileListener} so nearby off-world missiles
     * rematerialize into real entities it can actually engage.
     */
    public static final double INTERCEPTOR_LISTENER_RANGE = 64.0;
    /** Default cruise speed for an in-world interceptor (blocks/tick). */
    public static final double INTERCEPTOR_ENTITY_SPEED = 4.0;
    /**
     * Default max heading change per tick (radians) for an in-world interceptor: nimble, near-pure pursuit.
     */
    public static final double INTERCEPTOR_TURN_RATE = 0.6;
    /**
     * Default cruise speed for a supersonic interceptor: fast enough to actually run down a supersonic missile
     * (rather than only cross its path).
     */
    public static final double INTERCEPTOR_SUPERSONIC_ENTITY_SPEED = 9.0;
    /**
     * Default max heading change per tick (radians) for a supersonic interceptor.
     */
    public static final double INTERCEPTOR_SUPERSONIC_TURN_RATE = 0.5;
    /**
     * How many ticks ahead the collision predictor integrates the two tracks (IN_WORLD mode).
     */
    public static final int PREDICT_HORIZON_TICKS = 400;
    /**
     * Trigger the in-world spawn once the predicted collision is within this many ticks.
     */
    public static final int INTERCEPT_LEAD_TICKS = 100;
    /**
     * Distance back from the predicted collision point at which each missile is spawned.
     */
    public static final double INTERCEPT_SPAWN_DISTANCE = 64.0;
    /** Max length (blocks) of one collision sub-segment. */
    public static final double COLLISION_MAX_SUBSTEP_DIST = 4.0;
    /**
     * Hard clamp on substeps/tick so a very fast or stuck missile cannot blow the ray budget.
     */
    public static final int COLLISION_MAX_SUBSTEPS = 8;
    /** Cross-section below which a missile is called "stealth" in tooltips and commands. */
    public static final float STEALTH_RCS_THRESHOLD = 0.5f;

    // --- Continuous collision (anti-tunneling) ---
    /**
     * Active collision fidelity. Left on the cheap ray by default; {@code OBB_SWEEP} is opt-in.
     */
    public static final CollisionFidelity COLLISION_FIDELITY = CollisionFidelity.CENTER_RAY;

    private MissileSimConfig() {
    }

    /**
     * How the interception between two simulated missiles is resolved.
     */
    public enum InterceptResolution {
        /**
         * Cheap: roll a chance when interceptor and target get close in simulation.
         */
        CHANCE_ROLL,
        /**
         * Realistic: predict the collision area and spawn both missiles into the loaded world near it.
         */
        IN_WORLD
    }

    /**
     * Fidelity of the swept missile-vs-block collision.
     */
    public enum CollisionFidelity {
        /**
         * Nose-extended DDA center ray (cheapest, default). Fully stops head-on tunneling.
         */
        CENTER_RAY,
        /**
         * Also samples the oriented body box per substep, catching oblique/edge clips a thin ray misses.
         */
        OBB_SWEEP
    }
}
