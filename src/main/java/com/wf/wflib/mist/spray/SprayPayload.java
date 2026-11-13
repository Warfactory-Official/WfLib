package com.wf.wflib.mist.spray;

import com.wf.wflib.mist.api.MistEffect;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a sprayable fluid does, bound to the fluid by {@link SprayPayloads}. A fluid with a payload is fillable
 * into spray tanks.
 *
 * @param blocks   liquid curtain, per column; null => {@link SprayEffect#plain}
 * @param entities liquid curtain, per entity; null => entity-safe
 * @param gas      mist behaviour; non-null => the fluid sprays as a {@code GasCloud} puff
 * @param color    ARGB particle tint, -1 => the fluid's render colour
 */
public record SprayPayload(@Nullable SprayEffect blocks, @Nullable SprayEntityEffect entities,
                           @Nullable MistEffect gas, int color) {

    public static final SprayPayload INERT = new SprayPayload(null, null, null, -1);

    public SprayPayload blocks(SprayEffect effect) {
        return new SprayPayload(effect, entities, gas, color);
    }

    public SprayPayload entities(SprayEntityEffect effect) {
        return new SprayPayload(blocks, effect, gas, color);
    }

    public SprayPayload gas(MistEffect effect) {
        return new SprayPayload(blocks, entities, effect, color);
    }

    public SprayPayload color(int argb) {
        return new SprayPayload(blocks, entities, gas, argb);
    }
}
