package com.wf.wflib.contract.difficulty;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Defaults for how the world's difficulty climbs; a world can override each with {@code /contract difficulty ...}. */
public final class DifficultyConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> MODE;
    public static final ModConfigSpec.DoubleValue START, PER_DAY, MAX, PLAYER_WEIGHT, WORLDGEN_DENSITY, REWARD_SCALE;
    public static final ModConfigSpec.IntValue STEP_DAYS, DAILY_OFFERS;

    private DifficultyConfig() {
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("progression");
        MODE = b.comment("How the world tier climbs with the in-game day: flat, linear, stepped, exponential.").define("mode", "linear");
        START = b.comment("Tier on day one (0 to 10).").defineInRange("start", 0.5, 0.0, 10.0);
        PER_DAY = b.comment("Linear and stepped: tiers gained per day. Exponential: growth per day (0.03 = 3%).").defineInRange("perDay", 0.08, 0.0, 10.0);
        MAX = b.comment("The tier never goes above this (1 to 10).").defineInRange("max", 8.0, 0.1, 10.0);
        STEP_DAYS = b.comment("Stepped: days between steps.").defineInRange("stepDays", 7, 1, 365);
        PLAYER_WEIGHT = b.comment("How much an operator's standing adds to the world tier for their own contracts, 0 (none) to 1 (a top operator adds the full max).")
                .defineInRange("playerWeight", 0.25, 0.0, 1.0);
        b.pop();
        b.push("world");
        WORLDGEN_DENSITY = b.comment("Multiplier on how many soldiers and sites anything generated with the world gets (read through Difficulty.worldgenDensity).")
                .defineInRange("worldgenDensity", 1.0, 0.0, 5.0);
        REWARD_SCALE = b.comment("Multiplier on every contract's pay.").defineInRange("rewardScale", 1.0, 0.1, 10.0);
        DAILY_OFFERS = b.comment("Randomised contracts offered each in-game day.").defineInRange("dailyOffers", 6, 0, 24);
        b.pop();
        SPEC = b.build();
    }
}
