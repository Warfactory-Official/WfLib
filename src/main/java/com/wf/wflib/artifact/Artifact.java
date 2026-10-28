package com.wf.wflib.artifact;

import com.wf.wflib.WFLib;
import com.wf.wflib.anomaly.site.AnomalyMix.Element;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * @param bloom     strength of the bloom off its glowing parts, 0..1 (each glow texture blurred:
 *                  {@code <texture>_bloom.png})
 * @param lightRgb  trail_light_color, while held or shown
 * @param lightRange trail_light_range, blocks
 * @param detectRank the lowest detector that finds it (1 Echo, 2 Bear, 3 Veles: GAMMA's detector lists)
 * @param element   the anomalies it grows in (GAMMA's {@code af_class_*} spawn groups)
 * @param minTier   the lowest anomaly site tier it grows at (GAMMA's south 1, mid 2, north 3)
 */
public record Artifact(String id, ResourceLocation model, Map<String, ResourceLocation> slots, boolean arcs, float core,
                       Map<Hit, Float> immunity, float health, float power, float bleeding, float bloom, int lightRgb,
                       float lightRange, int detectRank, Element element, int minTier) {

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

    public float immunity(Hit hit) {
        return immunity.getOrDefault(hit, 0.0F);
    }

    public static boolean glowSlot(String slot) {
        return slot.startsWith("glow");
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
        private float bloom = 0.8F;
        private int lightRgb = 0xFFFFFF;
        private float lightRange = 2.0F;
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

        Builder bloom(float v) {
            bloom = v;
            return this;
        }

        Builder light(int rgb, float range) {
            lightRgb = rgb;
            lightRange = range;
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
                    Map.copyOf(immunity), health, power, bleeding, bloom, lightRgb, lightRange, detectRank, element,
                    minTier);
        }
    }

    private static Builder artifact(String id) {
        return new Builder(id);
    }

    // GAMMA's numbers throughout; lights are trail_light_color (0..1 per channel) as RGB.

    /** {@code af_dummy_battery}: a charged lattice from Electros. */
    public static final Artifact BATTERY = artifact("battery").slot("body", "sopli2").slot("glow", "battery_lightning")
            .arcs().resists(Hit.SHOCK, 0.1212F).resists(Hit.BURN, -0.095F).resists(Hit.STRIKE, -0.27F).power(0.00066F)
            .light(0x99B3E6, 2.0F).found(1, Element.ELECTRIC, 1).build();
    /** {@code af_soul}: a green spiral glowing in a veined shell. */
    public static final Artifact SOUL = artifact("soul").slot("body", "sopli2").slot("glow", "soul_glow").core(0.30F)
            .bloom(0.9F).health(0.0003F).power(-0.00035F).light(0x80B333, 2.0F).found(1, Element.GRAVITY, 1).build();

    /** {@code af_cristall} (Crystal): burning orange crystal from thermal anomalies. */
    public static final Artifact CRYSTAL = artifact("crystal").slot("glow", "artifact_lavahell1")
            .resists(Hit.BURN, 0.24375F).resists(Hit.SHOCK, 0.1818F).resists(Hit.CHEMICAL, -0.27F)
            .light(0xE66633, 2.0F).found(2, Element.FIRE, 2).build();
    /** {@code af_itcher} (Itcher). */
    public static final Artifact ITCHER = artifact("itcher").slot("body", "artefact_itcher")
            .resists(Hit.BURN, 0.1625F).resists(Hit.EXPLOSION, 0.47F).resists(Hit.CHEMICAL, -0.27F)
            .light(0x666600, 2.0F).found(1, Element.FIRE, 1).build();
    /** {@code af_sponge} (Sponge). */
    public static final Artifact SPONGE = artifact("sponge").slot("body", "artefact_sponge")
            .resists(Hit.STRIKE, 0.45F).resists(Hit.EXPLOSION, 0.47F)
            .light(0x666600, 2.0F).found(1, Element.CHEMICAL, 2).build();
    /** {@code af_fireball} (Fireball): a molten ball in a heat haze. */
    public static final Artifact FIREBALL = artifact("fireball").slot("body", "artifact_lavahell1")
            .slot("glow", "artifact_loner_lava").slot("glow2", "artifact_loner_lavahell")
            .resists(Hit.BURN, 0.41F).resists(Hit.SHOCK, -0.22725F).resists(Hit.STRIKE, -0.74F)
            .resists(Hit.WOUND, 0.285F).resists(Hit.BULLET, 0.19486F)
            .light(0xE66633, 2.0F).found(3, Element.FIRE, 3).build();
    /** {@code af_electra_sparkler} (Sparkler). */
    public static final Artifact SPARKLER = artifact("sparkler").slot("body", "artifact_sopli")
            .resists(Hit.SHOCK, 0.0606F).resists(Hit.WOUND, 0.1425F).resists(Hit.CHEMICAL, -0.27F)
            .light(0x99B3E6, 2.0F).found(1, Element.ELECTRIC, 1).build();
    /** {@code af_dummy_glassbeads} (Glass Beads). */
    public static final Artifact GLASS_BEADS = artifact("glass_beads").slot("glow", "artifact_lava")
            .resists(Hit.BURN, 0.08125F).resists(Hit.SHOCK, -0.135F).bleeding(0.00408F)
            .light(0xE66633, 2.0F).found(1, Element.FIRE, 1).build();
    /** {@code af_electra_moonlight} (Moonlight). */
    public static final Artifact MOONLIGHT = artifact("moonlight").slot("body", "artifact_hell1")
            .slot("glow", "artifact_glow").slot("glow2", "artifact_lighting")
            .resists(Hit.SHOCK, 0.1212F).resists(Hit.PSY, 0.165F)
            .light(0x99B3E6, 2.0F).found(2, Element.ELECTRIC, 2).build();
    /** {@code af_electra_flash} (Flash). */
    public static final Artifact FLASH = artifact("flash_artifact").slot("body", "artifact_fire").slot("glow", "artifact_glow")
            .slot("glow2", "artifact_fire_light").resists(Hit.SHOCK, 0.303F).resists(Hit.PSY, 0.275F)
            .health(-0.0003F).power(-0.00137F).light(0x99B3E6, 2.0F).found(3, Element.ELECTRIC, 3).build();
    /** {@code af_ice} (Ice). */
    public static final Artifact ICE = artifact("ice").slot("glow", "artifact_hell1").slot("glow2", "artifact_sopli2")
            .resists(Hit.PSY, -0.13F).health(-0.0006F).power(0.0017F)
            .light(0x99B3E6, 2.0F).found(3, Element.GRAVITY, 3).build();
    /** {@code af_ring} (Ring). */
    public static final Artifact RING = artifact("ring").slot("body", "artefact_ring")
            .resists(Hit.SHOCK, 0.1818F).resists(Hit.PSY, -0.13F).power(0.001F)
            .light(0x666600, 2.0F).found(2, Element.ELECTRIC, 2).build();
    /** {@code af_dummy_dummy} (Dummy). */
    public static final Artifact DUMMY = artifact("dummy").slot("body", "artifact_sopli")
            .slot("glow", "artifact_anim_lightning").slot("glow2", "artifact_sopli2").arcs()
            .resists(Hit.PSY, 0.165F).health(-0.0006F).light(0x99B3E6, 2.0F).found(2, Element.GRAVITY, 2).build();
    /** {@code af_cristall_flower} (Stone Flower). */
    public static final Artifact STONE_FLOWER = artifact("stone_flower").slot("body", "artifact_myasorubka2")
            .slot("glow", "artifact_hell1").resists(Hit.BURN, -0.176F).resists(Hit.CHEMICAL, 0.24888F)
            .resists(Hit.PSY, 0.11F).light(0x666600, 2.0F).found(1, Element.GRAVITY, 1).build();

    public static final List<Artifact> ALL = List.of(BATTERY, SOUL, CRYSTAL, ITCHER, SPONGE, FIREBALL, SPARKLER,
            GLASS_BEADS, MOONLIGHT, FLASH, ICE, RING, DUMMY, STONE_FLOWER);
}
