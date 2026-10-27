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
                          AnomalySounds sounds, Quirk quirk) {

    public AnomalyType(String name, AnomalyBehaviour behaviour, AnomalyLook look, ResourceKey<DamageType> damage,
                       AnomalySounds sounds) {
        this(name, behaviour, look, damage, sounds, Quirk.NONE);
    }

    /** What an anomaly does beyond its behaviour: Dynamic Anomalies Overhaul's scripts, X-Ray's odder zone classes. */
    public enum Quirk {
        NONE,
        /**
         * Flash ({@code anomalies_hit_functions.zone_mine_flash}): a player it hits loses 3-8 hours (hungry as if they
         * had passed, a few % of health, the clock too when alone on the server); a creature vanishes.
         */
        TIME_SLIP,
        /** Ghost ({@code anomalies_spawn_functions.zone_mine_ghost}): a Lissajous over the ground round its block. */
        WANDER,
        /** Comet ({@code CTorridZone}): a loop it finds through the terrain round its block, at chest height. */
        ORBIT,
        /** Green Dragon: the living inside are slowed to about half ({@code zone_mine_green_dragon}, speed 0.5). */
        SLOWS,
        /** Liquid Gold: players inside tire and hunger, more toward the middle (stamina, thirst). */
        DRAINS,
        /**
         * Rebounder: rounds and arrows heading in glance off its sphere at half speed; any that still hit someone
         * inside (fired from in there, say) lose half or more of their damage.
         */
        REBOUNDS,
        /** No gravity inside ({@code CNoGravityZone}): the living, loose objects and projectiles float. */
        WEIGHTLESS;

        /** Leaves its block: center() follows a path. */
        public boolean moves() {
            return this == WANDER || this == ORBIT;
        }
    }

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
     * ready again in 1 s (accamulate_time).
     */
    public static final AnomalyType SPRINGBOARD = new AnomalyType("Springboard",
            new BlastTuning(3.0D, 5, 10, 20, 0.4D, 1.3D, false, 0),
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
     * {@code zone_mine_electric}: discharges into whatever living thing touches it, chickens included (ignore_small
     * false), 100 ms after (max_start_power 0.66 for the average, shock, no throw); a blue-white flash (0.8/0.9/1,
     * 30 m, 1 s) and a faint glow while idle (idle_light_range 4); then gone for 3 s (accamulate_time 3000, Dynamic
     * Anomalies Overhaul), fading back in. Three times its zone ($def_sphere 2) across, its STALKER particles spread
     * to match (as high and as big as STALKER's), its glow too.
     */
    public static final AnomalyType ELECTRO = new AnomalyType("Electro",
            new BlastTuning(6.0D, 2, 4, 60, 0.66D, 0.0D, true, 0),
            new AnomalyLook(Style.ELECTRIC, 2.0F, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xCCE6FF, 30.0F, 20, 9.0F, 0.0F, "zones_mine/zone_electric_mine_idle", "anomaly2/electra2_blast",
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
     * (1.9/1.6/1.3, 9 m) through the burn; flares again 0.25 s after (accamulate_time) while anything stays in it.
     * zhar_blow runs 12 s, so it fades out with the flame. 1.5 times its zone, its STALKER particles scaled to match
     * (their heat-distortion sprites left out).
     */
    public static final AnomalyType BURNER = new AnomalyType("Burner",
            new BlastTuning(3.0D, 2, 140, 5, 0.5D, 0.0D, true, 4),
            new AnomalyLook(Style.FIRE, 1.6F, 0.6D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xFFD6AD, 9.0F, 140, 3.0F, 1.0F, "static/buble_distort", "static/zharka_static", 1.5F, 1.5F),
            WFDamageTypes.FIRE,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_FIRE_IDLE.get(),
                    () -> WFSounds.ANOMALY_ZHAR_BLOW.get(),
                    1,
                    () -> WFSounds.ANOMALY_ZHAR.get(),
                    () -> WFSounds.ANOMALY_ZHAR.get(),
                    null,
                    true));

    /**
     * {@code zone_mine_acidic}: a bubbling green patch that splashes whatever touches it for 1.4 s
     * (max_start_power 0.3 for the average, chemical_burn, no throw); a green flash (1.1/1.7/1.4, 7 m, 0.35 s);
     * splashes again 0.3 s later (accamulate_time) for as long as anything stays in it, so about every 1.7 s.
     * Three times its zone across, its STALKER particles spread to match (as high and as big as STALKER's), its
     * glow and flash too.
     */
    public static final AnomalyType ACID = new AnomalyType("Acid",
            new BlastTuning(6.0D, 2, 28, 6, 0.3D, 0.0D, true, 0),
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

    /**
     * {@code zone_mine_steam}: a Burner that throws up scalding steam (thermal, max_start_power 0.5 for the average,
     * no fire, so no light of its own). steam_blowout runs 12 s, so it fades with the 7 s jet.
     */
    public static final AnomalyType STEAM = new AnomalyType("Steam",
            new BlastTuning(3.0D, 2, 140, 5, 0.5D, 0.0D, true, 0),
            new AnomalyLook(Style.FIRE, 1.6F, 0.6D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xE6EEF5, 0.0F, 0, 0.0F, 1.0F, "zones/zone_steam_idle", "static/zharka_static_steam", 1.5F, 1.5F),
            WFDamageTypes.FIRE,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_STEAM.get(),
                    () -> WFSounds.ANOMALY_STEAM_BLOWOUT.get(),
                    1,
                    () -> WFSounds.ANOMALY_ZHAR.get(),
                    () -> WFSounds.ANOMALY_ZHAR.get(),
                    null,
                    true));

    /**
     * {@code zone_mine_flash} (Dynamic Anomalies Overhaul, a "temporal" anomaly): whatever large and alive steps in is
     * flung through time 250 ms later ({@link Quirk#TIME_SLIP}); a brown flash (0.5/0.4/0.3, 30 m, 0.9 s); then gone a
     * minute (accamulate_time 60000). Small things don't set it off (ignore_small).
     */
    public static final AnomalyType FLASH = new AnomalyType("Flash",
            new BlastTuning(4.5D, 5, 11, 1200, 0.0D, 0.4D, false, 0),
            new AnomalyLook(Style.ELECTRIC, 2.0F, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0x806650, 30.0F, 18, 0.0F, 0.0F, "eugenium_anomalies/anomaly_flash/flash_idle",
                    "eugenium_anomalies/anomaly_flash/flash_blast", 1.0F, 0.6F),
            WFDamageTypes.ELECTRIC,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_FLASH_IDLE.get(),
                    () -> WFSounds.ANOMALY_FLASH_SHIELD.get(),
                    1,
                    () -> WFSounds.ANOMALY_FLASH_SHIELD.get(),
                    () -> WFSounds.ANOMALY_FLASH_SHIELD.get(),
                    null),
            Quirk.TIME_SLIP);

    /**
     * {@code zone_mine_ghost}: an electro-chemical wisp drifting round its block ({@link Quirk#WANDER}), discharging
     * into whatever large and alive it touches 250 ms after (max_start_power 1, shock, a slight shove); a green idle
     * glow (4 m); ready again in 1 s.
     */
    public static final AnomalyType GHOST = new AnomalyType("Ghost",
            new BlastTuning(3.0D, 5, 11, 20, 1.0D, 0.3D, false, 0),
            new AnomalyLook(Style.ELECTRIC, 1.0F, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0x99FFB3, 12.0F, 18, 4.0F, 1.0F, "eugenium_anomalies/anomaly_ghost/ghost_idle",
                    "eugenium_anomalies/anomaly_ghost/ghost_blast", 1.0F, 1.0F),
            WFDamageTypes.ELECTRIC,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_GHOST_IDLE.get(),
                    () -> WFSounds.ANOMALY_GHOST_BLOWOUT.get(),
                    1,
                    () -> WFSounds.ANOMALY_GHOST_BLOWOUT.get(),
                    null,
                    null),
            Quirk.WANDER);

    /**
     * {@code zone_mine_net}: a web of charged particles that wraps whatever large and alive steps in, 250 ms after
     * (max_start_power 0.7, shock, a slight shove); no light; ready again in 1 s.
     */
    public static final AnomalyType NET = new AnomalyType("Net",
            new BlastTuning(4.5D, 5, 11, 20, 0.7D, 0.3D, false, 0),
            new AnomalyLook(Style.ELECTRIC, 1.5F, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xCCE6FF, 0.0F, 0, 0.0F, 1.0F, "eugenium_anomalies/anomaly_net/net_idle",
                    "eugenium_anomalies/anomaly_net/net_blast", 1.5F, 1.0F),
            WFDamageTypes.ELECTRIC,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_NET_IDLE.get(),
                    () -> WFSounds.ANOMALY_NET_BLOWOUT.get(),
                    1,
                    () -> WFSounds.ANOMALY_NET_BLOWOUT.get(),
                    () -> WFSounds.ANOMALY_NET_SHIELD.get(),
                    null));

    /**
     * {@code zone_mine_point} (Ball Lightning): a hovering spark that strikes whatever alive touches it, chickens too
     * (max_start_power 0.7, shock, at once: awaking 10 ms); a blue-white flash (0.8/0.9/1, 30 m, 1 s), an idle glow
     * (4 m); ready again in 1 s. (STALKER's battery charging is left out: nothing here runs on batteries.)
     */
    public static final AnomalyType BALL_LIGHTNING = new AnomalyType("Ball Lightning",
            new BlastTuning(3.0D, 1, 2, 20, 0.7D, 0.0D, true, 0),
            new AnomalyLook(Style.ELECTRIC, 1.0F, 0.75D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xCCE6FF, 30.0F, 20, 4.0F, 1.0F, "eugenium_anomalies/anomaly_point/point_idle",
                    "eugenium_anomalies/anomaly_point/point_blast", 1.0F, 0.6F),
            WFDamageTypes.ELECTRIC,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_POINT_IDLE.get(),
                    () -> WFSounds.ANOMALY_POINT_BLOWOUT.get(),
                    1,
                    () -> WFSounds.ANOMALY_POINT_BLOWOUT.get(),
                    () -> WFSounds.ANOMALY_POINT_BLOWOUT.get(),
                    null));

    /**
     * {@code zone_mine_gold} (Liquid Gold): an Acid of feverish gold mist (max_start_power 0.8, chemical, 1.4 s,
     * again 0.3 s later while anything stays); players near it tire and hunger ({@link Quirk#DRAINS}). No light.
     */
    public static final AnomalyType LIQUID_GOLD = new AnomalyType("Liquid Gold",
            new BlastTuning(3.0D, 2, 30, 6, 0.8D, 0.0D, true, 0),
            new AnomalyLook(Style.ACID, 1.6F, 0.4D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xFFC34D, 0.0F, 0, 0.0F, 1.0F, "eugenium_anomalies/anomaly_gold/gold_idle",
                    "eugenium_anomalies/anomaly_gold/gold_blast", 1.5F, 1.0F),
            WFDamageTypes.ACID,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_GOLD_IDLE.get(),
                    () -> WFSounds.ANOMALY_GOLD_BLOWOUT.get(),
                    1,
                    null,
                    null,
                    null),
            Quirk.DRAINS);

    /**
     * {@code zone_mine_green_dragon}: a poisonous green cloud that bursts at whatever alive steps in, 250 ms after
     * (max_start_power 0.15, chemical, a slight shove), and slows the living inside to half ({@link Quirk#SLOWS});
     * ready again in 1 s.
     */
    public static final AnomalyType GREEN_DRAGON = new AnomalyType("Green Dragon",
            new BlastTuning(4.5D, 5, 11, 20, 0.15D, 0.3D, true, 0),
            new AnomalyLook(Style.ACID, 1.6F, 0.4D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0x8CFF8C, 0.0F, 0, 0.0F, 1.0F, "eugenium_anomalies/anomaly_green_dragon/green_dragon_idle",
                    "eugenium_anomalies/anomaly_green_dragon/green_dragon_blast", 1.5F, 1.0F),
            WFDamageTypes.ACID,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_GREEN_DRAGON_IDLE.get(),
                    () -> WFSounds.ANOMALY_GREEN_DRAGON_BLOWOUT.get(),
                    1,
                    () -> WFSounds.ANOMALY_GREEN_DRAGON_BLOWOUT.get(),
                    () -> WFSounds.ANOMALY_GREEN_DRAGON_BLOWOUT.get(),
                    null),
            Quirk.SLOWS);

    /**
     * {@code zone_mine_sphere} (Rebounder): a disc of force waves (a CMincer pushing out: throw_in_impulse negative)
     * that bats away whatever alive steps in (max_start_power 0.65, strike, a hard throw outward, at once: 70 ms),
     * ready again in 0.4 s; rounds and arrows coming at it glance off, and any that hit someone inside lose half or
     * more ({@link Quirk#REBOUNDS}). Its look is all heat-haze in STALKER: here a lens and an upright disc of rings.
     */
    public static final AnomalyType REBOUNDER = new AnomalyType("Rebounder",
            new BlastTuning(6.0D, 2, 2, 8, 0.65D, 1.6D, true, 0),
            new AnomalyLook(Style.GRAVITY, 2.5F, 2.5D, 0.02F, 0.4F, 0.03F, 2.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xDCFFCB, 0.0F, 0, 0.0F, 1.0F, null, "eugenium_anomalies/anomaly_sphere/sphere_blast", 1.5F,
                    1.0F),
            WFDamageTypes.GRAVITY_ANOMALY,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_SPHERE_IDLE.get(),
                    () -> WFSounds.ANOMALY_SPHERE_BLOWOUT.get(),
                    1,
                    () -> WFSounds.ANOMALY_SPHERE_BLOWOUT.get(),
                    () -> WFSounds.ANOMALY_SPHERE_BLOWOUT.get(),
                    null),
            Quirk.REBOUNDS);

    /**
     * {@code zone_mine_mefistotel} (Mephistopheles): a flower-like formation that sings as anything large and alive
     * comes near, yanks it in from anywhere inside (blowout_radius_percent 1) and shreds it (max_start_power 1.5,
     * shock, torn apart: body_tear). STALKER's fires 70 ms after; here 1.5 s, so the yank is seen before the end.
     */
    public static final AnomalyType MEPHISTOPHELES = new AnomalyType("Mephistopheles",
            new MincerTuning(6.0D, 0.8D, 1.0D, 1.0D, 30, 60, 1.5D, 2.2D, 0.6D, 0.8D, 0.6D, 0.6D, 0.6D, 0.3D,
                    0.3D, 100, 100),
            new AnomalyLook(Style.GRAVITY, 2.0F, 0.8D, 0.006F, 0.3F, 0.02F, 3.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xB3CCA6, 20.0F, 16, 0.0F, 0.0F, "eugenium_anomalies/anomaly_mefistotel/mefistotel_idle",
                    "eugenium_anomalies/anomaly_mefistotel/mefistotel_blast", 1.5F, 1.0F),
            WFDamageTypes.ELECTRIC,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_MEFISTOTEL_IDLE.get(),
                    () -> WFSounds.ANOMALY_MEFISTOTEL_BLOWOUT.get(),
                    1,
                    () -> WFSounds.ANOMALY_MEFISTOTEL_BLOWOUT.get(),
                    () -> WFSounds.ANOMALY_MEFISTOTEL_SHIELD.get(),
                    () -> WFSounds.ANOMALY_ANOMALY_BODY_TEAR_1.get()));

    /**
     * {@code zone_burning_fuzz} (Burnt Fuzz, GAMMA balance; STALKER's strands are a level mesh, here simulated rope):
     * hangs from a ceiling and lashes whatever alive runs
     * under it (min_speed_to_react 6: a sprint; walking past is safe), for a second (max_start_power 0.3, chemical),
     * then rests 5 s.
     */
    public static final AnomalyType BURNT_FUZZ = new AnomalyType("Burnt Fuzz",
            new BlastTuning(2.0D, 1, 20, 100, 0.3D, 0.0D, true, 0, 0.25D),
            new AnomalyLook(Style.FUZZ, 1.0F, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.1F, 0, 0.0D, 0.0D,
                    0xA6FFD1, 0.0F, 0, 0.0F, 1.0F, null, "anomaly2/electra_entrance_small", 1.0F, 1.0F),
            WFDamageTypes.ACID,
            new AnomalySounds(
                    null,
                    () -> WFSounds.ANOMALY_BFUZZ_BLOWOUT.get(),
                    0,
                    () -> WFSounds.ANOMALY_BFUZZ_HIT.get(),
                    null,
                    null));

    /**
     * {@code zone_hvatalka} (Grabber): Burnt Fuzz's big sister, set off by anything alive that moves at all
     * (min_speed_to_react 1), clutching it for 7 s with a hit that kills the unarmoured (max_start_power 1.5,
     * strike); buzzing flies. Its STALKER attack sprites never shipped: Burnt Fuzz's stand in.
     */
    public static final AnomalyType GRABBER = new AnomalyType("Grabber",
            new BlastTuning(3.75D, 2, 140, 5, 1.5D, 0.5D, true, 0, 0.05D),
            new AnomalyLook(Style.FUZZ, 1.5F, 0.5D, 0.0F, 0.0F, 0.0F, 0.0F, 0.15F, 0, 0.0D, 0.0D,
                    0xA6FFD1, 0.0F, 0, 0.0F, 1.0F, null, "anomaly2/pux_blast", 1.5F, 1.0F),
            WFDamageTypes.GRAVITY_ANOMALY,
            new AnomalySounds(
                    null,
                    () -> WFSounds.ANOMALY_RND_FLY2.get(),
                    1,
                    () -> WFSounds.ANOMALY_RND_FLY1.get(),
                    () -> WFSounds.ANOMALY_RND_FLY1.get(),
                    null));

    /**
     * {@code fireball_zone} (Comet, CTorridZone): a ball of fire on a loop ({@link Quirk#ORBIT}) that burns whatever
     * large and alive it touches, again and again while it stays (max_start_power 1, burn, setting it alight); lit
     * all the time (idle_light 15 m; here 6, as STALKER's tone mapping keeps its glow far softer than raw light).
     */
    public static final AnomalyType COMET = new AnomalyType("Comet",
            new BlastTuning(3.0D, 2, 20, 2, 1.0D, 0.0D, false, 5),
            new AnomalyLook(Style.FIRE, 1.5F, 0.0D, 0.008F, 0.6F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xFFB066, 8.0F, 40, 6.0F, 1.0F, "anomaly2/heat_02", "anomaly2/heat_03", 1.0F, 0.5F),
            WFDamageTypes.FIRE,
            new AnomalySounds(
                    () -> WFSounds.ANOMALY_FIREBALL_IDLE.get(),
                    () -> WFSounds.ANOMALY_FIREBALL_BLOW.get(),
                    1,
                    () -> WFSounds.ANOMALY_ZHAR.get(),
                    () -> WFSounds.ANOMALY_ZHAR.get(),
                    null),
            Quirk.ORBIT);

    /**
     * {@code zone_field_radioactive_average} (GAMMA Radiation Dynamic Areas): 15 m of radiation, 0.04 a second at the
     * middle (here 40 rads/s) and half that at the rim (attenuation 0.5). Invisible: a geiger counter finds it.
     */
    public static final AnomalyType RADIATION_FIELD = new AnomalyType("Radiation Field",
            new FieldTuning(15.0D, 0.04D, 0.5D, true),
            new AnomalyLook(Style.ELECTRIC, 1.0F, 0.0D, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0.0D, 0.0D,
                    0xFFFFFF, 0.0F, 0, 0.0F, 1.0F, null, null, 1.0F, 1.0F),
            WFDamageTypes.RADIATION,
            new AnomalySounds(null, null, 0, null, null, null));

    /**
     * {@code zone_field_thermal_average}: 15 m of searing air, 0.33 a second at the middle (burn), 46% of it at the
     * rim (attenuation 0.54); a faint shimmer and smoke, crackling fire.
     */
    public static final AnomalyType THERMAL_FIELD = new AnomalyType("Thermal Field",
            new FieldTuning(15.0D, 0.33D, 0.54D, false),
            new AnomalyLook(Style.FIRE, 6.0F, 1.0D, 0.004F, 0.2F, 0.0F, 0.0F, 0.6F, 0, 0.0D, 0.0D,
                    0xFFD6AD, 0.0F, 0, 0.0F, 1.0F, null, null, 1.0F, 1.0F),
            WFDamageTypes.FIRE,
            new AnomalySounds(() -> WFSounds.ANOMALY_FIRE2.get(), null, 0, null, null, null));

    /**
     * {@code zone_field_acidic_average}: 15 m of caustic air, 0.17 a second at the middle (chemical), 54% of it at the
     * rim (attenuation 0.46); green specks drifting up, buzzing.
     */
    public static final AnomalyType ACID_FIELD = new AnomalyType("Acid Field",
            new FieldTuning(15.0D, 0.17D, 0.46D, false),
            new AnomalyLook(Style.ACID, 10.0F, 0.0D, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0, 0.0D, 0.0D,
                    0xA6FFD1, 0.0F, 0, 0.0F, 1.0F, null, null, 1.0F, 1.0F),
            WFDamageTypes.ACID,
            new AnomalySounds(() -> WFSounds.ANOMALY_BUZZ_IDLE.get(), null, 0, null, null, null));

    /**
     * No-gravity ({@code CNoGravityZone}, which no STALKER level places): whatever comes inside floats
     * ({@link Quirk#WEIGHTLESS}) until it drifts out; harmless but for the fall after.
     */
    public static final AnomalyType NO_GRAVITY = new AnomalyType("No-Gravity",
            new FieldTuning(5.0D, 0.0D, 0.0D, false),
            new AnomalyLook(Style.GRAVITY, 2.5F, 1.2D, 0.008F, 0.15F, 0.0F, 0.0F, 1.5F, 0, 0.02D, 0.0D,
                    0xDCFFCB, 0.0F, 0, 0.0F, 1.0F, null, null, 1.0F, 1.0F),
            WFDamageTypes.GRAVITY_ANOMALY,
            new AnomalySounds(() -> WFSounds.ANOMALY_GRAVI_IDLE00.get(), null, 0, null, null, null),
            Quirk.WEIGHTLESS);
}
