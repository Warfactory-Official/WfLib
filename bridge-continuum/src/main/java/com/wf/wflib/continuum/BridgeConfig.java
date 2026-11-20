package com.wf.wflib.continuum;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib_continuum.toml}: one switch per bridge. Read at start-up. */
final class BridgeConfig {
    private static final ModConfigSpec.BooleanValue ARMOUR;
    static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ARMOUR = b.comment("WFLib's armour system reduces damage for Continuum gear (with values seeded from Continuum's own and tunable by datapack).",
                "Off leaves Continuum's own reduction in charge; with both on, a hit would be reduced twice, so this is all or nothing.")
                .define("armour", true);
        SPEC = b.build();
    }

    private BridgeConfig() {
    }

    static boolean armour() {
        return ARMOUR.get();
    }
}
