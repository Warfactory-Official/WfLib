package com.wf.wflib.round;

import com.wf.wflib.missile.api.MissileModels;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.pen.PenMaterial;
import com.wf.wflib.warhead.api.FireWarhead;
import com.wf.wflib.warhead.api.GasWarhead;
import com.wf.wflib.warhead.api.RecursiveFrag;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.wf.wflib.round.api.KineticPresets.rl;

/** WFLib catalogue rounds; live only once requested. */
public final class KineticCatalogue {
    /** Pre-1.6.4 blast-resistance 100/60 outcome: every class below ARMOR drilled. */
    private static final double DRILLS_METAL = PenMaterial.METAL.resistance;
    private static final Map<ResourceLocation, KineticPreset> PRESETS = new LinkedHashMap<>();

    private KineticCatalogue() {
    }

    public static Collection<KineticPreset> all() {
        return Collections.unmodifiableCollection(PRESETS.values());
    }

    public static KineticPreset get(ResourceLocation id) {
        return PRESETS.get(id);
    }

    private static void add(KineticPreset preset) {
        PRESETS.put(preset.id(), preset);
    }

    static void define() {
        // A field gun's shell: the reference round every other one is a variation on.
        add(KineticPreset.builder(KineticPresets.defaultId(), MissileModels.rl("micro"), Warheads.rl("standard"))
                .speed(6.0).mass(20.0).build());

        add(KineticPreset.builder(rl("ap"), MissileModels.rl("micro"), Warheads.rl("inert"))
                .speed(12.0).drag(0.005).mass(30.0).penetration(8, DRILLS_METAL).armorPenetration(550.0)
                .passesThroughEntities().build());

        // Shaped charge: a narrow jet along the impact heading, so it drills rather than craters.
        add(KineticPreset.builder(rl("heat"), MissileModels.rl("micro"), Warheads.rl("shaped_charge"))
                .speed(8.0).mass(22.0).penetration(2, DRILLS_METAL).blastHalfAngle(15.0).armorPenetration(450.0).build());

        // Airburst fragmentation: bursts short of the ground so the cone covers infantry in the open.
        add(KineticPreset.builder(rl("frag"), MissileModels.rl("micro"), Warheads.rl("fragmentation"))
                .speed(6.0).mass(18.0).fragments(24).airburst(8.0).build());

        add(KineticPreset.builder(rl("incendiary"), MissileModels.rl("micro"), FireWarhead.ID)
                .speed(6.0).mass(18.0).build());

        add(KineticPreset.builder(rl("chemical"), MissileModels.rl("micro_taint"), GasWarhead.ID)
                .speed(5.0).mass(18.0).airburst(6.0).build());

        add(KineticPreset.builder(rl("cluster"), MissileModels.rl("cluster_part"), RecursiveFrag.ID)
                .speed(6.0).mass(22.0).fragments(6).airburst(24.0).build());

        add(KineticPreset.builder(rl("emp"), MissileModels.rl("micro_emp"), Warheads.rl("emp"))
                .speed(6.0).mass(16.0).airburst(12.0).build());

        // Rocket assisted: less drag and more speed buy range, which is the whole point of one.
        add(KineticPreset.builder(rl("rap"), MissileModels.rl("micro"), Warheads.rl("standard"))
                .speed(9.0).drag(0.004).mass(24.0).life(4000).build());

        add(KineticPreset.builder(rl("nuclear"), MissileModels.rl("micro_schrab"), Warheads.rl("mininuke"))
                .speed(4.0).mass(60.0).build());

        add(KineticPreset.builder(rl("practice"), MissileModels.rl("micro"), Warheads.rl("inert"))
                .speed(6.0).mass(20.0).build());
    }
}
