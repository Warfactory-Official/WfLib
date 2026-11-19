package com.wf.wflib.worldstats;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** The world-stats overlay's settings: where it is, how big, and the line templates (tags in braces, colour codes with the section sign). */
public final class StatsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOWN;
    public static final ModConfigSpec.IntValue X, Y, BACKGROUND;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.ConfigValue<String> UNITS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LINES;

    /** Tags: fps day time dimension chunks biome temp humidity entities cx cz ox oz y facing light moon storm. */
    public static final List<String> DEFAULT_LINES = List.of(
            "§eFPS: §f{fps}",
            "§aMinecraft §fDay {day}  §eTime: §f{time}",
            "§eDimension: §f{dimension}  §eChunks Loaded: §f{chunks}",
            "§eBiome: §f{biome}  {temp}  §b{humidity}% Humidity",
            "§eEntities: §f{entities}",
            "§eChunk X: §f{cx} §eZ: §f{cz} §eOff: §f{ox}, {oz}  §eY: §f{y}  §eFacing §f{facing}",
            "§eLight (Feet): §f{light}",
            "§eMoon phase: §f{moon}",
            "§b{storm}");

    private StatsConfig() {
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SHOWN = b.comment("Show the overlay (the toggle key flips this for the session).").define("shown", true);
        X = b.comment("Distance from the left edge, pixels.").defineInRange("x", 2, 0, 4000);
        Y = b.comment("Distance from the top edge, pixels.").defineInRange("y", 2, 0, 4000);
        SCALE = b.comment("Text scale.").defineInRange("scale", 0.75, 0.4, 3.0);
        BACKGROUND = b.comment("Background shade behind the text, 0-255 alpha; 0 = none.").defineInRange("background", 96, 0, 255);
        UNITS = b.comment("Temperature units shown, any of C, F, K in order.").define("units", "FCK");
        LINES = b.comment("One template per line. Tags: {fps} {day} {time} {dimension} {chunks} {biome} {temp} {humidity} {entities} {cx} {cz} {ox} {oz} {y} {facing} {light} {moon} {storm}. Other mods add tags through WorldStats.tag.")
                .defineListAllowEmpty("lines", DEFAULT_LINES, o -> o instanceof String);
        SPEC = b.build();
    }
}
