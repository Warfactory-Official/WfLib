package com.wf.wflib.hazard.type;

import com.wf.wflib.hazard.modifier.IHazardModifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface IHazardType {

    void onUpdate(LivingEntity target, double level, ItemStack stack);

    void updateEntity(ItemEntity item, double level);

    void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                              List<IHazardModifier> modifiers);
}
