package com.wf.wflib.skin.api;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import com.wf.wflib.core.api.WFLib;

/** Chosen {@link Skinnable} look per stack; client asks with {@code SkinChoose}, the server checks and stamps it. */
public final class Skins {
    /** Index into {@link Skinnable#skins()}; absent => 0. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SKIN =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "skin"));

    private Skins() {
    }

    /** {@code stack}'s looks; empty => not skinnable. */
    public static List<Skinnable.Skin> of(ItemStack stack) {
        return stack.getItem() instanceof Skinnable s ? s.skins() : List.of();
    }

    /** Worn look, clamped to the item's current list (a pack edit can shorten it). */
    public static int index(ItemStack stack) {
        int n = of(stack).size();
        return n == 0 ? 0 : Math.clamp(stack.getOrDefault(SKIN.get(), 0), 0, n - 1);
    }

    /** {@code index} out of range => false, stack untouched. */
    public static boolean set(ItemStack stack, int index) {
        if (index < 0 || index >= of(stack).size()) {
            return false;
        }
        if (index == 0) {
            stack.remove(SKIN.get());
        } else {
            stack.set(SKIN.get(), index);
        }
        return true;
    }
}
