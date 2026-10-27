package com.wf.wflib.hazard.type;

import com.wf.wflib.radiation.RadiationConfig;
import com.wf.wflib.hazard.HazardProtection;
import com.wf.wflib.hazard.modifier.IHazardModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// 1.12.2: no 1.7 counterpart; zero rows deliberate (CE: GroovyScript only). CE nerve-agent filter arm dropped:
// 1.7 ArmorRegistry:108 comments out its HazardClass.
public class HazardTypeToxic implements IHazardType {

    @Override
    public void onUpdate(LivingEntity target, double level, ItemStack stack) {
        if (RadiationConfig.disableToxic) return;

        boolean reacher = HazardProtection.isHoldingReacher(target);
        boolean hasHazmat = target instanceof Player player && HazardProtection.checkForHazmat(player);

        boolean isUnprotected = !(hasHazmat || reacher);

        if (isUnprotected) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 110, (int) (level - 1)));
            if (level > 2) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 110, (int) Math.min(4, level - 4)));
            }
            if (level > 4) {
                target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 110, (int) level));
            }
            if (level > 6 && target.getRandom().nextInt((int) (2000 / level)) == 0) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 110, (int) (level - 4)));
            }
        }

        if (level > 8) {
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 110, (int) (level - 8)));
        }
        if (level > 16) {
            target.addEffect(new MobEffectInstance(MobEffects.HARM, 110, (int) (level - 16)));
        }
    }

    @Override
    public void updateEntity(ItemEntity item, double level) {
    }

    @Override
    public void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                                     List<IHazardModifier> modifiers) {
        String adjectiveKey;
        if (level > 16) {
            adjectiveKey = "adjective.extreme";
        } else if (level > 8) {
            adjectiveKey = "adjective.veryhigh";
        } else if (level > 4) {
            adjectiveKey = "adjective.high";
        } else if (level > 2) {
            adjectiveKey = "adjective.medium";
        } else {
            adjectiveKey = "adjective.little";
        }

        list.add(Component.literal("[").append(Component.translatable(adjectiveKey)).append(" ")
                          .append(Component.translatable("trait.toxic")).append("]").withStyle(ChatFormatting.GREEN));
    }
}
