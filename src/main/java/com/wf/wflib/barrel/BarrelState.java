package com.wf.wflib.barrel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Barrel temperature {@code temp} degC at game tick {@code at}, {@code wear} in [0, 1]. Immutable, lazily cooled: read
 * through {@link #at}, never ticked => survives inventories, unloaded chunks and client replay.
 */
public record BarrelState(float temp, long at, float wear) {
    public static final float AMBIENT = 20;
    public static final BarrelState NEW = new BarrelState(AMBIENT, 0, 0);

    public static final Codec<BarrelState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("temp", AMBIENT).forGetter(BarrelState::temp),
            Codec.LONG.optionalFieldOf("at", 0L).forGetter(BarrelState::at),
            Codec.floatRange(0, 1).optionalFieldOf("wear", 0F).forGetter(BarrelState::wear)
    ).apply(i, BarrelState::new));

    public static final StreamCodec<ByteBuf, BarrelState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, BarrelState::temp,
            ByteBufCodecs.VAR_LONG, BarrelState::at,
            ByteBufCodecs.FLOAT, BarrelState::wear,
            BarrelState::new);

    /**
     * Cooled to tick {@code now}: {@code T(t) = A + (T0 - A) e^(-t/tau)}; wear += closed-form integral of
     * {@code (T - from)} over the time spent above {@code from}. {@code now <= at} => this.
     */
    public BarrelState at(BarrelSpec spec, long now) {
        if (now <= at) {
            return this;
        }
        double dt = (now - at) / 20.0, tau = spec.tau(), excess = temp - AMBIENT;
        float t = (float) (AMBIENT + excess * Math.exp(-dt / tau));
        float w = wear;
        BarrelSpec.Wear sw = spec.wear();
        if (temp > sw.from()) {
            double d = Math.min(dt, tau * Math.log(excess / (sw.from() - AMBIENT)));
            double area = (AMBIENT - sw.from()) * d + excess * tau * (1 - Math.exp(-d / tau));
            w = (float) Math.min(1, wear + area / ((spec.max() - sw.from()) * sw.life()));
        }
        return new BarrelState(t, now, w);
    }

    /** Cooled to {@code now}, then one round's heat. */
    public BarrelState fired(BarrelSpec spec, long now) {
        BarrelState c = at(spec, now);
        return new BarrelState(Math.min(spec.max(), c.temp + spec.perShot()), now, c.wear);
    }

    /** Extra cone half-angle, degrees; call on a cooled state. */
    public float spread(BarrelSpec spec) {
        return spec.spread().at(temp) + wear * spec.wear().spread();
    }

    public float jamChance(BarrelSpec spec) {
        return Math.min(1, spec.jam().base() + spec.jam().ramp().at(temp) + wear * spec.wear().jam());
    }

    /** Ignition rate of a chambered round, 1/s. */
    public float cookOffRate(BarrelSpec spec) {
        return spec.cookOff().at(temp);
    }

    /** Hazard {@code rate} over {@code seconds} vs uniform {@code u}. */
    public static boolean happens(float rate, double seconds, double u) {
        return rate > 0 && u < -Math.expm1(-rate * seconds);
    }

    /** Visible red heat, 0 at 450 degC, 1 at 750. */
    public float glow() {
        return Math.clamp((temp - 450) / 300F, 0, 1);
    }

    /** Heat haze / barrel smoke, 0 at 120 degC, 1 at 400. */
    public float haze() {
        return Math.clamp((temp - 120) / 280F, 0, 1);
    }
}
