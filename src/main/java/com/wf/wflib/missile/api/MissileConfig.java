package com.wf.wflib.missile.api;

import com.wf.wflib.missile.debug.MissileDebug;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Objects;

/** {@code wflib-missile.toml}: immutable snapshot, rebuilt on every load. */
public final class MissileConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue INTERCEPT_CHANCE;
    private static final ModConfigSpec.DoubleValue INTERCEPT_CROSSING_FACTOR;
    private static final ModConfigSpec.DoubleValue INTERCEPTOR_KILL_RADIUS;
    private static final ModConfigSpec.DoubleValue INTERCEPTOR_ACQUIRE_RANGE;
    private static final ModConfigSpec.DoubleValue SUPERSONIC_SPEED;
    private static final ModConfigSpec.BooleanValue INTERCEPTOR_CHIP_MODE;
    private static final ModConfigSpec.DoubleValue INTERCEPTOR_HIT_DAMAGE;
    private static final ModConfigSpec.DoubleValue INTERCEPTOR_GRAZE_DAMAGE;
    private static final ModConfigSpec.DoubleValue MIN_PROJECTILE_DAMAGE;
    private static final ModConfigSpec.DoubleValue STEALTH_RCS;
    private static final ModConfigSpec.DoubleValue DIVE_EVASION_MULTIPLIER;
    private static final ModConfigSpec.IntValue BATTERY_MAGAZINE;
    private static final ModConfigSpec.IntValue BATTERY_RELOAD_TICKS;
    private static final ModConfigSpec.IntValue DEFAULT_FUEL_TICKS;
    private static final ModConfigSpec.DoubleValue SIM_TERMINAL_RANGE;
    private static final ModConfigSpec.DoubleValue SIM_SPAWN_MARGIN;
    private static final ModConfigSpec.IntValue SIM_CRUISE_DELAY_TICKS;
    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    private record Snapshot(float defaultInterceptChance, float interceptorCrossingHitFactor,
                            double interceptorKillRadius, double interceptorAcquireRange, double supersonicSpeed,
                            boolean interceptorChipMode, float interceptorHitDamage, float interceptorGrazeDamage,
                            float minProjectileDamage, float stealthRcs, double diveEvasionMultiplier,
                            int batteryMagazine, int batteryReloadTicks, int defaultFuelTicks,
                            double destinationRange, double listenerSpawnMargin, int cruiseSimDelayTicks) {
    }

    private static volatile Snapshot snapshot = new Snapshot(0.90f, 0.35f, 6.0, 200.0, 2.5, false, 60.0f, 8.0f,
            20.0f, 0.00067f, 1.5, 0, 200, 1200, 1000.0, 16.0, 100);
    private static volatile MissileSimConfig.InterceptResolution interceptMode =
            MissileSimConfig.InterceptResolution.IN_WORLD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Interceptor combat tuning.").push("interception");
        INTERCEPT_CHANCE = b
                .comment("Default per-interceptor kill probability on a proper (timed) intercept.")
                .defineInRange("killChance", 0.90, 0.0, 1.0);
        INTERCEPT_CROSSING_FACTOR = b
                .comment("Multiplier applied to the kill chance for a 'crossing' shot (interceptor too slow to",
                        "run the target down, only able to cross its path).")
                .defineInRange("crossingShotFactor", 0.35, 0.0, 1.0);
        INTERCEPTOR_KILL_RADIUS = b
                .comment("Separation (blocks) at which the interceptor's closest-approach test rolls for a kill.")
                .defineInRange("killRadius", 6.0, 0.5, 64.0);
        INTERCEPTOR_ACQUIRE_RANGE = b
                .comment("How far a NEAREST-mode interceptor scans for a hostile missile each tick (blocks).")
                .defineInRange("acquireRange", 200.0, 16.0, 1024.0);
        SUPERSONIC_SPEED = b
                .comment("cruiseSpeed (blocks/tick) at or above which a missile is classed 'supersonic'.")
                .defineInRange("supersonicSpeed", 2.5, 0.5, 64.0);
        INTERCEPTOR_CHIP_MODE = b
                .comment("If true, a proximity intercept damages the target's health pool (shared with CIWS)",
                        "instead of a binary destroy/miss: interceptors + CIWS combine and missile toughness",
                        "(health) matters. If false, a successful roll destroys the target outright.")
                .define("chipMode", false);
        INTERCEPTOR_HIT_DAMAGE = b
                .comment("Chip mode: health damage a successful intercept deals.")
                .defineInRange("hitDamage", 60.0, 0.0, 100000.0);
        INTERCEPTOR_GRAZE_DAMAGE = b
                .comment("Chip mode: health damage a missed intercept deals.")
                .defineInRange("grazeDamage", 8.0, 0.0, 100000.0);
        b.pop();

        b.comment("Missile toughness against incidental damage.").push("durability");
        MIN_PROJECTILE_DAMAGE = b
                .comment("Projectile hits weaker than this bounce off a missile instead of damaging it, so an",
                        "archer cannot plink one out of the sky and bypass interceptors and CIWS entirely.",
                        "Purpose-built anti-air hits far harder and is unaffected. 0 restores vanilla damage.")
                .defineInRange("minProjectileDamage", 20.0, 0.0, 100000.0);
        b.pop();

        b.comment("Stealth missiles: reduced-observability, not invisible.").push("stealth");
        STEALTH_RCS = b
                .comment("Radar cross-section of a stealth missile, against a reference of 1.0.",
                        "Detection range scales with the fourth root of this, so halving the range a missile",
                        "is seen at costs a sixteenfold reduction. The default gives about 32 blocks against",
                        "a 200-block interceptor battery, matching the fixed window this replaced.")
                .defineInRange("rcs", 0.00067, 0.0, 1.0);
        b.pop();

        b.comment("Evasion: higher-tier missiles shrug off interception more often.").push("evasion");
        DIVE_EVASION_MULTIPLIER = b
                .comment("Multiplier on a missile's evasion during its terminal dive (hardest to hit there).")
                .defineInRange("diveMultiplier", 1.5, 0.0, 10.0);
        b.pop();

        b.comment("Interceptor batteries.").push("batteries");
        BATTERY_MAGAZINE = b
                .comment("Interceptors a battery can fire before it must reload; 0 = unlimited (no logistics).")
                .defineInRange("magazine", 0, 0, 100000);
        BATTERY_RELOAD_TICKS = b
                .comment("Ticks a battery takes to regenerate one interceptor toward its magazine.")
                .defineInRange("reloadTicks", 200, 1, 1_000_000);
        b.pop();

        b.comment("Fuel.").push("fuel");
        DEFAULT_FUEL_TICKS = b
                .comment("Default ticks of powered flight for a missile with no explicit fuel configured.")
                .defineInRange("defaultFuelTicks", 1200, 1, 1_000_000);
        b.pop();

        b.comment("Off-world missile simulation: when a cruising missile is swapped between a real, ticking,",
                        "rendered entity and a lightweight off-world sim. Larger ranges keep missiles real (and",
                        "visible) farther out at the cost of more ticking + chunk-loading; smaller ranges favour",
                        "performance.")
                .push("simulation");
        SIM_TERMINAL_RANGE = b
                .comment("Horizontal distance (blocks) to its target at which a simulated missile respawns for its",
                        "terminal run, independent of any nearby player.")
                .defineInRange("terminalRange", 1000.0, 0.0, 100000.0);
        SIM_SPAWN_MARGIN = b
                .comment("Missiles rematerialize this many blocks before crossing a keep-range boundary, so they are",
                        "already live by the time they enter view (no spawn pop-in).")
                .defineInRange("spawnMargin", 16.0, 0.0, 512.0);
        SIM_CRUISE_DELAY_TICKS = b
                .comment("Ticks a missile must cruise before it may offload to the sim at all (20 ticks = 1s).")
                .defineInRange("cruiseDelayTicks", 100, 0, 1_000_000);
        b.pop();

        b.comment("Debugging.").push("debug");
        DEBUG_LOGGING = b
                .comment("Log detailed per-missile flight telemetry to the server log and auto-track new missiles.",
                        "Toggle at runtime with /wflib debug on|off; list off-world tracks with simlist.")
                .define("missileLogging", false);
        b.pop();

        SPEC = b.build();
    }

    private MissileConfig() {
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        snapshot = new Snapshot(INTERCEPT_CHANCE.get().floatValue(), INTERCEPT_CROSSING_FACTOR.get().floatValue(),
                INTERCEPTOR_KILL_RADIUS.get(), INTERCEPTOR_ACQUIRE_RANGE.get(), SUPERSONIC_SPEED.get(),
                INTERCEPTOR_CHIP_MODE.get(), INTERCEPTOR_HIT_DAMAGE.get().floatValue(),
                INTERCEPTOR_GRAZE_DAMAGE.get().floatValue(), MIN_PROJECTILE_DAMAGE.get().floatValue(),
                STEALTH_RCS.get().floatValue(), DIVE_EVASION_MULTIPLIER.get(), BATTERY_MAGAZINE.get(),
                BATTERY_RELOAD_TICKS.get(), DEFAULT_FUEL_TICKS.get(), SIM_TERMINAL_RANGE.get(),
                SIM_SPAWN_MARGIN.get(), SIM_CRUISE_DELAY_TICKS.get());
        MissileDebug.configureDefault(DEBUG_LOGGING.get());
    }

    public static float defaultInterceptChance() {
        return snapshot.defaultInterceptChance;
    }

    public static float interceptorCrossingHitFactor() {
        return snapshot.interceptorCrossingHitFactor;
    }

    public static double interceptorKillRadius() {
        return snapshot.interceptorKillRadius;
    }

    public static double interceptorAcquireRange() {
        return snapshot.interceptorAcquireRange;
    }

    public static double supersonicSpeed() {
        return snapshot.supersonicSpeed;
    }

    public static boolean interceptorChipMode() {
        return snapshot.interceptorChipMode;
    }

    public static float interceptorHitDamage() {
        return snapshot.interceptorHitDamage;
    }

    public static float interceptorGrazeDamage() {
        return snapshot.interceptorGrazeDamage;
    }

    public static float minProjectileDamage() {
        return snapshot.minProjectileDamage;
    }

    public static float stealthRcs() {
        return snapshot.stealthRcs;
    }

    public static double diveEvasionMultiplier() {
        return snapshot.diveEvasionMultiplier;
    }

    public static int batteryMagazine() {
        return snapshot.batteryMagazine;
    }

    public static int batteryReloadTicks() {
        return snapshot.batteryReloadTicks;
    }

    public static int defaultFuelTicks() {
        return snapshot.defaultFuelTicks;
    }

    public static double destinationRange() {
        return snapshot.destinationRange;
    }

    public static double listenerSpawnMargin() {
        return snapshot.listenerSpawnMargin;
    }

    public static int cruiseSimDelayTicks() {
        return snapshot.cruiseSimDelayTicks;
    }

    /** Runtime override ({@code /wflib interceptmode}); not a config value. */
    public static MissileSimConfig.InterceptResolution interceptMode() {
        return interceptMode;
    }

    public static void setInterceptMode(MissileSimConfig.InterceptResolution mode) {
        interceptMode = Objects.requireNonNull(mode);
    }
}
