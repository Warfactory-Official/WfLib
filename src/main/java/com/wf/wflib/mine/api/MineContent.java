package com.wf.wflib.mine.api;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Collection;
import java.util.Optional;
import com.wf.wflib.core.api.WFLib;

/** Holders registered by {@code MineSystem}. */
public final class MineContent {
    public static final DeferredHolder<EntityType<?>, EntityType<MineEntity>> MINE =
            DeferredHolder.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "mine"));

    /** How many canisters were left on a rack when it was lifted. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MINE_CANISTERS =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE,
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "mine_canisters"));

    private MineContent() {
    }

    /** One {@link MineItem} per live preset, registration order. */
    public static Collection<DeferredHolder<Item, MineItem>> items() {
        return MinePresets.live().stream().map(MineContent::holder).toList();
    }

    /** @return the item a mine preset is laid from, or empty if that preset has no item. */
    public static Optional<DeferredHolder<Item, MineItem>> item(ResourceLocation presetId) {
        return MinePresets.live().contains(presetId) ? Optional.of(holder(presetId)) : Optional.empty();
    }

    private static DeferredHolder<Item, MineItem> holder(ResourceLocation presetId) {
        return DeferredHolder.create(Registries.ITEM, MinePresets.itemId(presetId));
    }
}
