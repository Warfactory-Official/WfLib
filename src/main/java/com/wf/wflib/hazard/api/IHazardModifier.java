package com.wf.wflib.hazard.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IHazardModifier {

    static double evalAllModifiers(ItemStack stack, @Nullable LivingEntity entity, double level,
                                   List<IHazardModifier> mods) {
        if (mods.isEmpty()) return level;
        double result = level;
        for (int i = 0; i < mods.size(); i++) result = mods.get(i).modify(stack, entity, result);
        return result;
    }

    double modify(ItemStack stack, @Nullable LivingEntity entity, double level);
}
