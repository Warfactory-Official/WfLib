package com.wf.wflib.missile.client;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-missile-client.toml}. */
public final class MissileClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue SHOW_MISSILE_TARGETS;

    private static volatile boolean showMissileTargets;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Debug overlays (singleplayer diagnostics).").push("debug");
        SHOW_MISSILE_TARGETS = b
                .comment("Draw a green box at every missile's current aim point, with a line back to the missile,",
                        "so you can see where each one (recursive missilelets especially) is actually aiming and",
                        "whether that point is on ground or floating in the air. Reads the server-side target off the",
                        "integrated server, so it only works in singleplayer. Off by default.")
                .define("showMissileTargets", false);
        b.pop();
        SPEC = b.build();
    }

    private MissileClientConfig() {
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            showMissileTargets = SHOW_MISSILE_TARGETS.get();
        }
    }

    public static boolean showMissileTargets() {
        return showMissileTargets;
    }
}
