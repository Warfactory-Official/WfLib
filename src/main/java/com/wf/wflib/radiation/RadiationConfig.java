package com.wf.wflib.radiation;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Radiation + item hazard tunables; static fields so gametests can set them. Sim constants consumed at server start. */
public final class RadiationConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLE_CHUNK_RADS_VALUE;
    private static final ModConfigSpec.BooleanValue ENABLE_CONTAMINATION_VALUE;
    private static final ModConfigSpec.BooleanValue ENABLE_CONTAMINATION_ON_GROUND_VALUE;
    private static final ModConfigSpec.BooleanValue WORLD_RAD_EFFECTS_VALUE;
    private static final ModConfigSpec.IntValue RAD_TICK_RATE_VALUE;
    private static final ModConfigSpec.DoubleValue RAD_DIFFUSIVITY_VALUE;
    private static final ModConfigSpec.DoubleValue RAD_HALF_LIFE_VALUE;
    private static final ModConfigSpec.DoubleValue FOG_RAD_VALUE;
    private static final ModConfigSpec.DoubleValue FOG_CH_VALUE;
    private static final ModConfigSpec.BooleanValue NEUTRON_ACTIVATION_VALUE;
    private static final ModConfigSpec.IntValue NEUTRON_ACTIVATION_THRESHOLD_VALUE;
    private static final ModConfigSpec.IntValue HAZARD_RATE_VALUE;
    private static final ModConfigSpec.IntValue ITEM_HAZARD_DROP_TICKRATE_VALUE;
    private static final ModConfigSpec.DoubleValue ARMOR_WEAR_PER_RAD_VALUE;
    private static final ModConfigSpec.DoubleValue FALLOUT_RAD_VALUE;
    private static final ModConfigSpec.DoubleValue FALLOUT_HALF_LIFE_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_ASBESTOS_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_COAL_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_HOT_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_COLD_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_EXPLOSIVE_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_HYDRO_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_BLINDING_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_TOXIC_VALUE;
    private static final ModConfigSpec.BooleanValue ENABLE_528_VALUE;
    private static final ModConfigSpec.BooleanValue DEBUG_VALUE;

    public static boolean enableChunkRads = true;
    public static boolean enableContamination = true;
    public static boolean enableContaminationOnGround = true;
    public static boolean worldRadEffects = true;
    public static int radTickRate = 1;
    public static double radDiffusivity = 10.0D;
    public static double radHalfLifeSeconds = 120.0D;
    public static double fogRad = 100.0D;
    public static double fogCh = 20.0D;
    public static boolean neutronActivation = false;
    public static int neutronActivationThreshold = 15;
    public static int hazardRate = 5;
    public static int itemHazardDropTickrate = 2;
    /** RAD/s received -> RADIATION exposure tier (condition points per armour exposure interval). */
    public static double armorWearPerRad = 20.0D;
    public static double falloutRad = 500.0D;
    public static double falloutHalfLifeSeconds = 3600.0D;
    public static boolean disableAsbestos = false;
    public static boolean disableCoal = false;
    public static boolean disableHot = false;
    public static boolean disableCold = false;
    public static boolean disableExplosive = false;
    public static boolean disableHydro = false;
    public static boolean disableBlinding = false;
    public static boolean disableToxic = false;
    public static boolean enable528 = false;
    public static boolean enableDebugMode = false;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("radiation");
        ENABLE_CHUNK_RADS_VALUE = b.comment("World (chunk) radiation field.").define("enableChunkRads", true);
        ENABLE_CONTAMINATION_VALUE = b.comment("Entity dose + radiation sickness.").define("enableContamination", true);
        ENABLE_CONTAMINATION_ON_GROUND_VALUE = b.comment("Dropped contaminating stacks seed the field.")
                .define("enableContaminationOnGround", true);
        WORLD_RAD_EFFECTS_VALUE = b.comment("High fields kill surface vegetation and spawn fog.")
                .define("worldRadEffects", true);
        RAD_TICK_RATE_VALUE = b.comment("Ticks between sweeps. Read at server start.")
                .defineInRange("radTickRate", 1, 1, Integer.MAX_VALUE);
        RAD_DIFFUSIVITY_VALUE = b.comment("Diffusion coefficient. Read at server start.")
                .defineInRange("radDiffusivity", 10.0D, 1.0E-6D, Double.MAX_VALUE);
        RAD_HALF_LIFE_VALUE = b.comment("Field half-life, seconds. Read at server start.")
                .defineInRange("radHalfLifeSeconds", 120.0D, 1.0E-6D, Double.MAX_VALUE);
        FOG_RAD_VALUE = b.comment("Field (RAD) above which fog may spawn.").defineInRange("fogRad", 100.0D, 0.0D, Double.MAX_VALUE);
        FOG_CH_VALUE = b.comment("Mean seconds between fog puffs per pocket above fogRad.")
                .defineInRange("fogChance", 20.0D, 1.0D, Double.MAX_VALUE);
        NEUTRON_ACTIVATION_VALUE = b.comment("High received dose activates carried items.")
                .define("neutronActivation", false);
        NEUTRON_ACTIVATION_THRESHOLD_VALUE = b.comment("RAD/s before activation starts.")
                .defineInRange("neutronActivationThreshold", 15, 0, Integer.MAX_VALUE);
        ARMOR_WEAR_PER_RAD_VALUE = b.comment("Armour RADIATION exposure tier per RAD/s received.")
                .defineInRange("armorWearPerRad", 20.0D, 0.0D, 1.0E7D);
        FALLOUT_RAD_VALUE = b.comment("Nuke fallout ground source at ground zero, RAD; 0 = none.")
                .defineInRange("falloutRad", 500.0D, 0.0D, 1.0E7D);
        FALLOUT_HALF_LIFE_VALUE = b.comment("Nuke fallout half-life, seconds.")
                .defineInRange("falloutHalfLifeSeconds", 3600.0D, 1.0D, 1.0E9D);
        DEBUG_VALUE = b.comment("Sweep profiling logs.").define("debug", false);
        b.pop();
        b.push("hazards");
        HAZARD_RATE_VALUE = b.comment("Ticks between item hazard applications.")
                .defineInRange("hazardRate", 5, 1, Integer.MAX_VALUE);
        ITEM_HAZARD_DROP_TICKRATE_VALUE = b.comment("Ticks between dropped-item hazard updates.")
                .defineInRange("itemHazardDropTickrate", 2, 1, Integer.MAX_VALUE);
        DISABLE_ASBESTOS_VALUE = b.define("disableAsbestos", false);
        DISABLE_COAL_VALUE = b.define("disableCoaldust", false);
        DISABLE_HOT_VALUE = b.define("disableHot", false);
        DISABLE_COLD_VALUE = b.define("disableCold", false);
        DISABLE_EXPLOSIVE_VALUE = b.define("disableExplosive", false);
        DISABLE_HYDRO_VALUE = b.define("disableHydroactive", false);
        DISABLE_BLINDING_VALUE = b.define("disableBlinding", false);
        DISABLE_TOXIC_VALUE = b.define("disableToxic", false);
        ENABLE_528_VALUE = b.comment("Reacher divides dose by 49 instead of sqrt.").define("enable528", false);
        b.pop();
        SPEC = b.build();
    }

    private RadiationConfig() {
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        enableChunkRads = ENABLE_CHUNK_RADS_VALUE.get();
        enableContamination = ENABLE_CONTAMINATION_VALUE.get();
        enableContaminationOnGround = ENABLE_CONTAMINATION_ON_GROUND_VALUE.get();
        worldRadEffects = WORLD_RAD_EFFECTS_VALUE.get();
        radTickRate = RAD_TICK_RATE_VALUE.get();
        radDiffusivity = RAD_DIFFUSIVITY_VALUE.get();
        radHalfLifeSeconds = RAD_HALF_LIFE_VALUE.get();
        fogRad = FOG_RAD_VALUE.get();
        fogCh = FOG_CH_VALUE.get();
        neutronActivation = NEUTRON_ACTIVATION_VALUE.get();
        neutronActivationThreshold = NEUTRON_ACTIVATION_THRESHOLD_VALUE.get();
        armorWearPerRad = ARMOR_WEAR_PER_RAD_VALUE.get();
        falloutRad = FALLOUT_RAD_VALUE.get();
        falloutHalfLifeSeconds = FALLOUT_HALF_LIFE_VALUE.get();
        enableDebugMode = DEBUG_VALUE.get();
        hazardRate = HAZARD_RATE_VALUE.get();
        itemHazardDropTickrate = ITEM_HAZARD_DROP_TICKRATE_VALUE.get();
        disableAsbestos = DISABLE_ASBESTOS_VALUE.get();
        disableCoal = DISABLE_COAL_VALUE.get();
        disableHot = DISABLE_HOT_VALUE.get();
        disableCold = DISABLE_COLD_VALUE.get();
        disableExplosive = DISABLE_EXPLOSIVE_VALUE.get();
        disableHydro = DISABLE_HYDRO_VALUE.get();
        disableBlinding = DISABLE_BLINDING_VALUE.get();
        disableToxic = DISABLE_TOXIC_VALUE.get();
        enable528 = ENABLE_528_VALUE.get();
    }
}
