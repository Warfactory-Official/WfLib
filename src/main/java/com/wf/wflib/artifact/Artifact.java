package com.wf.wflib.artifact;

import com.wf.wflib.WFLib;
import net.minecraft.resources.ResourceLocation;

/**
 * One kind of STALKER artefact, as GAMMA leaves it (its Artefacts Reinvention's last word on each section): the hit
 * immunities it lends while worn ({@code hit_absorbation_sect}: + resists, - makes worse) and how it looks.
 *
 * @param id            item id
 * @param model         GemRender model, centred on the artefact; texture slots {@code body} (lit) and {@code glow}
 *                      (unlit)
 * @param body          the body slot's texture
 * @param glow          the glow slot's texture
 * @param arcs          the glow is electric arcs: they crawl along their shells and writhe; else it only swells
 * @param core          above 0: the glow is a sprite this wide (m) at the centre, turned to the viewer and spinning,
 *                      not the model's glow meshes (flat crossed planes read as a disc, or a star edge-on)
 * @param shock         shock_immunity: electricity (anomalies, shock weapons)
 * @param burn          burn_immunity: fire
 * @param strike        strike_immunity: blunt blows (melee, gravitational anomalies)
 * @param healthRestore health_restore_speed: share of full health back per game second (X-Ray conditions run on
 *                      game time: {@link #TIME_FACTOR})
 * @param bloom         strength of the glow round it, 0..1 (a see-through cage shows it through every gap: less)
 * @param lightRgb      trail_light_color, while held
 * @param lightRange    trail_light_range, blocks
 */
public record Artifact(String id, ResourceLocation model, ResourceLocation body, ResourceLocation glow, boolean arcs,
                       float core, float shock, float burn, float strike, float healthRestore, float bloom,
                       int lightRgb, float lightRange) {
    /** GAMMA's alife time_factor: game seconds per real second, which its restore speeds are per. */
    public static final float TIME_FACTOR = 6.0F;

    private static ResourceLocation wflib(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    /** Health back per real second while worn, in health points of {@code maxHealth}. */
    public float healthPerSecond(float maxHealth) {
        return healthRestore * TIME_FACTOR * maxHealth;
    }


    /**
     * {@code af_dummy_battery} (Battery, electric, tier 2): a charged lattice from Electros. GAMMA: shock +12.1%,
     * burn -9.5%, strike -27%, stamina back faster ({@code power_restore_speed} 0.00066: needs WFWeight, not yet),
     * radiation none; a pale blue glow (0.6/0.7/0.9, 2 m).
     */
    public static final Artifact BATTERY = new Artifact("battery", wflib("models/artifact/battery.glb"),
            wflib("textures/artifact/sopli2.png"), wflib("textures/artifact/battery_lightning.png"), true, 0.0F,
            0.1212F, -0.095F, -0.27F, 0.0F, 1.0F, 0x99B3E6, 2.0F);

    /**
     * {@code af_soul} (Soul, chemical, tier 2): a green spiral glowing in a veined shell, from acid anomalies. GAMMA:
     * health back 0.0003 a game second (0.18% a real second, a heart a minute or so), stamina back slower
     * ({@code power_restore_speed} -0.00035: needs WFWeight, not yet), radiation immunity 0.00043 (nothing); a green
     * glow (0.5/0.7/0.2, 2 m).
     */
    public static final Artifact SOUL = new Artifact("soul", wflib("models/artifact/soul.glb"),
            wflib("textures/artifact/sopli2.png"), wflib("textures/artifact/soul_glow.png"), false, 0.30F,
            0.0F, 0.0F, 0.0F, 0.0003F, 0.35F, 0x80B333, 2.0F);
}
