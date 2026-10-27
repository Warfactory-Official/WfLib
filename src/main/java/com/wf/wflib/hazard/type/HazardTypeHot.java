package com.wf.wflib.hazard.type;

import com.wf.wflib.radiation.RadiationConfig;
import com.wf.wflib.hazard.HazardProtection;
import com.wf.wflib.hazard.modifier.IHazardModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class HazardTypeHot implements IHazardType {

    @Override
    public void onUpdate(LivingEntity target, double level, ItemStack stack) {
        boolean wetOrReacher = HazardProtection.isHoldingReacher(target) || target.isInWaterOrRain();
        if (RadiationConfig.disableHot || wetOrReacher || level <= 0) return;
        target.setRemainingFireTicks((int) Math.ceil(level) * RadiationConfig.hazardRate);
    }

    @Override
    public void updateEntity(ItemEntity item, double level) {
    }

    @Override
    public void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                                     List<IHazardModifier> modifiers) {
        level = IHazardModifier.evalAllModifiers(stack, player, level, modifiers);
        if (level > 0) {
            list.add(Component.literal("[").append(Component.translatable("trait.hot")).append("]")
                              .withStyle(ChatFormatting.GOLD));
        }
    }
}
