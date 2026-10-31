package com.wf.wflib.armor.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Armour items of the live {@link ArmorPresets}; registered by {@code ArmorSystem}. */
public final class ArmorContent {
    private ArmorContent() {
    }

    /** The item {@code presetId} is worn as; null when the preset is not live. */
    @Nullable
    public static Item item(ResourceLocation presetId) {
        return ArmorPresets.get(presetId) == null ? null : DeferredHolder.create(Registries.ITEM, presetId).get();
    }

    /** Live presets' items, catalogue order. */
    public static List<DeferredHolder<Item, Item>> items() {
        return ArmorPresets.live().stream()
                .map(p -> DeferredHolder.<Item, Item>create(Registries.ITEM, p.id())).toList();
    }
}
