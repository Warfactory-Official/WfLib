package com.wf.wflib.labs;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class LabsConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue THREADS;
    private static final ModConfigSpec.IntValue SAMPLES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ARMOR_EXCLUDE;
    private static volatile Snapshot snapshot = new Snapshot(0, 128, List.of("gtceu:*"));

    public record Snapshot(int threads, int samples, List<String> armorExclude) {
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("On/off: wflib-systems.toml, key 'labs'.");
        THREADS = b.comment("Worker threads of a run; 0 = cores - 1.").defineInRange("threads", 0, 0, 256);
        SAMPLES = b.comment("Seeded samples per dice-rolled grid cell (ricochet, fragment rolls).")
                .defineInRange("samples", 128, 1, 1 << 20);
        ARMOR_EXCLUDE = b.comment("Garment / insert id globs kept out of every armour set the labs enumerate.")
                .defineListAllowEmpty("armorExclude", List.of("gtceu:*"), () -> "gtceu:*", o -> o instanceof String);
        SPEC = b.build();
    }

    private LabsConfig() {
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            snapshot = new Snapshot(THREADS.get(), SAMPLES.get(), List.copyOf(ARMOR_EXCLUDE.get()));
        }
    }
}
