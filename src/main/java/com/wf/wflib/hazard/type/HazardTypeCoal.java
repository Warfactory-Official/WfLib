package com.wf.wflib.hazard.type;

import com.wf.wflib.hazard.HazardConfig;
import com.wf.wflib.hazard.HazardProtection;

import com.wf.wflib.radiation.api.RadiationConfig;
import com.wf.wflib.hazard.api.LivingHazardProps;
import com.wf.wflib.hazard.api.HazardClass;
import com.wf.wflib.hazard.api.IHazardModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import com.wf.wflib.hazard.api.IHazardType;

public class HazardTypeCoal implements IHazardType {

    @Override
    public void onUpdate(LivingEntity target, double level, ItemStack stack) {
        if (HazardConfig.get().disableCoal()) return;
        if (!HazardProtection.hasProtection(target, EquipmentSlot.HEAD, HazardClass.PARTICLE_COARSE)) {
            LivingHazardProps.incrementBlackLung(target, (int) Math.min(level * stack.getCount(), 10) * HazardConfig.get().hazardRate());
        } else if (target.getRandom().nextInt(Math.max(65 - stack.getCount(), 1)) == 0) {
            HazardProtection.damageGasMaskFilter(target, (int) level * HazardConfig.get().hazardRate());
        }
    }

    @Override
    public void updateEntity(ItemEntity item, double level) {
    }

    @Override
    public void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                                     List<IHazardModifier> modifiers) {
        list.add(Component.literal("[").append(Component.translatable("trait.coal")).append("]")
                          .withStyle(ChatFormatting.DARK_GRAY));
    }
}
