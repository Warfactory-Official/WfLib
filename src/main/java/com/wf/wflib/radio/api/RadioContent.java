package com.wf.wflib.radio.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.core.api.WFLib;

/** Holders dependents name; registered by {@code RadioSystem}. */
public final class RadioContent {
    /** Strapped to a carrier: its radio plays as {@link RadioVoice#SPEAKER}. */
    public static final DeferredHolder<Item, Item> LOUDSPEAKER =
            DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "loudspeaker"));

    private RadioContent() {
    }
}
