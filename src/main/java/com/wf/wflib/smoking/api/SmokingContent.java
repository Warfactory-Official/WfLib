package com.wf.wflib.smoking.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.core.api.WFLib;

/** Holders registered by {@code SmokingSystem}. */
public final class SmokingContent {
    /** Smoked for stress relief where a mod answers {@link com.wf.wflib.smoking.api.event.CigaretteSmokedEvent}. */
    public static final DeferredHolder<Item, Item> CIGARETTE = DeferredHolder.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "cigarette"));

    private SmokingContent() {
    }
}
