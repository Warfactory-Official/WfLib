package com.wf.wflib.mist.lab;

import com.wf.wflib.damage.api.WFDamageTypes;
import com.wf.wflib.labs.api.LabHazard;
import com.wf.wflib.labs.api.LabHazards;
import com.wf.wflib.mist.MustardGasMistEffect;
import com.wf.wflib.mist.PhosgeneMistEffect;

/** War-gas emitters: their own damage and armour-exposure constants. */
public final class MistHazards {
    private MistHazards() {
    }

    public static void register() {
        LabHazards.register(new LabHazard("wflib:mustard_gas", "gas", WFDamageTypes.GAS.location().toString(),
                MustardGasMistEffect.DAMAGE, MustardGasMistEffect.DAMAGE_INTERVAL, "CHEMICAL",
                MustardGasMistEffect.EXPOSURE_TIER, 0.0, LabHazard.DEFAULT_INTENSITIES));
        LabHazards.register(new LabHazard("wflib:phosgene", "gas", "minecraft:magic", PhosgeneMistEffect.DAMAGE,
                PhosgeneMistEffect.DAMAGE_INTERVAL, null, 0.0, 0.0, LabHazard.DEFAULT_INTENSITIES));
    }
}
