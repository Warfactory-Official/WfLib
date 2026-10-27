package com.wf.wflib.anomaly;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Anomaly switches. A server config: per world, synced to clients, so a client pulls its own player and draws the
 * lens only when the server it is on runs anomalies.
 */
public final class AnomalyConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED_VALUE;

    /** Off (the default) => placed anomalies are inert and invisible, and not in the creative tab. */
    public static boolean enabled = false;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("STALKER-style anomalies (gravitational: the Vortex). Optional, experimental.")
                .push("anomalies");
        ENABLED_VALUE = builder
                .comment("Master switch, off by default. Off leaves placed anomalies inert and invisible.")
                .define("enabled", false);
        builder.pop();
        SPEC = builder.build();
    }

    private AnomalyConfig() {
    }

    /** Saved to the world's file (the server's, so only with a server running). */
    public static void set(boolean on) {
        ENABLED_VALUE.set(on);
        ENABLED_VALUE.save();
        enabled = on;
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        // A server config unloads with its world: nothing to read then (get() throws), and no world to run in.
        enabled = !(event instanceof ModConfigEvent.Unloading) && ENABLED_VALUE.get();
    }
}
