package com.wf.wflib.hazard.type;

import com.wf.wflib.hazard.HazardProtection;

import com.wf.wflib.radiation.RadiationConfig;
import com.wf.wflib.hazard.LivingHazardProps;
import com.wf.wflib.hazard.HazardClass;
import com.wf.wflib.hazard.modifier.IHazardModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class HazardTypeAsbestos implements IHazardType {

    @Override
    public void onUpdate(LivingEntity target, double level, ItemStack stack) {
        if (RadiationConfig.disableAsbestos) return;
        if (HazardProtection.hasProtection(target, EquipmentSlot.HEAD, HazardClass.PARTICLE_FINE)) {
            HazardProtection.damageGasMaskFilter(target, (int) level * RadiationConfig.hazardRate);
        } else {
            LivingHazardProps.incrementAsbestos(target, (int) Math.min(level, 10) * RadiationConfig.hazardRate);
        }
    }

    @Override
    public void updateEntity(ItemEntity item, double level) {
    }

    @Override
    public void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                                     List<IHazardModifier> modifiers) {
        list.add(Component.literal("[").append(Component.translatable("trait.asbestos")).append("]")
                          .withStyle(ChatFormatting.WHITE));
    }
}
