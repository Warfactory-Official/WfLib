package com.wf.wflib.photo;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-photo.toml}. */
public final class PhotoConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_PER_PLAYER;
    public static final ModConfigSpec.IntValue COOLDOWN_TICKS;
    public static final ModConfigSpec.ConfigValue<String> FILM_ITEM;
    public static final ModConfigSpec.ConfigValue<String> PRINT_ITEM;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        MAX_PER_PLAYER = b.comment("Photographs a player may keep in their gallery. At the limit the camera refuses to fire.")
                .defineInRange("maxPerPlayer", 100, 1, 500);
        COOLDOWN_TICKS = b.comment("Ticks between shots.").defineInRange("cooldownTicks", 40, 0, 1200);
        FILM_ITEM = b.comment("Item id consumed per shot, e.g. minecraft:paper. Empty = free. Creative players never pay.")
                .define("filmItem", "");
        PRINT_ITEM = b.comment("Item id consumed to print a photograph item. Empty = free.")
                .define("printItem", "minecraft:paper");
        SPEC = b.build();
    }

    private PhotoConfig() {
    }
}
