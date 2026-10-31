package com.wf.wflib.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import com.wf.wflib.radiation.api.RadiationConfig;
import com.wf.wflib.core.api.WFLib;

/**
 * Per-dimension overrides for the radiation field's physics constants, the entry id being the dimension id.
 * Accepted ranges mirror {@code ConfigSchema}'s, so config and datapack cannot disagree about what is legal;
 * the sweep cadence is deliberately absent, scheduling every dimension off one counter.
 */
public record RadiationSettings(Optional<Double> diffusivity,
                                Optional<Double> halfLifeSeconds,
                                Optional<Double> fogRad,
                                Optional<Double> fogChance,
                                Optional<Boolean> worldRadEffects,
                                Optional<Double> ambientRad,
                                Optional<Double> minBound,
                                Optional<Boolean> diffusivityTransport) {

    public static final ResourceKey<Registry<RadiationSettings>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "radiation_settings"));

    public static final RadiationSettings DEFAULT = new RadiationSettings(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<RadiationSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(1.0e-6D, Double.MAX_VALUE).optionalFieldOf("diffusivity")
                    .forGetter(RadiationSettings::diffusivity),
            Codec.doubleRange(1.0e-6D, Double.MAX_VALUE).optionalFieldOf("half_life_seconds")
                    .forGetter(RadiationSettings::halfLifeSeconds),
            Codec.doubleRange(0.0D, Double.MAX_VALUE).optionalFieldOf("fog_rad")
                    .forGetter(RadiationSettings::fogRad),
            // 1.7: a chance of zero is an error, not "no fog"; fog is disabled by world_rad_effects.
            Codec.doubleRange(1.0D, Double.MAX_VALUE).optionalFieldOf("fog_chance")
                    .forGetter(RadiationSettings::fogChance),
            Codec.BOOL.optionalFieldOf("world_rad_effects").forGetter(RadiationSettings::worldRadEffects),
            Codec.doubleRange(0.0D, Double.MAX_VALUE).optionalFieldOf("ambient_rad")
                    .forGetter(RadiationSettings::ambientRad),
            // TODO(ntm-port): Leave negative ambient-floor/absorber support disabled until a non-legacy
            // gameplay design supplies a producer; every shipped min_bound remains zero.
            // Signed, and the only field here that may be negative: a floor the field may reach, not a magnitude.
            Codec.doubleRange(-Double.MAX_VALUE, 0.0D).optionalFieldOf("min_bound")
                    .forGetter(RadiationSettings::minBound),
            // Absent means "on iff a non-trivial hbm:rad_diffusivity mapping loaded"; present, it decides outright.
            Codec.BOOL.optionalFieldOf("diffusivity_transport")
                    .forGetter(RadiationSettings::diffusivityTransport)
    ).apply(instance, RadiationSettings::new));

    /** One derivation, shared by the Java sweep and by the backend's {@code rad_world_set_params}. */
    public record Resolved(double diffusionDt, double uuE, double retentionDt, long fogProbU64, double fogRad,
                           boolean worldRadEffects, double minBound, double ambientRad) {
    }

    public Resolved resolve() {
        return resolve(RadiationSystemNT.dT);
    }

    /** @param dT seconds per sweep, which is global */
    public Resolved resolve(double dT) {
        double diffusionDt = diffusivityOrDefault() * dT;
        double chance = fogChanceOrDefault();
        return new Resolved(
                diffusionDt,
                Math.exp(-(diffusionDt / 128.0d)),
                Math.exp(Math.log(0.5) * (dT / halfLifeSecondsOrDefault())),
                (chance > 0.0D && Double.isFinite(chance)) ? RadiationSystemNT.probU64(dT / chance) : 0L,
                fogRadOrDefault(),
                worldRadEffectsOrDefault(),
                minBoundOrDefault(),
                ambientRadOrDefault());
    }

    /** {@link #DEFAULT} where the pack ships no entry; a missing registry throws, being a wiring defect. */
    public static RadiationSettings forLevel(ServerLevel level) {
        return level.registryAccess().registryOrThrow(REGISTRY)
                .getOptional(ResourceKey.create(REGISTRY, level.dimension().location()))
                .orElse(DEFAULT);
    }

    public double diffusivityOrDefault() {
        return diffusivity.orElse(RadiationConfig.get().radDiffusivity());
    }

    public double halfLifeSecondsOrDefault() {
        return halfLifeSeconds.orElse(RadiationConfig.get().radHalfLifeSeconds());
    }

    public double fogRadOrDefault() {
        return fogRad.orElse(RadiationConfig.get().fogRad());
    }

    public double fogChanceOrDefault() {
        return fogChance.orElse(RadiationConfig.get().fogChance());
    }

    public boolean worldRadEffectsOrDefault() {
        return worldRadEffects.orElse(RadiationConfig.get().worldRadEffects());
    }

    // 1.7: RadiationConfig.hellRad, applied at EntityEffectHandler:284-285 as a FLOOR in rad/s on an entity's dose
    // (`if(rad < hellRad) rad = hellRad`) and never a term; only the per-dimension scope is ours. NOT 1.12.2's
    // CompatibilityConfig.dimensionRad, which ADDS its rate on top of the field.
    public double ambientRadOrDefault() {
        return ambientRad.orElse(0.0D);
    }

    /** The lowest density the field may hold, at or below zero; the default is 1.7's own {@code decrementRad} clamp. */
    public double minBoundOrDefault() {
        return minBound.orElse(0.0D);
    }
}
