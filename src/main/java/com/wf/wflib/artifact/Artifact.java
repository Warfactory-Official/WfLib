package com.wf.wflib.artifact;

import com.wf.wflib.WFLib;
import com.wf.wflib.anomaly.site.AnomalyMix.Element;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One kind of STALKER artefact, as GAMMA leaves it (Artefacts Reinvention's last word on each section, resolved over
 * the base game and the mods in GAMMA's order): what it does worn, how it looks, where it grows and what finds it.
 *
 * @param id        item id
 * @param model     GemRender model, centred on the artefact; its parts on texture slots
 * @param slots     slot -> texture; slots named {@code glow*} are unlit and bloom, the rest lit
 * @param arcs      the {@code glow} slot is electric arcs: they crawl along their shells and writhe; else it breathes
 * @param core      above 0: {@code glow} is a sprite this wide (m) at the centre, turned to the viewer and spinning,
 *                  not the model's glow meshes (flat crossed planes read as a disc, or a star edge-on)
 * @param immunity  each hit kind's share taken away while worn ({@code hit_absorbation_sect}: + resists, - worsens)
 * @param health    health_restore_speed: share of full health per game second (X-Ray conditions run on game time:
 *                  {@link #TIME_FACTOR}); negative drains
 * @param power     power_restore_speed: share of full stamina per game second (WFWeight), negative slows
 * @param bleeding  bleeding_restore_speed: wounds close by this share a game second (WFMedical's clotting)
 * @param radShield radiation_immunity ({@code hit_absorbation_sect}): radiation units taken off incoming dose per game
 *                  second while any comes in; negative adds. X-Ray subtracts it per radiation hit, not per second
 * @param radiation radiation_restore_speed: radiation units per game second, positive irradiates
 * @param carry     additional_inventory_weight: kg onto carry capacity while worn (WFWeight), negative lowers it
 * @param satiety   satiety_restore_speed: share of a full stomach per game second; only negative applied (starves)
 * @param bloom     strength of the bloom off its glowing parts, 0..1 (each glow texture blurred:
 *                  {@code <texture>_bloom.png})
 * @param lightRgb  trail_light_color, while held or shown
 * @param lightRange trail_light_range, blocks
 * @param idleFx    {@code particles}: X-Ray particle group (WFLib {@code xray/particles/}) looped on it lying in the
 *                  world; null: none
 * @param showFx    {@code det_show_particles}: group played once as a detector shows it
 * @param detectRank the lowest detector that finds it (1 Echo, 2 Bear, 3 Veles: GAMMA's detector lists)
 * @param element   the anomalies it grows in (GAMMA's {@code af_class_*} spawn groups)
 * @param minTier   the lowest anomaly site tier it grows at (GAMMA's south 1, mid 2, north 3)
 */
public record Artifact(String id, ResourceLocation model, Map<String, ResourceLocation> slots, boolean arcs, float core,
                       Map<Hit, Float> immunity, float health, float power, float bleeding, float radShield,
                       float radiation, float carry, float satiety, float bloom, int lightRgb,
                       float lightRange, @Nullable String idleFx, String showFx, int detectRank, Element element,
                       int minTier) {

    /** What a hit can be, for immunities: STALKER's {@code *_immunity} kinds that this world has a damage for. */
    public enum Hit {
        /** shock_immunity: electricity (anomalies, lightning). */
        SHOCK,
        /** burn_immunity: fire, thermal anomalies. */
        BURN,
        /** strike_immunity: blunt blows (gravitational anomalies, falls, hitting walls). */
        STRIKE,
        /** wound_immunity (STALKER's "rupture"): claws, teeth, blades. */
        WOUND,
        /** fire_wound_immunity: bullets and other projectiles. */
        BULLET,
        /** explosion_immunity. */
        EXPLOSION,
        /** chemical_burn_immunity: acid, gas. */
        CHEMICAL,
        /** telepatic_immunity: psy, which here is stress (WFMedical): its gain cut by this share. */
        PSY
    }

    /** GAMMA's alife time_factor: game seconds per real second, which its restore speeds are per. */
    public static final float TIME_FACTOR = 6.0F;
    /** RAD per STALKER radiation unit: 1 kills there, ~1000 RAD here. */
    public static final float RAD_PER_UNIT = 1000.0F;

    public float immunity(Hit hit) {
        return immunity.getOrDefault(hit, 0.0F);
    }

    /** Unlit: {@code glow*} slots, the blended ones ({@code glow}, {@code glow2}) and solid ones ({@code glowsolid*}). */
    public static boolean glowSlot(String slot) {
        return slot.startsWith("glow");
    }

    /**
     * Blooms: blended glows only. A solid glow ({@code glowsolid*}: STALKER's opaque self-lit parts) would have its
     * blurred copy drawn opaque over it, at the same depth.
     */
    public static boolean bloomSlot(String slot) {
        return glowSlot(slot) && !slot.startsWith("glowsolid");
    }

    /** A glow slot's texture blurred: what its bloom draws ({@code <texture>_bloom.png}). */
    public static ResourceLocation bloomOf(ResourceLocation texture) {
        return ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),
                texture.getPath().replace(".png", "_bloom.png"));
    }

    /** Health per real second while worn, in health points of {@code maxHealth} (negative: a drain). */
    public float healthPerSecond(float maxHealth) {
        return health * TIME_FACTOR * maxHealth;
    }

    /** Stamina back per tick while worn, as a share of the most (WFWeight's stamina modifiers). */
    public double staminaPerTick() {
        return power * TIME_FACTOR / 20.0D;
    }

    /** Wound closing per real second while worn (WFMedical's clotting strength scales off it). */
    public float bleedingPerSecond() {
        return bleeding * TIME_FACTOR;
    }

    /** RAD/s kept off incoming dose while worn (negative: added while dosed). */
    public float radShieldPerSecond() {
        return radShield * TIME_FACTOR * RAD_PER_UNIT;
    }

    /** RAD/s dosed while worn (negative: cleared). */
    public float radiationPerSecond() {
        return radiation * TIME_FACTOR * RAD_PER_UNIT;
    }

    /** Food points per second while worn (a full stomach: 20). */
    public float foodPerSecond() {
        return satiety * TIME_FACTOR * 20.0F;
    }

    private static ResourceLocation wflib(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    private static final class Builder {
        private final String id;
        private final Map<String, ResourceLocation> slots = new LinkedHashMap<>();
        private final Map<Hit, Float> immunity = new EnumMap<>(Hit.class);
        private boolean arcs;
        private float core;
        private float health;
        private float power;
        private float bleeding;
        private float radShield;
        private float radiation;
        private float carry;
        private float satiety;
        private float bloom = 0.8F;
        private int lightRgb = 0xFFFFFF;
        private float lightRange = 2.0F;
        @Nullable
        private String idleFx;
        private String showFx;
        private int detectRank = 1;
        private Element element = Element.EXOTIC;
        private int minTier = 1;

        private Builder(String id) {
            this.id = id;
        }

        /** {@code slot} on {@code textures/artifact/<texture>.png}. */
        Builder slot(String slot, String texture) {
            slots.put(slot, wflib("textures/artifact/" + texture + ".png"));
            return this;
        }

        Builder arcs() {
            arcs = true;
            return this;
        }

        Builder core(float width) {
            core = width;
            return this;
        }

        Builder resists(Hit hit, float share) {
            immunity.put(hit, share);
            return this;
        }

        Builder health(float v) {
            health = v;
            return this;
        }

        Builder power(float v) {
            power = v;
            return this;
        }

        Builder bleeding(float v) {
            bleeding = v;
            return this;
        }

        Builder radShield(float v) {
            radShield = v;
            return this;
        }

        Builder radiation(float v) {
            radiation = v;
            return this;
        }

        Builder carry(float kg) {
            carry = kg;
            return this;
        }

        Builder satiety(float v) {
            satiety = v;
            return this;
        }

        Builder bloom(float v) {
            bloom = v;
            return this;
        }

        Builder light(int rgb, float range) {
            lightRgb = rgb;
            lightRange = range;
            return this;
        }

        Builder fx(@Nullable String idle, String show) {
            idleFx = idle;
            showFx = show;
            return this;
        }

        Builder found(int rank, Element grows, int tier) {
            detectRank = rank;
            element = grows;
            minTier = tier;
            return this;
        }

        Artifact build() {
            return new Artifact(id, wflib("models/artifact/" + id + ".glb"), Map.copyOf(slots), arcs, core,
                    Map.copyOf(immunity), health, power, bleeding, radShield, radiation, carry, satiety, bloom,
                    lightRgb, lightRange, idleFx, Objects.requireNonNull(showFx, id), detectRank, element, minTier);
        }
    }

    private static Builder artifact(String id) {
        return new Builder(id);
    }

    // GAMMA's numbers throughout; lights are trail_light_color (0..1 per channel) as RGB.

    /** {@code af_dummy_battery}: a charged lattice from Electros. */
    public static final Artifact BATTERY = artifact("battery").slot("body", "sopli2").slot("glow", "battery_lightning")
            .arcs().resists(Hit.SHOCK, 0.1212F).resists(Hit.BURN, -0.095F).resists(Hit.STRIKE, -0.27F).power(0.00066F)
            .fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x99B3E6, 2.0F).found(1, Element.ELECTRIC, 1).build();
    /** {@code af_soul}: a green spiral glowing in a veined shell. */
    public static final Artifact SOUL = artifact("soul").slot("body", "sopli2").slot("glow", "soul_glow").core(0.30F)
            .bloom(0.9F).health(0.0003F).power(-0.00035F).radShield(0.00043F).fx("artefact/af_acidic_idle", "artefact/af_acidic_show").light(0x80B333, 2.0F).found(1, Element.GRAVITY, 1).build();

    /** {@code af_cristall} (Crystal): burning orange crystal from thermal anomalies. */
    public static final Artifact CRYSTAL = artifact("crystal").slot("glowsolid", "artifact_lavahell1")
            .resists(Hit.BURN, 0.24375F).resists(Hit.SHOCK, 0.1818F).resists(Hit.CHEMICAL, -0.27F)
            .fx("artefact/af_thermal_idle", "artefact/af_thermal_show").light(0xE66633, 2.0F).found(2, Element.FIRE, 2).build();
    /** {@code af_itcher} (Itcher). */
    public static final Artifact ITCHER = artifact("itcher").slot("body", "artefact_itcher")
            .resists(Hit.BURN, 0.1625F).resists(Hit.EXPLOSION, 0.47F).resists(Hit.CHEMICAL, -0.27F)
            .fx("artefact/af_thermal_idle", "artefact/af_thermal_show").light(0x666600, 2.0F).found(1, Element.FIRE, 1).build();
    /** {@code af_sponge} (Sponge). */
    public static final Artifact SPONGE = artifact("sponge").slot("body", "artefact_sponge")
            .resists(Hit.STRIKE, 0.45F).resists(Hit.EXPLOSION, 0.47F)
            .fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x666600, 2.0F).found(1, Element.CHEMICAL, 2).build();
    /** {@code af_fireball} (Fireball): a molten ball in a heat haze. */
    public static final Artifact FIREBALL = artifact("fireball").slot("body", "artifact_lavahell1")
            .slot("glowsolid", "artifact_loner_lava").slot("glowsolid2", "artifact_loner_lavahell")
            .resists(Hit.BURN, 0.41F).resists(Hit.SHOCK, -0.22725F).resists(Hit.STRIKE, -0.74F)
            .resists(Hit.WOUND, 0.285F).resists(Hit.BULLET, 0.19486F)
            .fx("artefact/af_thermal_idle", "artefact/af_thermal_show").light(0xE66633, 2.0F).found(3, Element.FIRE, 3).build();
    /** {@code af_electra_sparkler} (Sparkler). */
    public static final Artifact SPARKLER = artifact("sparkler").slot("body", "artifact_sopli")
            .resists(Hit.SHOCK, 0.0606F).resists(Hit.WOUND, 0.1425F).resists(Hit.CHEMICAL, -0.27F)
            .fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x99B3E6, 2.0F).found(1, Element.ELECTRIC, 1).build();
    /** {@code af_dummy_glassbeads} (Glass Beads). */
    public static final Artifact GLASS_BEADS = artifact("glass_beads").slot("glowsolid", "artifact_lava")
            .resists(Hit.BURN, 0.08125F).resists(Hit.SHOCK, -0.135F).bleeding(0.00408F)
            .fx("artefact/af_thermal_idle", "artefact/af_thermal_show").light(0xE66633, 2.0F).found(1, Element.FIRE, 1).build();
    /** {@code af_electra_moonlight} (Moonlight). */
    public static final Artifact MOONLIGHT = artifact("moonlight").slot("body", "artifact_hell1_solid")
            .slot("glow", "artifact_glow").slot("glow2", "artifact_lighting")
            .resists(Hit.SHOCK, 0.1212F).resists(Hit.PSY, 0.165F)
            .fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x99B3E6, 2.0F).found(2, Element.ELECTRIC, 2).build();
    /** {@code af_electra_flash} (Flash). */
    public static final Artifact FLASH = artifact("flash_artifact").slot("body", "artifact_fire").slot("glow", "artifact_glow")
            .slot("glow2", "artifact_fire_light").resists(Hit.SHOCK, 0.303F).resists(Hit.PSY, 0.275F)
            .health(-0.0003F).power(-0.00137F).fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x99B3E6, 2.0F).found(3, Element.ELECTRIC, 3).build();
    /** {@code af_ice} (Ice). */
    public static final Artifact ICE = artifact("ice").slot("glowsolid", "artifact_hell1").slot("glowsolid2", "artifact_sopli2")
            .resists(Hit.PSY, -0.13F).health(-0.0006F).power(0.0017F)
            .fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x99B3E6, 2.0F).found(3, Element.GRAVITY, 3).build();
    /** {@code af_ring} (Ring). */
    public static final Artifact RING = artifact("ring").slot("body", "artefact_ring")
            .resists(Hit.SHOCK, 0.1818F).resists(Hit.PSY, -0.13F).power(0.001F)
            .fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x666600, 2.0F).found(2, Element.ELECTRIC, 2).build();
    /** {@code af_dummy_dummy} (Dummy). */
    public static final Artifact DUMMY = artifact("dummy").slot("body", "artifact_sopli")
            .slot("glow", "artifact_anim_lightning").slot("glowsolid2", "artifact_sopli2").arcs()
            .resists(Hit.PSY, 0.165F).health(-0.0006F).fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x99B3E6, 2.0F).found(2, Element.GRAVITY, 2).build();
    /** {@code af_cristall_flower} (Stone Flower). */
    public static final Artifact STONE_FLOWER = artifact("stone_flower").slot("body", "artifact_myasorubka2")
            .slot("glowsolid", "artifact_hell1").resists(Hit.BURN, -0.176F).resists(Hit.CHEMICAL, 0.24888F)
            .resists(Hit.PSY, 0.11F).fx("artefact/af_gravi_idle", "artefact/af_gravi_show").light(0x666600, 2.0F).found(1, Element.GRAVITY, 1).build();

    /** {@code af_blood} (Stone Blood). */
    public static final Artifact STONE_BLOOD = artifact("stone_blood").slot("glowsolid", "artifact_blood")
            .resists(Hit.CHEMICAL, 0.24888F).resists(Hit.PSY, -0.0694445F).bleeding(0.004082F)
            .fx("artefact/af_acidic_idle", "artefact/af_acidic_show").light(0x80B333, 2.0F).found(1, Element.CHEMICAL, 1).build();
    /** {@code af_lobster_eyes} (Lobster Eyes). */
    public static final Artifact LOBSTER_EYES = artifact("lobster_eyes").slot("glowsolid", "artefact_lobster_eyes")
            .slot("body", "artefact_lobster_eyes").resists(Hit.BURN, 0.1625F).resists(Hit.EXPLOSION, -0.54F)
            .resists(Hit.PSY, 0.165F).radShield(-0.000475F).fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x666600, 2.0F).found(1, Element.FIRE, 1).build();
    /** {@code af_medusa} (Jellyfish). */
    public static final Artifact JELLYFISH = artifact("jellyfish").slot("body", "artifact_anim_dist")
            .slot("glowsolid", "artifact_myasorubka3").resists(Hit.WOUND, 0.07125F).resists(Hit.BULLET, 0.097431F)
            .resists(Hit.EXPLOSION, -0.2833375F).resists(Hit.PSY, -0.0694445F)
            .fx("artefact/af_gravi_idle", "artefact/af_gravi_show").light(0x666600, 2.0F).found(1, Element.GRAVITY, 1).build();
    /** {@code af_mincer_meat} (Meat Chunk). */
    public static final Artifact MEAT_CHUNK = artifact("meat_chunk").slot("glowsolid", "artifact_mincer_meat")
            .resists(Hit.BULLET, 0.194862F).resists(Hit.CHEMICAL, 0.24888F).bleeding(-0.002045F)
            .fx("artefact/af_acidic_idle", "artefact/af_acidic_show").light(0x80B333, 2.0F).found(1, Element.CHEMICAL, 1).build();
    /** {@code af_pin} (Pin). */
    public static final Artifact PIN = artifact("pin").slot("body", "artefact_pin").resists(Hit.BURN, 0.1625F)
            .resists(Hit.CHEMICAL, -0.27F).resists(Hit.PSY, 0.11F).radShield(-0.000475F)
            .fx("artefact/af_thermal_idle", "artefact/af_thermal_show").light(0x666600, 2.0F).found(1, Element.FIRE, 1).build();
    /** {@code af_baloon} (Bubble). */
    public static final Artifact BUBBLE = artifact("bubble").slot("glowsolid", "artifact_mp_green")
            .resists(Hit.BURN, 0.33F).resists(Hit.CHEMICAL, 0.77F).health(-0.0006F).radShield(0.00129F)
            .fx("artefact/af_acidic_idle", "artefact/af_acidic_show").light(0x80B333, 2.0F).found(3, Element.CHEMICAL, 3).build();
    /** {@code af_black_spray} (Black Spray). */
    public static final Artifact BLACK_SPRAY = artifact("black_spray").slot("body", "artefact_black_spray")
            .slot("glowsolid", "artefact_black_spray").resists(Hit.STRIKE, 0.93F).resists(Hit.WOUND, -0.24F)
            .resists(Hit.PSY, 0.33F).health(0.0003F).bleeding(-0.004082F).radShield(0.00043F)
            .fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0xE66633, 2.0F).found(3, Element.GRAVITY, 3).build();
    /** {@code af_bracelet} (Bracelet). */
    public static final Artifact BRACELET = artifact("bracelet").slot("glowsolid", "artefact_bracelet")
            .resists(Hit.EXPLOSION, -0.54F).resists(Hit.CHEMICAL, 0.37332F).resists(Hit.PSY, 0.165F)
            .power(0.00066F).bleeding(-0.002041F).fx("artefact/af_acidic_idle", "artefact/af_acidic_show").light(0x666600, 1.5F).found(2, Element.CHEMICAL, 2).build();
    /** {@code af_eye} (Eye). */
    public static final Artifact EYE = artifact("eye").slot("glowsolid", "artifact_eye").resists(Hit.SHOCK, -0.135F)
            .resists(Hit.CHEMICAL, -0.27F).resists(Hit.PSY, 0.11F).bleeding(0.00612F)
            .fx("artefact/af_thermal_idle", "artefact/af_thermal_show").light(0xE66633, 2.0F).found(2, Element.GRAVITY, 2).build();
    /** {@code af_fire} (Flame). */
    public static final Artifact FLAME = artifact("flame").slot("body", "artifact_lavahell1")
            .slot("glow", "pfx_anomaly_1").slot("glowsolid2", "artifact_lava").resists(Hit.BURN, 0.1625F)
            .resists(Hit.WOUND, 0.285F).resists(Hit.CHEMICAL, -0.46665F).resists(Hit.PSY, 0.11F).power(-0.0007F)
            .bleeding(0.008157F).radShield(-0.001425F).fx("artefact/af_thermal_idle", "artefact/af_thermal_show").light(0xE66633, 2.0F).found(3, Element.FIRE, 3).build();
    /** {@code af_fuzz_kolobok} (Kolobok). */
    public static final Artifact KOLOBOK = artifact("kolobok").slot("body", "artifact_sopli")
            .slot("glowsolid", "artifact_mp_green").resists(Hit.BULLET, -0.125F).health(0.0006F).power(-0.0007F)
            .radShield(0.00086F).fx("artefact/af_acidic_idle", "artefact/af_acidic_show").light(0x80B333, 2.0F).found(2, Element.CHEMICAL, 2).build();
    /** {@code af_glass} (Firefly). */
    public static final Artifact FIREFLY = artifact("firefly").slot("glow", "artifact_fire_light")
            .slot("body", "artifact_myasorubka2").slot("glow2", "pfx_anomaly_4").resists(Hit.BULLET, -0.24F)
            .resists(Hit.PSY, -0.0694445F).health(0.0009F).power(-0.00105F).radShield(0.0013F)
            .fx("artefact/af_acidic_idle", "artefact/af_acidic_show").light(0x80B333, 2.0F).found(3, Element.CHEMICAL, 3).build();
    /** {@code af_vyvert} (Wrenched). */
    public static final Artifact WRENCHED = artifact("wrenched").slot("body", "artifact_myasorubka3_solid")
            .resists(Hit.WOUND, 0.21375F).resists(Hit.BULLET, 0.097431F).resists(Hit.CHEMICAL, 0.24888F)
            .bleeding(-0.004082F).radShield(-0.000475F).fx("artefact/af_gravi_idle", "artefact/af_gravi_show").light(0x666600, 2.0F).found(2, Element.GRAVITY, 2).build();
    /** {@code af_dummy_pellicle} (Pellicle): GAMMA spawns it once; here a rare tier-4 find. */
    public static final Artifact PELLICLE = artifact("pellicle").slot("body", "pfx_anomaly_19")
            .resists(Hit.CHEMICAL, 0.125F).fx("anomaly2/artefact/artefact_ameba", "anomaly2/plasma_generator_death")
            .found(3, Element.CHEMICAL, 4).build();
    /** {@code af_oasis_heart} (Heart of the Oasis): GAMMA spawns it once; here a rare tier-4 find. */
    public static final Artifact OASIS_HEART = artifact("oasis_heart").slot("body", "artifact_hell1")
            .slot("body2", "af_cristall_jup").slot("glow", "pfx_anomaly_4").resists(Hit.BURN, -0.3046875F)
            .resists(Hit.SHOCK, -0.22725F).resists(Hit.BULLET, -0.35F).resists(Hit.CHEMICAL, -0.46665F)
            .health(0.0012F).power(0.00099F).bleeding(0.00816F).radShield(0.00173F)
            .fx("anomaly2/artefact/artefact_ameba", "anomaly2/plasma_generator_death").light(0xFFFFBF, 2.0F).found(1, Element.EXOTIC, 4).build();


    /** {@code af_compass} (Compass). */
    public static final Artifact COMPASS = artifact("compass").slot("body", "artifact_simple2")
            .slot("glowsolid", "artifact_sopli2").resists(Hit.STRIKE, 0.69F).resists(Hit.WOUND, 0.21375F)
            .resists(Hit.BULLET, 0.52F).resists(Hit.EXPLOSION, 0.47F).health(-0.0006F).power(-0.0007F)
            .bleeding(-0.004082F).carry(-12F).radShield(-0.00185F).fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0xFFFFBF, 2F).found(1, Element.EXOTIC, 4)
            .build();
    /** {@code af_death_lamp} (Death Lamp). */
    public static final Artifact DEATH_LAMP = artifact("death_lamp").slot("glow", "artefact_death_skull_glow")
            .slot("body", "artefact_death_lamp").slot("glowsolid2", "artefact_death_skull_glow2")
            .slot("body2", "artefact_death_skull").resists(Hit.PSY, -0.0544F).power(-0.00088F).radiation(0.0006F)
            .satiety(-0.1F).radShield(-0.002F).fx("artefact/af_gravi_idle", "artefact/af_gravi_show").light(0x666600, 2F).found(3, Element.CHEMICAL, 3).build();
    /** {@code af_drops} (Droplets). */
    public static final Artifact DROPLETS = artifact("droplets").slot("body", "artifact_sopli2").power(-0.00125F)
            .radiation(-0.00125F).fx("anomaly2/artefact/artefact_zharka", "anomaly2/plasma_generator_death").light(0x99B280, 0.5F).found(1, Element.FIRE, 1).build();
    /** {@code af_empty} (Empty). */
    public static final Artifact EMPTY = artifact("empty").slot("body", "artefact_empty").resists(Hit.STRIKE, 0.69F)
            .resists(Hit.BULLET, 0.194862F).resists(Hit.EXPLOSION, 0.47F).resists(Hit.PSY, -0.0694445F).power(-0.0007F)
            .carry(-4F).radShield(-0.000475F).fx("artefact/af_gravi_idle", "artefact/af_gravi_show").light(0x666600, 2F).found(2, Element.GRAVITY, 2).build();
    /** {@code af_full_empty} (Full Empty). */
    public static final Artifact FULL_EMPTY = artifact("full_empty").slot("body", "artefact_full_empty_solid")
            .slot("glow", "artefact_full_empty").resists(Hit.WOUND, 0.07125F).resists(Hit.BULLET, 0.4F)
            .resists(Hit.EXPLOSION, 0.47F).resists(Hit.PSY, -0.0694445F).bleeding(-0.004082F).carry(-12F)
            .radShield(-0.001425F).fx("artefact/af_gravi_idle", "artefact/af_gravi_show").light(0x666600, 2F).found(3, Element.GRAVITY, 3).build();
    /** {@code af_gold_fish} (Goldfish). */
    public static final Artifact GOLDFISH = artifact("goldfish").slot("body", "artifact_anim_dist")
            .slot("glow", "artifact_myasorubka3").resists(Hit.BURN, -0.18F).resists(Hit.BULLET, 0.097431F)
            .resists(Hit.EXPLOSION, 0.97F).health(-0.0006F).carry(21F).radShield(0.00172F).fx("artefact/af_gravi_idle", "artefact/af_gravi_show").light(0x666600, 2F)
            .found(3, Element.GRAVITY, 2).build();
    /** {@code af_gravi} (Gravi). */
    public static final Artifact GRAVI = artifact("gravi").slot("body", "artifact_myasorubka3_solid")
            .resists(Hit.BURN, -0.18F).resists(Hit.BULLET, -0.125F).resists(Hit.EXPLOSION, 0.47F).carry(14F)
            .radShield(0.00086F).fx("artefact/af_gravi_idle", "artefact/af_gravi_show").light(0x666600, 2F).found(2, Element.GRAVITY, 2).build();
    /** {@code af_monolith} (Volat Emerald). */
    public static final Artifact VOLAT_EMERALD = artifact("volat_emerald").slot("glow", "artifact_monolith_lava")
            .slot("body", "artifact_monohell1_00").slot("glow2", "artifact_monolith_lavahell").resists(Hit.BURN, 0.41F)
            .resists(Hit.SHOCK, 0.303F).resists(Hit.CHEMICAL, 0.6222F).resists(Hit.PSY, 0.22F).carry(10F)
            .radShield(-0.0023F).fx("artefact/af_electra_idle", "artefact/af_electra_show").light(0x80B233, 2F).found(1, Element.EXOTIC, 4).build();
    /** {@code af_night_star} (Nightstar). */
    public static final Artifact NIGHTSTAR = artifact("nightstar").slot("body", "artifact_myasorubka3_solid")
            .slot("glowsolid", "artifact_myasorubka2").resists(Hit.BURN, -0.095F).resists(Hit.EXPLOSION, 0.22667F)
            .carry(6F).radShield(0.00043F).fx("artefact/af_gravi_idle", "artefact/af_gravi_show").light(0x666600, 2F).found(1, Element.GRAVITY, 1).build();
    /** {@code af_rusty_kristall} (Crystal Thorn). */
    public static final Artifact CRYSTAL_THORN = artifact("crystal_thorn").slot("body", "artifact_liana2")
            .slot("body2", "pfx_anomaly_vegetation").radiation(-0.0025F).bleeding(-0.00375F).fx(null, "anomaly2/plasma_generator_death").light(0x666666, 2F)
            .found(1, Element.CHEMICAL, 1).build();
    /** {@code af_rusty_sea} (Sea Urchin). */
    public static final Artifact SEA_URCHIN = artifact("sea_urchin").slot("body", "artifact_myasorubka3_solid")
            .slot("body2", "artifact_simple3").radiation(-0.00375F).bleeding(-0.00375F).fx(null, "anomaly2/plasma_generator_death").light(0x666666, 2F)
            .found(1, Element.CHEMICAL, 1).build();
    /** {@code af_rusty_thorn} (Thorn). */
    public static final Artifact THORN = artifact("thorn").slot("body", "artifact_liana2")
            .slot("body2", "pfx_anomaly_vegetation").radiation(-0.00125F).bleeding(-0.00375F).fx(null, "anomaly2/plasma_generator_death").light(0x666666, 2F)
            .found(1, Element.CHEMICAL, 1).build();
    /** {@code af_dummy_spring} (Spring). */
    public static final Artifact SPRING = artifact("spring").slot("body", "artifact_pustishka")
            .slot("body2", "artifact_sopli").resists(Hit.STRIKE, -0.0375F).power(0.00375F).radiation(0.000375F)
            .fx(null, "anomaly2/plasma_generator_death").light(0x666666, 2F).found(1, Element.GRAVITY, 1).build();

    // Perk-Based Artefacts (GAMMA): no stats of their own; perks not ported. Element by trail light colour.
    /** {@code af_ball} (Ball). */
    public static final Artifact BALL = artifact("ball").slot("body", "brown_skin").slot("body2", "blue_insides")
            .slot("glowsolid", "blue_insides").fx(null, "artefact/af_thermal_show").light(0xE66633, 1F).found(1, Element.FIRE, 1).build();
    /** {@code af_bat} (Bat). */
    public static final Artifact BAT = artifact("bat").slot("body", "artifact_val024")
            .slot("glowsolid", "artifact_val000").fx(null, "artefact/af_electra_show").light(0xB266E6, 1F).found(1, Element.EXOTIC, 1).build();
    /** {@code af_black_angel} (Black Angel). */
    public static final Artifact BLACK_ANGEL = artifact("black_angel").slot("body", "artifact_val033")
            .fx(null, "artefact/af_electra_show").light(0xCCB2E6, 1F).found(1, Element.EXOTIC, 1).build();
    /** {@code af_cell} (Cell). */
    public static final Artifact CELL = artifact("cell").slot("body", "artifact_val029")
            .slot("glowsolid", "artifact_val013").fx(null, "artefact/af_thermal_show").light(0x666666, 1F).found(1, Element.GRAVITY, 2).build();
    /** {@code af_chelust} (Chain). */
    public static final Artifact CHAIN = artifact("chain").slot("glow", "chelust").fx(null, "artefact/af_thermal_show").light(0xE66633, 1F)
            .found(1, Element.FIRE, 1).build();
    /** {@code af_cocoon} (Cocoon). */
    public static final Artifact COCOON = artifact("cocoon").slot("body", "sap_paytina").slot("body2", "sap_paytina_01")
            .slot("body3", "sap_paytina_02").slot("body4", "sap_paytina_03").slot("body5", "sap_paytina_def")
            .slot("body6", "sap_paytina_04").fx(null, "artefact/af_thermal_show").light(0x4C4C4C, 1F).found(1, Element.GRAVITY, 1).build();
    /** {@code af_dragon_eye} (Dragon's Eye). */
    public static final Artifact DRAGON_EYE = artifact("dragon_eye").slot("body", "artifact_lavahell1")
            .slot("glow", "pfx_anomaly_6").slot("glowsolid2", "artifact_lava").fx(null, "artefact/af_thermal_show").light(0xE66633, 1F)
            .found(1, Element.FIRE, 2).build();
    /** {@code af_elektron} (Electron). */
    public static final Artifact ELECTRON = artifact("electron").slot("body", "elektron").fx(null, "artefact/af_electra_show").light(0x99B2E6, 1F)
            .found(1, Element.ELECTRIC, 2).build();
    /** {@code af_fonar} (Lamp). */
    public static final Artifact LAMP = artifact("lamp").slot("body", "fonar").slot("glowsolid", "fonar_self")
            .fx(null, "artefact/af_electra_show").light(0x99B2E6, 1F).found(1, Element.ELECTRIC, 1).build();
    /** {@code af_fountain} (Fountain). */
    public static final Artifact FOUNTAIN = artifact("fountain").slot("body", "artifact_val025").fx(null, "artefact/af_thermal_show").light(0xE66633, 1F)
            .found(1, Element.FIRE, 1).build();
    /** {@code af_generator} (Generator). */
    public static final Artifact GENERATOR = artifact("generator").slot("glow", "sap_generator_00")
            .slot("body", "sap_generator").fx(null, "artefact/af_electra_show").light(0x99B2E6, 2F).found(1, Element.ELECTRIC, 1).build();
    /** {@code af_kogot} (Claw). */
    public static final Artifact CLAW = artifact("claw").slot("body", "kogot").slot("glowsolid", "kogot_inner")
            .fx(null, "artefact/af_electra_show").light(0x99B2E6, 1F).found(1, Element.ELECTRIC, 2).build();
    /** {@code af_lighthouse} (Beacon). */
    public static final Artifact BEACON = artifact("beacon").slot("body", "artifact_val031")
            .slot("glow", "artifact_val005").fx(null, "artefact/af_electra_show").light(0x8099E6, 1F).found(1, Element.ELECTRIC, 2).build();
    /** {@code af_medallion} (Medal). */
    public static final Artifact MEDAL = artifact("medal").slot("body", "sap_medal_00").slot("body2", "sap_medal")
            .slot("glow", "sap_medal_01").fx(null, "artefact/af_acidic_show").light(0x80B233, 1F).found(1, Element.CHEMICAL, 2).build();
    /** {@code af_moh} (Moss). */
    public static final Artifact MOSS = artifact("moss").slot("body", "mox").slot("glowsolid", "artifact_sopli")
            .fx(null, "artefact/af_acidic_show").light(0x80B233, 1F).found(1, Element.CHEMICAL, 1).build();
    /** {@code af_peas} (Moon). */
    public static final Artifact MOON = artifact("moon").slot("glowsolid", "artifact_val022")
            .slot("body", "artifact_val023").fx(null, "artefact/af_thermal_show").light(0x808080, 2F).found(1, Element.GRAVITY, 1).build();
    /** {@code af_repei} (Thistle). */
    public static final Artifact THISTLE = artifact("thistle").slot("body", "repei_1")
            .slot("body2", "artifact_lavahell1_solid").fx(null, "artefact/af_thermal_show").light(0xE66633, 1F).found(1, Element.FIRE, 2).build();
    /** {@code af_sandstone} (Sandstone). */
    public static final Artifact SANDSTONE = artifact("sandstone").slot("body", "sap_sand").slot("glow", "sap_gubka")
            .fx(null, "artefact/af_acidic_show").light(0x80B233, 1F).found(1, Element.CHEMICAL, 1).build();
    /** {@code af_serofim} (Seraphim). */
    public static final Artifact SERAPHIM = artifact("seraphim").slot("body", "sap_serafim_high")
            .slot("glow", "sap_serafim_low").slot("body2", "sap_serafim_medium").fx(null, "artefact/af_electra_show").light(0x99B2E6, 1F)
            .found(1, Element.ELECTRIC, 2).build();
    /** {@code af_skull_miser} (Miser's Skull). */
    public static final Artifact MISERS_SKULL = artifact("misers_skull").slot("glowsolid", "blue_insides")
            .slot("body", "bright_red_skin").fx(null, "artefact/af_thermal_show").light(0xE66633, 1F).found(1, Element.FIRE, 1).build();
    /** {@code af_spaika} (Spike). */
    public static final Artifact SPIKE = artifact("spike").slot("body", "artifact_val028")
            .slot("body2", "artifact_val027").fx(null, "artefact/af_thermal_show").light(0xE66633, 1F).found(1, Element.FIRE, 1).build();
    /** {@code af_star_phantom} (Phantom Star). */
    public static final Artifact PHANTOM_STAR = artifact("phantom_star").slot("glow", "sap_star_of_ph")
            .fx(null, "artefact/af_electra_show").light(0xCC80E6, 1F).found(1, Element.EXOTIC, 2).build();
    /** {@code af_sun} (Sun). */
    public static final Artifact SUN = artifact("sun").slot("body", "sap_glass").slot("glow", "sap_gubka")
            .fx(null, "artefact/af_thermal_show").light(0x666600, 2F).found(1, Element.FIRE, 2).build();
    /** {@code af_tapeworm} (Tapeworm). */
    public static final Artifact TAPEWORM = artifact("tapeworm").slot("body", "sap_orbital").fx(null, "artefact/af_electra_show").light(0x99B2E6, 1F)
            .found(1, Element.ELECTRIC, 1).build();
    /** {@code af_zhelch} (Scar). */
    public static final Artifact SCAR = artifact("scar").slot("body", "zhelch").slot("glowsolid", "zhelch")
            .fx(null, "artefact/af_thermal_show").light(0xE666B2, 1F).found(1, Element.EXOTIC, 1).build();

    /** GAMMA's one-off artefacts: drawn far less often than the rest. */
    public static final List<Artifact> UNIQUE = List.of(PELLICLE, OASIS_HEART, COMPASS, VOLAT_EMERALD);

    public static final List<Artifact> ALL = List.of(BATTERY, SOUL, CRYSTAL, ITCHER, SPONGE, FIREBALL, SPARKLER,
            GLASS_BEADS, MOONLIGHT, FLASH, ICE, RING, DUMMY, STONE_FLOWER, STONE_BLOOD, LOBSTER_EYES, JELLYFISH,
            MEAT_CHUNK, PIN, BUBBLE, BLACK_SPRAY, BRACELET, EYE, FLAME, KOLOBOK, FIREFLY, WRENCHED, PELLICLE,
            OASIS_HEART, COMPASS, DEATH_LAMP, DROPLETS, EMPTY, FULL_EMPTY, GOLDFISH, GRAVI, VOLAT_EMERALD, NIGHTSTAR,
            CRYSTAL_THORN, SEA_URCHIN, THORN, SPRING, BALL, BAT, BLACK_ANGEL, CELL, CHAIN, COCOON, DRAGON_EYE, ELECTRON,
            LAMP, FOUNTAIN, GENERATOR, CLAW, BEACON, MEDAL, MOSS, MOON, THISTLE, SANDSTONE, SERAPHIM, MISERS_SKULL,
            SPIKE, PHANTOM_STAR, SUN, TAPEWORM, SCAR);
}
