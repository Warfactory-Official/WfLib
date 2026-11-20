package com.wf.wflib.continuum;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib_continuum.toml}: one switch per bridge. Read at start-up. */
final class BridgeConfig {
    private static final ModConfigSpec.BooleanValue ARMOUR, DOORS, GLYPHIDS;
    static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ARMOUR = b.comment("WFLib's armour system reduces damage for Continuum gear (with values seeded from Continuum's own and tunable by datapack).",
                "Off leaves Continuum's own reduction in charge; with both on, a hit would be reduced twice, so this is all or nothing.")
                .define("armour", true);
        DOORS = b.comment("New doors are WFLib's: Continuum's assembly recipes for its fifteen doors produce the wflib door of the same name.",
                "Doors already placed, and Continuum's door items, are untouched.").define("doors", true);
        GLYPHIDS = b.comment("WFLib's colonies replace Continuum's glyphids: Continuum's natural spawns and hives are switched off, and WFLib glyphids drop Continuum's meat.")
                .define("glyphids", true);
        SPEC = b.build();
    }

    private BridgeConfig() {
    }

    static boolean glyphids() {
        return GLYPHIDS.get();
    }

    static boolean doors() {
        return DOORS.get();
    }

    static boolean armour() {
        return ARMOUR.get();
    }
}
