package com.wf.wflib.round.api;

import com.wf.wflib.core.api.WFLibContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.Optional;
import com.wf.wflib.core.api.WFLib;

/** Holders dependents name; registered by {@code RoundSystem}. */
public final class RoundContent {
    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = DeferredHolder.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "rocket"));

    private RoundContent() {
    }

    /** Live presets with a shell item; empty until {@link WFLibContent} freezes. */
    public static List<DeferredHolder<Item, KineticShellItem>> shellItems() {
        return KineticPresets.withItems().stream().map(RoundContent::holder).toList();
    }

    /** Empty: preset not live, registered after freeze, or an ammo variant. */
    public static Optional<DeferredHolder<Item, KineticShellItem>> shellItem(ResourceLocation presetId) {
        return KineticPresets.withItems().stream().filter(p -> p.id().equals(presetId)).findFirst()
                .map(RoundContent::holder);
    }

    private static DeferredHolder<Item, KineticShellItem> holder(KineticPreset preset) {
        return DeferredHolder.create(Registries.ITEM, WFLibContent.itemId(preset.id(), preset.itemPath()));
    }
}
