package com.wf.wflib.ammo.api;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;

/** {@code wflib:ammo_variant} component + one {@link CartridgeItem} per live calibre; registered by {@code AmmoSystem}. */
public final class AmmoItems {
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> VARIANT =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE,
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "ammo_variant"));

    private static volatile Map<ResourceLocation, CartridgeItem> items = Map.of();

    private AmmoItems() {
    }

    /** {@code AmmoSystem} only, at item registration. */
    public static void setItems(Map<ResourceLocation, CartridgeItem> registered) {
        items = Collections.unmodifiableMap(registered);
    }

    /** Calibre id -> item, registration order. */
    public static Map<ResourceLocation, CartridgeItem> items() {
        return items;
    }

    public static CartridgeItem item(ResourceLocation calibre) {
        CartridgeItem i = items.get(calibre);
        if (i == null) {
            throw new IllegalArgumentException("no cartridge item for calibre " + calibre);
        }
        return i;
    }

    public static ItemStack stack(AmmoVariant v, int count) {
        ItemStack s = new ItemStack(item(v.calibre()), count);
        s.set(VARIANT.get(), v.id());
        return s;
    }

    public static ItemStack stack(ResourceLocation variant, int count) {
        AmmoVariant v = Ammo.variant(variant);
        if (v == null) {
            throw new IllegalArgumentException("unknown ammo variant " + variant);
        }
        return stack(v, count);
    }

    /** Null unless a cartridge; the variant is the stack's, calibre mismatch => item's default. */
    @Nullable
    public static AmmoVariant variantOf(ItemStack stack) {
        return stack.getItem() instanceof CartridgeItem c ? c.variant(stack) : null;
    }
}
