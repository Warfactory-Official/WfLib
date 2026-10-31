package com.wf.wflib.demolition.api;

import com.wf.wflib.demolition.MiningChargeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.UUID;
import com.wf.wflib.core.api.WFLib;

/** Holders registered by {@code DemolitionSystem}. */
public final class DemolitionContent {
    public static final DeferredHolder<Block, Block> MINING_CHARGE = block("mining_charge");
    public static final DeferredHolder<Item, Item> MINING_CHARGE_ITEM = item("mining_charge");
    public static final DeferredHolder<Block, Block> DEEP_MINING_CHARGE = block("deep_mining_charge");
    public static final DeferredHolder<Item, Item> DEEP_MINING_CHARGE_ITEM = item("deep_mining_charge");
    /** Links and fires mining charges. */
    public static final DeferredHolder<Item, Item> DETONATOR = item("detonator");

    /** Shared by both charge tiers; stores the placer's UUID so the sneak overlay knows whose charge it is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MiningChargeBlockEntity>> MINING_CHARGE_BE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id("mining_charge"));

    /** Block positions of the mining charges a detonator is wired to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<BlockPos>>> DETONATOR_CHARGES =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, id("detonator_charges"));

    /** Ids of the detonatable entities a detonator is wired to: mines, and any {@link IDetonatableEntity}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<UUID>>> DETONATOR_MINES =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, id("detonator_mines"));

    private DemolitionContent() {
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
