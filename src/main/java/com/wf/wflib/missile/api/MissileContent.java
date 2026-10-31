package com.wf.wflib.missile.api;

import com.wf.wflib.core.api.WFLibContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.Optional;
import com.wf.wflib.core.api.WFLib;

/** Holders dependents name; registered by {@code MissileSystem}. */
public final class MissileContent {
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> STEALTH_MISSILE =
            DeferredHolder.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "missile"));

    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_FLIGHT = DeferredHolder.create(
            Registries.SOUND_EVENT, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "weapon.missile_flight"));

    private MissileContent() {
    }

    /** Live presets with an item; empty until {@link WFLibContent} freezes. */
    public static List<DeferredHolder<Item, MissileItem>> missileItems() {
        return MissilePresets.withItems().stream().map(MissileContent::holder).toList();
    }

    /** Empty: preset not live or registered after freeze. */
    public static Optional<DeferredHolder<Item, MissileItem>> missileItem(ResourceLocation presetId) {
        return MissilePresets.withItems().stream().filter(p -> p.id().equals(presetId)).findFirst()
                .map(MissileContent::holder);
    }

    private static DeferredHolder<Item, MissileItem> holder(MissilePreset preset) {
        return DeferredHolder.create(Registries.ITEM, WFLibContent.itemId(preset.id(), preset.itemPath()));
    }
}
