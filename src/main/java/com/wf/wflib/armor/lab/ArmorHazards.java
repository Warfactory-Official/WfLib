package com.wf.wflib.armor.lab;

import com.wf.wflib.armor.ArmorConfig;
import com.wf.wflib.damage.api.WFDamageTypes;
import com.wf.wflib.fire.api.FireType;
import com.wf.wflib.labs.api.LabHazard;
import com.wf.wflib.labs.api.LabHazards;
import com.wf.wflib.labs.api.LabProducers;

import java.util.Locale;

/** Fire and radiation emitters; the player-side hazard tables. */
public final class ArmorHazards {
    private ArmorHazards() {
    }

    public static void register(boolean radiation) {
        for (FireType t : FireType.values()) {
            LabHazards.register(new LabHazard("wflib:fire_" + t.name().toLowerCase(Locale.ROOT), "fire",
                    WFDamageTypes.FIRE.location().toString(), t.damage, 20, "THERMAL", -1.0, 0.0,
                    LabHazard.DEFAULT_INTENSITIES));
        }
        if (radiation) {
            LabHazards.register(new LabHazard("wflib:radiation_field", "radiation", null, 0.0, 20, "RADIATION", -1.0,
                    1.0, new double[]{1, 10, 100, 1000}));
        }
        LabProducers.register(new HazardsProducer());
    }

    static double fireTier() {
        return ArmorConfig.get().fireTier();
    }
}
