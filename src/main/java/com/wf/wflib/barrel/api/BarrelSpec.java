package com.wf.wflib.barrel.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * Barrel heat model of one gun. Temperatures degC, times s, spread degrees of cone half-angle.
 *
 * @param type    quick-change family: spare barrels of the same type fit; empty = fixed barrel
 * @param perShot rise per round
 * @param tau     Newtonian cooling time constant toward {@link BarrelState#AMBIENT}
 * @param max     temperature cap
 * @param spread  extra spread by temperature
 * @param jam     stoppage chance per round
 * @param cookOff chambered-round ignition rate, 1/s, by temperature; value 0 = never. Closed breech only (consumer)
 * @param wear    permanent damage while hotter than {@link Wear#from}
 * @param swap    barrel change times
 * @param variants other barrels of {@code type} by id (spare {@link SpareBarrel#variant}); absent id = standard
 */
public record BarrelSpec(Optional<ResourceLocation> type, float perShot, float tau, float max, Ramp spread, Jam jam,
                         Ramp cookOff, Wear wear, Swap swap, Map<String, Variant> variants) {
    /** {@code length}: m, consumer's barrel length (muzzle); {@code velocity}: muzzle speed factor. */
    public record Variant(float length, float velocity) {
        public static final Codec<Variant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.01F, Float.MAX_VALUE).fieldOf("length").forGetter(Variant::length),
                Codec.floatRange(0.01F, 10).optionalFieldOf("velocity", 1F).forGetter(Variant::velocity)
        ).apply(i, Variant::new));
    }

    /** {@code value * clamp((t - from) / (to - from), 0, 1)}. */
    public record Ramp(float from, float to, float value) {
        public static final Codec<Ramp> CODEC = RecordCodecBuilder.<Ramp>create(i -> i.group(
                Codec.FLOAT.fieldOf("from").forGetter(Ramp::from),
                Codec.FLOAT.fieldOf("to").forGetter(Ramp::to),
                Codec.floatRange(0, Float.MAX_VALUE).fieldOf("value").forGetter(Ramp::value)
        ).apply(i, Ramp::new)).validate(r -> r.to > r.from ? DataResult.success(r)
                : DataResult.error(() -> "ramp to " + r.to + " <= from " + r.from));

        public float at(float t) {
            return value * Math.clamp((t - from) / (to - from), 0, 1);
        }
    }

    /** {@code base + ramp(t)} per round; {@code ramp} value = chance at {@code to}. */
    public record Jam(float base, Ramp ramp) {
        public static final Codec<Jam> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1).optionalFieldOf("base", 0F).forGetter(Jam::base),
                Ramp.CODEC.optionalFieldOf("ramp", DEFAULT_JAM).forGetter(Jam::ramp)
        ).apply(i, Jam::new));
    }

    /**
     * Rate {@code d wear / dt = (t - from) / (max - from) / life} => {@code life} s at {@code max} wears it out.
     * {@code spread} / {@code jam} added at wear 1, linear.
     */
    public record Wear(float from, float life, float spread, float jam) {
        public static final Codec<Wear> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("from", 450F).forGetter(Wear::from),
                Codec.floatRange(1, Float.MAX_VALUE).optionalFieldOf("life", 600F).forGetter(Wear::life),
                Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf("spread", 0.3F).forGetter(Wear::spread),
                Codec.floatRange(0, 1).optionalFieldOf("jam", 0.02F).forGetter(Wear::jam)
        ).apply(i, Wear::new));
    }

    /** {@code self}: one player alone; {@code coop}: an assistant while the gunner holds / mans the gun. */
    public record Swap(float self, float coop) {
        public static final Codec<Swap> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf("self", 6F).forGetter(Swap::self),
                Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf("coop", 2.5F).forGetter(Swap::coop)
        ).apply(i, Swap::new));

        public int selfTicks() {
            return Math.max(1, Math.round(self * 20));
        }

        public int coopTicks() {
            return Math.max(1, Math.round(coop * 20));
        }
    }

    static final Ramp DEFAULT_SPREAD = new Ramp(150, 600, 0.35F);
    static final Ramp DEFAULT_JAM = new Ramp(300, 700, 0.03F);
    static final Ramp DEFAULT_COOK_OFF = new Ramp(320, 600, 0.6F);
    static final Jam JAM = new Jam(0, DEFAULT_JAM);
    static final Wear WEAR = new Wear(450, 600, 0.3F, 0.02F);
    static final Swap SWAP = new Swap(6, 2.5F);

    public static final BarrelSpec DEFAULT = new BarrelSpec(Optional.empty(), 2, 120, 800, DEFAULT_SPREAD, JAM,
            DEFAULT_COOK_OFF, WEAR, SWAP, Map.of());

    public static final Codec<BarrelSpec> CODEC = RecordCodecBuilder.<BarrelSpec>create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("type").forGetter(BarrelSpec::type),
            Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf("perShot", DEFAULT.perShot).forGetter(BarrelSpec::perShot),
            Codec.floatRange(1, Float.MAX_VALUE).optionalFieldOf("tau", DEFAULT.tau).forGetter(BarrelSpec::tau),
            Codec.FLOAT.optionalFieldOf("max", DEFAULT.max).forGetter(BarrelSpec::max),
            Ramp.CODEC.optionalFieldOf("spread", DEFAULT_SPREAD).forGetter(BarrelSpec::spread),
            Jam.CODEC.optionalFieldOf("jam", JAM).forGetter(BarrelSpec::jam),
            Ramp.CODEC.optionalFieldOf("cookOff", DEFAULT_COOK_OFF).forGetter(BarrelSpec::cookOff),
            Wear.CODEC.optionalFieldOf("wear", WEAR).forGetter(BarrelSpec::wear),
            Swap.CODEC.optionalFieldOf("swap", SWAP).forGetter(BarrelSpec::swap),
            Codec.unboundedMap(Codec.STRING, Variant.CODEC).optionalFieldOf("variants", Map.of())
                    .forGetter(BarrelSpec::variants)
    ).apply(i, BarrelSpec::new)).validate(BarrelSpec::check);

    private static DataResult<BarrelSpec> check(BarrelSpec s) {
        if (s.max <= BarrelState.AMBIENT) {
            return DataResult.error(() -> "max " + s.max + " <= ambient " + BarrelState.AMBIENT);
        }
        if (s.wear.from <= BarrelState.AMBIENT || s.wear.from >= s.max) {
            return DataResult.error(() -> "wear.from " + s.wear.from + " outside (ambient, max)");
        }
        if (!s.variants.isEmpty() && s.type.isEmpty()) {
            return DataResult.error(() -> "variants without a quick-change type");
        }
        return DataResult.success(s);
    }

    public boolean quickChange() {
        return type.isPresent();
    }
}
