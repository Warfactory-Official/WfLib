package com.wf.wflib.artifact;

import com.wf.wflib.WFLib;
import net.minecraft.resources.ResourceLocation;

/**
 * One kind of STALKER artefact, as GAMMA leaves it (its Artefacts Reinvention's last word on each section): the hit
 * immunities it lends while worn ({@code hit_absorbation_sect}: + resists, - makes worse) and how it looks.
 *
 * @param id          item id
 * @param model       GemRender model, centred on the artefact
 * @param shock       shock_immunity: electricity (anomalies, shock weapons)
 * @param burn        burn_immunity: fire
 * @param strike      strike_immunity: blunt blows (melee, gravitational anomalies)
 * @param lightRgb    trail_light_color, while held
 * @param lightRange  trail_light_range, blocks
 */
public record Artifact(String id, ResourceLocation model, float shock, float burn, float strike, int lightRgb,
                       float lightRange) {

    /**
     * {@code af_dummy_battery} (Battery, electric, tier 2): a charged lattice from Electros. GAMMA: shock +12.1%,
     * burn -9.5%, strike -27%, stamina back faster ({@code power_restore_speed} 0.00066: needs WFWeight, not yet),
     * radiation none; a pale blue glow (0.6/0.7/0.9, 2 m).
     */
    public static final Artifact BATTERY = new Artifact("battery",
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/artifact/battery.glb"),
            0.1212F, -0.095F, -0.27F, 0x99B3E6, 2.0F);
}
