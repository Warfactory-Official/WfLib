package com.wf.wflib.corpse;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CorpseConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ONLY_OWNER;
    public static final ModConfigSpec.IntValue EMPTY_DESPAWN_TICKS;
    public static final ModConfigSpec.IntValue FORCE_DESPAWN_TICKS;

    private CorpseConfig() {
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ONLY_OWNER = b.comment("Only the owner (and operators) may open a corpse.")
                .define("onlyOwner", false);
        EMPTY_DESPAWN_TICKS = b.comment("An empty corpse despawns after this many ticks.")
                .defineInRange("emptyDespawnTicks", 20 * 30, 1, Integer.MAX_VALUE);
        FORCE_DESPAWN_TICKS = b.comment("Any corpse despawns after this many ticks, dropping what it holds; -1 = never.")
                .defineInRange("forceDespawnTicks", -1, -1, Integer.MAX_VALUE);
        SPEC = b.build();
    }
}
