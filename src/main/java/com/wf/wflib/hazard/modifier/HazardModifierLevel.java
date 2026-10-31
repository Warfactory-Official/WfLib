package com.wf.wflib.hazard.modifier;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;
import com.wf.wflib.hazard.api.IHazardModifier;

/**
 * Discards the incoming level: {@code levels[property]} off the stack's block state, 1.7's per-metadata rows.
 */
public class HazardModifierLevel implements IHazardModifier {

    private final IntegerProperty property;
    private final double[] levels;

    public HazardModifierLevel(IntegerProperty property, double... levels) {
        this.property = property;
        this.levels = levels;
    }

    @Override
    public double modify(ItemStack stack, @Nullable LivingEntity entity, double level) {
        Integer value = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(property);
        return levels[value == null ? 0 : value];
    }
}
