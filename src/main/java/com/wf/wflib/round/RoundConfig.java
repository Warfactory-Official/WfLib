package com.wf.wflib.round;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-round.toml}: immutable snapshot, rebuilt on every load. */
public final class RoundConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue REWIND_WHOLE_FLIGHT;
    private static final ModConfigSpec.DoubleValue NEAR_MISS_RADIUS;
    private static final ModConfigSpec.DoubleValue UNLOADED_PRELOAD_BLAST;
    private static final ModConfigSpec.BooleanValue BLOCK_DAMAGE;
    private static final ModConfigSpec.DoubleValue BLOCK_TOUGHNESS_SCALE;

    public record Snapshot(boolean rewindWholeFlight, double nearMissRadius, float preloadBlast, boolean blockDamage,
                           float blockToughnessScale) {
        public Snapshot withRewindWholeFlight(boolean v) {
            return new Snapshot(v, nearMissRadius, preloadBlast, blockDamage, blockToughnessScale);
        }

        public Snapshot withBlockToughnessScale(float v) {
            return new Snapshot(rewindWholeFlight, nearMissRadius, preloadBlast, blockDamage, v);
        }
    }

    private static volatile Snapshot snapshot = new Snapshot(true, 3.0, 4.0f, true, 1.0f);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Rounds.").push("rounds");
        REWIND_WHOLE_FLIGHT = b
                .comment("Lag compensation: true = every step of a round sweeps targets as its shooter saw them;",
                        "false = first step only, later steps sweep live targets.")
                .define("rewindWholeFlight", true);
        NEAR_MISS_RADIUS = b
                .comment("A round passing within this many blocks of a player's eyes without hitting posts a",
                        "RoundNearMissEvent (suppression). 0 disables.")
                .defineInRange("nearMissRadius", 3.0, 0.0, 64.0);
        UNLOADED_PRELOAD_BLAST = b
                .comment("A round hitting unloaded ground with at least this blast size loads the blast's chunks",
                        "(async) and waits for them; smaller ones show their blast at once and apply it to the",
                        "world when the chunk next loads.")
                .defineInRange("unloadedPreloadBlast", 4.0, 0.0, 1000.0);
        BLOCK_DAMAGE = b
                .comment("Rounds crack and break blocks by the momentum they lose in them (no drops).")
                .define("blockDamage", true);
        BLOCK_TOUGHNESS_SCALE = b
                .comment("x every block's toughness: 2 = twice the hits.")
                .defineInRange("blockToughnessScale", 1.0, 0.01, 1000.0);
        b.pop();
        SPEC = b.build();
    }

    private RoundConfig() {
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        snapshot = new Snapshot(REWIND_WHOLE_FLIGHT.get(), NEAR_MISS_RADIUS.get(),
                UNLOADED_PRELOAD_BLAST.get().floatValue(), BLOCK_DAMAGE.get(),
                BLOCK_TOUGHNESS_SCALE.get().floatValue());
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    /** Test seam: replaces the snapshot until the next config load. */
    public static void override(Snapshot replacement) {
        snapshot = replacement;
    }
}
