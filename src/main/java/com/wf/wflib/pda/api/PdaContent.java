package com.wf.wflib.pda.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.core.api.WFLib;

/** Holders dependents name; registered by {@code PdaSystem}. */
public final class PdaContent {
    public static final DeferredHolder<Item, Item> PDA =
            DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda"));

    private PdaContent() {
    }
}
