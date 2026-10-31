package com.wf.wflib.hazard.transformer;

import com.wf.wflib.hazard.api.HazardEntry;
import com.wf.wflib.hazard.api.Hazards;
import com.wf.wflib.hazard.api.HazardContent;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import com.wf.wflib.hazard.api.IHazardTransformer;

public class HazardTransformerRadiationNBT implements IHazardTransformer {

    @Override
    public boolean appliesTo(ItemStack stack) {
        Float rad = stack.get(HazardContent.HAZ_RADIATION.get());
        return rad != null && rad > 0F;
    }

    @Override
    public void transform(ItemStack stack, List<HazardEntry> entries) {
        entries.add(new HazardEntry(Hazards.RADIATION, stack.get(HazardContent.HAZ_RADIATION.get())));
    }
}
