package com.wf.wflib.warhead;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class WarheadConfig {
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue FIRE_BOMBLETS_AS_FIREBALLS;

    private record Snapshot(boolean fireBombletsAsFireballs) {
    }

    private static volatile Snapshot snapshot = new Snapshot(false);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        FIRE_BOMBLETS_AS_FIREBALLS = b
                .comment("If true, the fire cluster warhead scatters flying fireballs instead of fire bomblets.")
                .define("fireBombletsAsFireballs", false);
        SPEC = b.build();
    }

    private WarheadConfig() {
    }

    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            snapshot = new Snapshot(FIRE_BOMBLETS_AS_FIREBALLS.get());
        }
    }

    public static boolean fireBombletsAsFireballs() {
        return snapshot.fireBombletsAsFireballs;
    }
}
