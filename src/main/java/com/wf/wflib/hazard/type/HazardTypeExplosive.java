package com.wf.wflib.hazard.type;

import com.wf.wflib.radiation.RadiationConfig;
import com.wf.wflib.hazard.modifier.IHazardModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

public class HazardTypeExplosive implements IHazardType {

    @Override
    public void onUpdate(LivingEntity target, double level, ItemStack stack) {
        if (RadiationConfig.disableExplosive) return;
        if (target.isOnFire() && stack.getCount() > 0) {
            stack.setCount(0);
            target.level().explode(null, target.getX(), target.getEyeY(), target.getZ(),
                    (float) level, false, Level.ExplosionInteraction.TNT);
        }
    }

    @Override
    public void updateEntity(ItemEntity item, double level) {
        if (RadiationConfig.disableExplosive) return;
        if (item.isOnFire()) {
            item.discard();
            item.level().explode(null, item.getX(), item.getY() + item.getBbHeight() * 0.5,
                    item.getZ(), (float) level, false, Level.ExplosionInteraction.TNT);
        }
    }

    @Override
    public void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                                     List<IHazardModifier> modifiers) {
        list.add(Component.literal("[").append(Component.translatable("trait.explosive")).append("]")
                          .withStyle(ChatFormatting.RED));
    }
}
