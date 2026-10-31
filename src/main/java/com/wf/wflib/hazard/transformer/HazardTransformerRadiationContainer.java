package com.wf.wflib.hazard.transformer;

import com.wf.wflib.hazard.api.HazardContent;
import com.wf.wflib.hazard.api.HazardEntry;
import com.wf.wflib.hazard.api.Hazards;
import com.wf.wflib.hazard.api.HazardRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;
import com.wf.wflib.hazard.api.IHazardTransformer;

/**
 * Divergence: NTM box / bag / toolbox / crates -> any stack with {@code container} or {@code bundle_contents};
 * box sqrt -> {@code #wflib:rad_containment}; bag x2, sealed crates absent.
 */
public class HazardTransformerRadiationContainer implements IHazardTransformer {

    @Override
    public boolean appliesTo(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null && contents != ItemContainerContents.EMPTY) return true;
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundle != null && !bundle.isEmpty();
    }

    @Override
    public void transform(ItemStack stack, List<HazardEntry> entries) {
        float radiation = 0;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) for (ItemStack held : contents.nonEmptyItems()) radiation += rads(held);
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) for (ItemStack held : bundle.items()) radiation += rads(held);
        if (stack.is(HazardContent.RAD_CONTAINMENT)) radiation = (float) Math.sqrt(radiation);
        if (radiation > 0) entries.add(new HazardEntry(Hazards.RADIATION, radiation));
    }

    private static float rads(ItemStack held) {
        return (float) HazardRules.getHazardLevelFromStack(held, Hazards.RADIATION) * held.getCount();
    }
}
