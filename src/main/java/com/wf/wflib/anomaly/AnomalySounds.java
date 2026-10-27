package com.wf.wflib.anomaly;

import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * An anomaly's STALKER sounds, as its zone config names them.
 *
 * @param idle        idle_sound: looped while present
 * @param blowout     blowout_sound: once per blowout, from {@code blowoutTick} (blowout_sound_time); cut off if it
 *                    fizzles
 * @param blowoutTick ticks into the blowout it starts at
 * @param hit         hit_sound: at whatever it hits
 * @param entrance    entrance_sound: at whatever comes within it
 * @param tear        body_tearing_sound: a body it tears apart; null if it does not
 */
public record AnomalySounds(Supplier<SoundEvent> idle, Supplier<SoundEvent> blowout, int blowoutTick,
                            Supplier<SoundEvent> hit, Supplier<SoundEvent> entrance,
                            @Nullable Supplier<SoundEvent> tear) {
}
