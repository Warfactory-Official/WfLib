package com.wf.wflib.physics;

import com.wf.wflib.physics.api.DeckSettings;
import com.wf.wflib.physics.api.PhysicsSettings;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class PhysicsConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ASYNC_STEP;
    private static final ModConfigSpec.BooleanValue NATIVE_NARROWPHASE;
    private static final ModConfigSpec.IntValue THREADS;
    private static final ModConfigSpec.BooleanValue DECK_PARENTING;
    private static final ModConfigSpec.BooleanValue CARRIER_DECKS;

    private static volatile PhysicsSettings values = PhysicsSettings.DEFAULTS;
    private static volatile DeckSettings deck = DeckSettings.DEFAULTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("On/off: wflib-systems.toml, key 'physics'.");
        ASYNC_STEP = b.comment("Islands solve on worker threads; results apply in tick order (deterministic).")
                .define("asyncStep", true);
        NATIVE_NARROWPHASE = b.comment("Body-vs-terrain contacts in the native library (bit-equal to the Java path;"
                + " platforms without it fall back).").define("nativeNarrowphase", true);
        THREADS = b.comment("Step worker threads; 0 = half the CPU cores.").defineInRange("threads", 0, 0, 64);
        DECK_PARENTING = b.comment("Entities on a body's deck ride in its frame (off: pushed out instead).")
                .define("deckParenting", true);
        CARRIER_DECKS = b.comment("Bodies rest on other bodies' landing pieces.").define("carrierDecks", true);
        SPEC = b.build();
    }

    private PhysicsConfig() {
    }

    public static PhysicsSettings get() {
        return values;
    }

    public static DeckSettings deck() {
        return deck;
    }

    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        values = new PhysicsSettings(ASYNC_STEP.get(), NATIVE_NARROWPHASE.get(), THREADS.get());
        deck = new DeckSettings(DECK_PARENTING.get(), CARRIER_DECKS.get());
    }
}
