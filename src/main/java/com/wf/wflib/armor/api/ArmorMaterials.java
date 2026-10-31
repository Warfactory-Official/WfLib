package com.wf.wflib.armor.api;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.core.api.WFLib;

/** Per-preset {@link ArmorMaterial} holders; registered by {@code ArmorSystem}. Defense numbers are never read: {@code ArmorEvents} strips the attributes of managed pieces. */
public final class ArmorMaterials {

    /** Night vision goggle worn standalone: no protection; an ArmorItem so the head renders its armour model. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> NVG = DeferredHolder.create(
            Registries.ARMOR_MATERIAL, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "nvg"));

    private ArmorMaterials() {
    }

    public static Holder<ArmorMaterial> of(ArmorPreset preset) {
        if (preset.isInsert()) {
            throw new IllegalStateException("no armour material for " + preset.id() + " (insert)");
        }
        return DeferredHolder.create(Registries.ARMOR_MATERIAL, preset.id());
    }
}
