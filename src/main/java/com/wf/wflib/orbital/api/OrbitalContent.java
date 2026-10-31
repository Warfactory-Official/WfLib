package com.wf.wflib.orbital.api;

import com.wf.wflib.orbital.CargoShuttleBlockEntity;
import com.wf.wflib.orbital.LandingPadBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.core.api.WFLib;

/** Holders registered by {@code OrbitalSystem}. */
public final class OrbitalContent {
    public static final DeferredHolder<Block, Block> CARGO_SHUTTLE = block("cargo_shuttle");
    public static final DeferredHolder<Item, Item> CARGO_SHUTTLE_ITEM = item("cargo_shuttle");
    public static final DeferredHolder<Block, Block> LANDING_PAD = block("landing_pad");
    public static final DeferredHolder<Item, Item> LANDING_PAD_ITEM = item("landing_pad");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoShuttleBlockEntity>> CARGO_SHUTTLE_BE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id("cargo_shuttle"));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LandingPadBlockEntity>> LANDING_PAD_BE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id("landing_pad"));

    private OrbitalContent() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    private static DeferredHolder<Block, Block> block(String path) {
        return DeferredHolder.create(Registries.BLOCK, id(path));
    }

    private static DeferredHolder<Item, Item> item(String path) {
        return DeferredHolder.create(Registries.ITEM, id(path));
    }
}
