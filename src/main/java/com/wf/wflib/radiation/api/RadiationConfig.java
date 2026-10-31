package com.wf.wflib.radiation.api;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.UnaryOperator;

/** Radiation tunables; immutable snapshot swapped on load. {@code radTickRate}, {@code radDiffusivity}, {@code radHalfLifeSeconds}: read at server start. */
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
    private static final ModConfigSpec.DoubleValue ARMOR_WEAR_PER_RAD_VALUE;
    private static final ModConfigSpec.DoubleValue FALLOUT_RAD_VALUE;
    private static final ModConfigSpec.DoubleValue FALLOUT_HALF_LIFE_VALUE;
    private static final ModConfigSpec.BooleanValue DEBUG_VALUE;

    /**
     * @param fogChance        mean seconds between fog puffs per pocket above {@code fogRad}
     * @param armorWearPerRad  RAD/s received -> RADIATION exposure tier (condition points per armour exposure interval)
     * @param debug            sweep profiling logs
     */
    public record Values(boolean enableChunkRads, boolean enableContamination, boolean enableContaminationOnGround,
                         boolean worldRadEffects, int radTickRate, double radDiffusivity, double radHalfLifeSeconds,
                         double fogRad, double fogChance, boolean neutronActivation, int neutronActivationThreshold,
                         double armorWearPerRad, double falloutRad, double falloutHalfLifeSeconds, boolean debug) {
        public Values withNeutronActivation(boolean on) {
            return new Values(enableChunkRads, enableContamination, enableContaminationOnGround, worldRadEffects,
                    radTickRate, radDiffusivity, radHalfLifeSeconds, fogRad, fogChance, on,
                    neutronActivationThreshold, armorWearPerRad, falloutRad, falloutHalfLifeSeconds, debug);
        }
    }

    private static volatile Values values = new Values(true, true, true, true, 1, 10.0D, 120.0D, 100.0D, 20.0D,
            false, 15, 20.0D, 500.0D, 3600.0D, false);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("On/off: wflib-systems.toml, key 'radiation'.").push("radiation");
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
        SPEC = b.build();
    }

    private RadiationConfig() {
    }

    public static Values get() {
        return values;
    }

    /** Test seam. */
    public static void override(UnaryOperator<Values> change) {
        values = change.apply(values);
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        values = new Values(ENABLE_CHUNK_RADS_VALUE.get(), ENABLE_CONTAMINATION_VALUE.get(),
                ENABLE_CONTAMINATION_ON_GROUND_VALUE.get(), WORLD_RAD_EFFECTS_VALUE.get(), RAD_TICK_RATE_VALUE.get(),
                RAD_DIFFUSIVITY_VALUE.get(), RAD_HALF_LIFE_VALUE.get(), FOG_RAD_VALUE.get(), FOG_CH_VALUE.get(),
                NEUTRON_ACTIVATION_VALUE.get(), NEUTRON_ACTIVATION_THRESHOLD_VALUE.get(),
                ARMOR_WEAR_PER_RAD_VALUE.get(), FALLOUT_RAD_VALUE.get(), FALLOUT_HALF_LIFE_VALUE.get(), DEBUG_VALUE.get());
    }
}
