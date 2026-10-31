package com.wf.wflib.recon.api;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.core.api.WFLib;

/** Holders dependents name; registered by {@code ReconSystem}. */
public final class ReconContent {
    public static final DeferredHolder<Block, Block> RECON_HUB = block("recon_hub");
    public static final DeferredHolder<Item, Item> RECON_HUB_ITEM = item("recon_hub");
    public static final DeferredHolder<Item, Item> PORTABLE_JAMMER = item("portable_jammer");
    /** Switched on: {@link PortableJammerItem}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ACTIVE =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE,
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "active"));

    private ReconContent() {
    }

    private static DeferredHolder<Block, Block> block(String path) {
        return DeferredHolder.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path));
    }

    private static DeferredHolder<Item, Item> item(String path) {
        return DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path));
    }
}
