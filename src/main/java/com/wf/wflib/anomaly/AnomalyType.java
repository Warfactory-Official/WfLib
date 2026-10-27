package com.wf.wflib.anomaly;

import com.wf.wflib.anomaly.AnomalyLook.Style;
import com.wf.wflib.WFSounds;
import com.wf.wflib.damage.WFDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * One kind of anomaly: what it does ({@link AnomalyBehaviour}, one per X-Ray zone class), how it looks, and what it
 * hits with (armour resolves it by that type: shock, thermal, chemical). Values come from GAMMA's zone configs
 * (the average variant where there are several); comments name the section.
 */
// Sounds as lambdas: the types load before (and in tests without) the sound registry.
public record AnomalyType(String name, AnomalyBehaviour behaviour, AnomalyLook look, ResourceKey<DamageType> damage,
                          AnomalySounds sounds) {

    /**
     * {@code zone_mine_gravitational_strong}: a long pull, the caught lifted high, a big tear at 6 s
     * (blowout_explosion_time 5999 ms: where anomaly_mincer_blowout peaks), gone 15 s.
     */
    public static final AnomalyType VORTEX = new AnomalyType("Vortex",
            new MincerTuning(8.0D, 0.75D, 0.6D, 0.6D, 120, 300, 1.2D, 1.0D, 1.4D, 1.8D, 0.25D, 0.55D, 3.5D, 1.2D,
                    0.9D, 140, 80),
            new AnomalyLook(Style.GRAVITY, 5.0F, 1.6D, 0.012F, 0.25F, 0.03F, 3.0F, 3.0F, 10, 0.12D, 0.35D,
                    0xDCFFCB, 28.0F, 8, 0.0F, 0.0F, null, null, 1.0F, 1.0F),
            WFDamageTypes.GRAVITY_ANOMALY,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_GRAVI_IDLE01.get(),
                    () -> WFSounds.ANOMALY_ANOMALY_MINCER_BLOWOUT.get(),
                    2,
                    () -> WFSounds.ANOMALY_ANOMALY_GRAVY_HIT1.get(),
                    () -> WFSounds.ANOMALY_ANOMALY_GRAVY_HIT1.get(),
                    () -> WFSounds.ANOMALY_ANOMALY_BODY_TEAR_1.get()));

    /**
     * {@code zone_mine_gravitational_average}: smaller, quick (2.1 s), pulls harder (throw_in_impulse_alive
     * 1000), spins the caught round at the ground (tele_height 0.05) and throws them far (throw_out_impulse 4000);
     * players are caught nearer the middle (0.5) than others (0.8); gone 5 s (disable_time).
     */
    public static final AnomalyType WHIRLIGIG = new AnomalyType("Whirligig",
            new MincerTuning(6.0D, 0.75D, 0.8D, 0.5D, 42, 100, 0.8D, 1.67D, 1.0D, 0.6D, 0.5D, 0.8D, 0.5D, 1.6D,
                    1.0D, 100, 100),
            new AnomalyLook(Style.GRAVITY, 3.5F, 1.0D, 0.012F, 0.6F, 0.035F, 5.0F, 3.0F, 14, 0.2D, 0.6D,
                    0xDCFFCB, 30.0F, 8, 0.0F, 0.0F, null, null, 1.0F, 1.0F),
            WFDamageTypes.GRAVITY_ANOMALY,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_GRAVI_IDLE00.get(),
                    () -> WFSounds.ANOMALY_GRAVI_BLOWOUT6.get(),
                    1,
                    () -> WFSounds.ANOMALY_ANOMALY_GRAVY_HIT1.get(),
                    () -> WFSounds.ANOMALY_ANOMALY_GRAVY_HIT1.get(),
                    () -> WFSounds.ANOMALY_ANOMALY_BODY_TEAR_1.get()));

    /**
     * {@code zone_mine_gravitational_weak}: small ($def_sphere 3), slams whatever steps in 5 ticks later (awaking 50
     * + explosion 200 ms) up and away (max_start_power 0.4, strike), a brown flash (0.5/0.4/0.3, 30 m, 0.9 s);
     * ready again in 1 s (accamulate_time), but leaves what it hit alone 5 s (disable_time).
     */
    public static final AnomalyType SPRINGBOARD = new AnomalyType("Springboard",
            new BlastTuning(3.0D, 5, 10, 20, 100, 0.4D, 1.3D, false, 0),
            new AnomalyLook(Style.GRAVITY, 2.2F, 0.6D, 0.012F, 0.2F, 0.0F, 0.0F, 0.6F, 0, 0.08D, 0.0D,
                    0xFFCC99, 30.0F, 18, 0.0F, 0.4F, null, null, 1.0F, 1.0F),
            WFDamageTypes.GRAVITY_ANOMALY,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_GRAVI_RUMBLE1.get(),
                    () -> WFSounds.ANOMALY_ANOMALY_GRAVY_BLAST1.get(),
                    2,
                    () -> WFSounds.ANOMALY_ANOMALY_GRAVY_HIT1.get(),
                    () -> WFSounds.ANOMALY_ANOMALY_GRAVY_HIT1.get(),
                    null));

    /**
     * {@code zone_mine_electric}: discharges into whatever touches it, a thrown bolt included (ignore_small false),
     * 100 ms after (max_start_power 0.66 for the average, shock, no throw); a blue-white flash (0.8/0.9/1, 30 m, 1 s)
     * and a faint glow while idle (idle_light_range 4); ready again in 1.5 s, and hits the same thing again on the
     * next discharge (disable_time 50 ms; 5 ticks here, so one discharge is one hit). Three times its zone
     * ($def_sphere 2) across, its STALKER particles spread to match (as high and as big as STALKER's), its glow too.
     */
    public static final AnomalyType ELECTRO = new AnomalyType("Electro",
            new BlastTuning(6.0D, 2, 4, 30, 5, 0.66D, 0.0D, true, 0),
            new AnomalyLook(Style.ELECTRIC, 2.0F, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xCCE6FF, 30.0F, 20, 9.0F, 0.6F, "zones_mine/zone_electric_mine_idle", "anomaly2/electra2_blast",
                    3.0F, 1.0F),
            WFDamageTypes.ELECTRIC,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_ELECTRA_IDLE1.get(),
                    () -> WFSounds.ANOMALY_ELECTRA_BLAST1.get(),
                    1,
                    () -> WFSounds.ANOMALY_ELECTRA_HIT.get(),
                    () -> WFSounds.ANOMALY_ELECTRA_HIT.get(),
                    null));

    /**
     * {@code zone_mine_thermal}: a heat shimmer that throws up a 7 s column of flame (blowout_time 7000) at whatever
     * touches it (max_start_power 0.5 for the average, light_burn, no throw), setting it alight; an orange glow
     * (1.9/1.6/1.3, 9 m) through the burn; leaves what it hit alone 10 s (disable_time). 1.5 times its zone, its
     * STALKER particles scaled to match (their heat-distortion sprites left out).
     */
    public static final AnomalyType BURNER = new AnomalyType("Burner",
            new BlastTuning(3.0D, 2, 140, 5, 200, 0.5D, 0.0D, true, 4),
            new AnomalyLook(Style.FIRE, 1.6F, 0.6D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xFFD6AD, 9.0F, 140, 3.0F, 1.0F, "static/buble_distort", "static/zharka_static", 1.5F, 1.5F),
            WFDamageTypes.FIRE,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_FIRE_IDLE.get(),
                    () -> WFSounds.ANOMALY_ZHAR_BLOW.get(),
                    1,
                    () -> WFSounds.ANOMALY_ZHAR.get(),
                    () -> WFSounds.ANOMALY_ZHAR.get(),
                    null));

    /**
     * {@code zone_mine_acidic}: a bubbling green patch that splashes whatever touches it for 1.4 s
     * (max_start_power 0.3 for the average, chemical_burn, no throw); a green flash (1.1/1.7/1.4, 7 m, 0.35 s);
     * leaves what it hit alone 10 s (disable_time). Three times its zone across, its STALKER particles spread to
     * match (as high and as big as STALKER's), its glow and flash too.
     */
    public static final AnomalyType ACID = new AnomalyType("Acid",
            new BlastTuning(6.0D, 2, 28, 6, 200, 0.3D, 0.0D, true, 0),
            new AnomalyLook(Style.ACID, 1.6F, 0.4D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xA6FFD1, 14.0F, 7, 8.0F, 1.0F, "zones_mine/zone_acidic_mine_idle",
                    "zones_mine/zone_acidic_mine_hit", 3.0F, 1.0F),
            WFDamageTypes.ACID,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_BUZZ_IDLE.get(),
                    () -> WFSounds.ANOMALY_BUZZ_HIT.get(),
                    1,
                    () -> WFSounds.ANOMALY_BUZZ_HIT.get(),
                    () -> WFSounds.ANOMALY_BUZZ_HIT.get(),
                    null));
}
