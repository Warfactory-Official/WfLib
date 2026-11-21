package com.wf.wflib.continuum;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib_continuum.toml}: one switch per bridge. Read at start-up. */
final class BridgeConfig {
    private static final ModConfigSpec.BooleanValue MASTER, ARMOUR, DOORS, GLYPHIDS, RADIATION, NUKES, MASSES, PROBE, WIKI, SOLDIERS, WARHEADS, BULLETS, PENETRATION, RECON, ORBIT, EXPLOSIONFX, PDA, EXPLOSIVES;
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
        NUKES = b.comment("Continuum's nuclear blasts run WFLib's parallel crater ray. Off by default: in dirt the two craters agree within 10% (BridgeGameTests.mk5RaysAgreeInDirt), in stone they do not and Continuum's own result varies from run to run (mk5RaysAgreeInStone, optional).")
                .define("nukes", false);
        MASSES = b.comment("Masses for Continuum's material items (nugget to block) from their material and its density, for Ultimate Weight. Needs wfweight.")
                .define("masses", true);
        PROBE = b.comment("Continuum's look-at overlays (its blocks' and entities' readouts) show in WFLib's probe panel; Continuum's own crosshair overlay is switched off.")
                .define("probe", true);
        WIKI = b.comment("Continuum's manual pages are written as WFLib wiki articles, bound to the items that trigger them, for the PDA's Wiki app (client).")
                .define("wiki", true);
        SOLDIERS = b.comment("WFLib soldiers can be issued and fire Continuum's magazine-fed guns, beside whichever gun mod owns their weapon slot.")
                .define("soldiers", true);
        WARHEADS = b.comment("Continuum's blasts are registered as WFLib warheads (wflib_continuum:he, nuclear, nuclear_small) that WFLib missiles, rounds and drones can carry.")
                .define("warheads", true);
        PENETRATION = b.comment("Continuum's bullets carry a WFLib threat (bore, penetration in mm of steel) to armour, plates and hulls.")
                .define("penetration", true);
        BULLETS = b.comment("Plain Continuum bullets are flown as WFLib rounds (lag compensation, terrain, tracers, ricochet); loads with their own behaviour stay Continuum's.")
                .define("bullets", true);
        RECON = b.comment("Continuum's missiles and rockets are contacts on WFLib's radar and scopes; its explosions are heard by seismic sensors.")
                .define("recon", true);
        ORBIT = b.comment("Continuum's satellites are mirrored into WFLib's orbital catalogue (the PDA satellites app, scopes, hunters).")
                .define("orbit", true);
        EXPLOSIONFX = b.comment("EXPERIMENTAL. Continuum's small, standard and large explosion effects are drawn by WFLib's GemRender effects (shockwave, cloud, thrown ground) instead of Continuum's particles.")
                .define("explosionfx", true);
        PDA = b.comment("Continuum's missile launches and impacts, large explosions, satellites and radiation doses are alerts on a carried WFLib PDA.")
                .define("pda", true);
        EXPLOSIVES = b.comment("Continuum's landmines and charges can be wired to a WFLib detonator; its landmines show in the probe with a defuse row.")
                .define("explosives", true);
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
            case "masses" -> MASSES;
            case "probe" -> PROBE;
            case "wiki" -> WIKI;
            case "soldiers" -> SOLDIERS;
            case "warheads" -> WARHEADS;
            case "penetration" -> PENETRATION;
            case "bullets" -> BULLETS;
            case "recon" -> RECON;
            case "orbit" -> ORBIT;
            case "explosionfx" -> EXPLOSIONFX;
            case "pda" -> PDA;
            case "explosives" -> EXPLOSIVES;
            default -> throw new IllegalArgumentException(id);
        };
    }

    static ModConfigSpec.BooleanValue master() {
        return MASTER;
    }

    static boolean penetration() {
        return MASTER.get() && PENETRATION.get();
    }

    static boolean explosives() {
        return MASTER.get() && EXPLOSIVES.get();
    }

    static boolean pda() {
        return MASTER.get() && PDA.get();
    }

    static boolean explosionFx() {
        return MASTER.get() && EXPLOSIONFX.get();
    }

    static boolean recon() {
        return MASTER.get() && RECON.get();
    }

    static boolean orbit() {
        return MASTER.get() && ORBIT.get();
    }

    static boolean bullets() {
        return MASTER.get() && BULLETS.get();
    }

    static boolean warheads() {
        return MASTER.get() && WARHEADS.get();
    }

    static boolean soldiers() {
        return MASTER.get() && SOLDIERS.get();
    }

    static boolean wiki() {
        return MASTER.get() && WIKI.get();
    }

    static boolean probe() {
        return MASTER.get() && PROBE.get();
    }

    static boolean masses() {
        return MASTER.get() && MASSES.get();
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
