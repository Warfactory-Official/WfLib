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

// 1.12.2: CE-derived; 1.7's eight types (HazardRegistry:150-157) hold no cold hazard - its freeze concept is
// environmental only (ExplosionThermo, BombThermo, EntityChemical), never a held item. Zero rows is
// deliberate: CE leaves COLD unregistered too, reachable there only through GroovyScript.
public class HazardTypeCold implements IHazardType {

    @Override
    public void onUpdate(LivingEntity target, double level, ItemStack stack) {
        boolean reacher = HazardProtection.isHoldingReacher(target);
        if (RadiationConfig.disableCold || reacher) return;
        if (target instanceof Player && HazardProtection.checkForHazmat(target)) return;

        int baseLevel = (int) level - 1;
        int witherLevel = (int) level - 3;

        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 110, baseLevel));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 110, Math.min(4, baseLevel)));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 110, baseLevel));

        if (level > 4) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 110, witherLevel));
        }
    }

    @Override
    public void updateEntity(ItemEntity item, double level) {
    }

    @Override
    public void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                                     List<IHazardModifier> modifiers) {
        list.add(Component.literal("[").append(Component.translatable("trait.cryogenic")).append("]")
                          .withStyle(ChatFormatting.AQUA));
    }
}
