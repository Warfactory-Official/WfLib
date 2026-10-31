package com.wf.wflib.drone.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.core.api.WFLib;

/** Holders dependents name; registered by {@code DroneSystem}. */
public final class DroneContent {
    public static final DeferredHolder<Block, Block> DRONE_NET = block("drone_net");
    public static final DeferredHolder<Item, Item> DRONE_NET_ITEM = item("drone_net");
    public static final DeferredHolder<Block, Block> DRONE_PAD = block("drone_pad");
    public static final DeferredHolder<Item, Item> DRONE_PAD_ITEM = item("drone_pad");

    private DroneContent() {
    }

    private static DeferredHolder<Block, Block> block(String path) {
        return DeferredHolder.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path));
    }

    private static DeferredHolder<Item, Item> item(String path) {
        return DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path));
    }
}
