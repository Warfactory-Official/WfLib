package com.wf.wflib.hazard.transformer;

import com.wf.wflib.hazard.HazardEntry;
import com.wf.wflib.hazard.HazardRegistry;
import com.wf.wflib.hazard.HazardContent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class HazardTransformerRadiationNBT implements IHazardTransformer {

    @Override
    public boolean appliesTo(ItemStack stack) {
        Float rad = stack.get(HazardContent.HAZ_RADIATION.get());
        return rad != null && rad > 0F;
    }

    @Override
    public void transform(ItemStack stack, List<HazardEntry> entries) {
        entries.add(new HazardEntry(HazardRegistry.RADIATION, stack.get(HazardContent.HAZ_RADIATION.get())));
    }
}
