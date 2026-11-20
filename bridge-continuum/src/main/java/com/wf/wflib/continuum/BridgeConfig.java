package com.wf.wflib.continuum;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib_continuum.toml}: one switch per bridge. Read at start-up. */
final class BridgeConfig {
    private static final ModConfigSpec.BooleanValue MASTER, ARMOUR, DOORS, GLYPHIDS, RADIATION, NUKES;
    static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        MASTER = b.comment("Master switch: off turns every bridge below off (the choices are kept).").define("enabled", true);
        ARMOUR = b.comment("WFLib's armour system reduces damage for Continuum gear (with values seeded from Continuum's own and tunable by datapack).",
                "Off leaves Continuum's own reduction in charge; with both on, a hit would be reduced twice, so this is all or nothing.")
                .define("armour", true);
        DOORS = b.comment("New doors are WFLib's: Continuum's assembly recipes for its fifteen doors produce the wflib door of the same name.",
                "Doors already placed, and Continuum's door items, are untouched.").define("doors", true);
        GLYPHIDS = b.comment("WFLib's colonies replace Continuum's glyphids: Continuum's natural spawns and hives are switched off, and WFLib glyphids drop Continuum's meat.")
                .define("glyphids", true);
        RADIATION = b.comment("Continuum's radiation field is the one WFLib reads and feeds; players take WFLib's dose (shielding, armour wear, hazard rows), not Continuum's.",
                "Needs WFLib's hazard and radiation systems on. Hazard rows for Continuum items are generated with it.").define("radiation", true);
        NUKES = b.comment("Continuum's nuclear blasts run WFLib's parallel crater ray. On because the parity tests (BridgeGameTests.mk5RaysAgreeInStone and InDirt) show the two craters within 10% of each other.")
                .define("nukes", true);
        SPEC = b.build();
    }

    private BridgeConfig() {
    }

    static ModConfigSpec.BooleanValue value(String id) {
        return switch (id) {
            case "armour" -> ARMOUR;
            case "doors" -> DOORS;
            case "glyphids" -> GLYPHIDS;
            case "radiation" -> RADIATION;
            case "nukes" -> NUKES;
            default -> throw new IllegalArgumentException(id);
        };
    }

    static ModConfigSpec.BooleanValue master() {
        return MASTER;
    }

    static boolean nukes() {
        return MASTER.get() && NUKES.get();
    }

    static boolean radiation() {
        return MASTER.get() && RADIATION.get();
    }

    static boolean glyphids() {
        return MASTER.get() && GLYPHIDS.get();
    }

    static boolean doors() {
        return MASTER.get() && DOORS.get();
    }

    static boolean armour() {
        return MASTER.get() && ARMOUR.get();
    }
}
