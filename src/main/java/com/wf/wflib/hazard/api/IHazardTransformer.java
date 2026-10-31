package com.wf.wflib.hazard.api;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A hazard source keyed on something the registration tiers cannot express, contributing on top of what they
 * resolved. The pair is split so a stack no transformer touches allocates nothing: appliesTo must stay a
 * cheap test, the entry list waiting until one answers true. A transformer only ever ADDS.
 */
public interface IHazardTransformer {

    boolean appliesTo(ItemStack stack);

    void transform(ItemStack stack, List<HazardEntry> entries);
}
