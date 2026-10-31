package com.wf.wflib.armor.api.exo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Live exo spec per item id; re-authorable (content reload). Slot offer (battery/belt) is fixed at registration by the preset. */
public final class ExoProfiles {
    private static final Map<ResourceLocation, ExoSpec> SPECS = new ConcurrentHashMap<>();

    private ExoProfiles() {
    }

    public static void author(ResourceLocation item, @Nullable ExoSpec spec) {
        if (spec == null) {
            SPECS.remove(item);
        } else {
            SPECS.put(item, spec);
        }
    }

    @Nullable
    public static ExoSpec of(ItemStack stack) {
        return stack.isEmpty() || SPECS.isEmpty() ? null : SPECS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
