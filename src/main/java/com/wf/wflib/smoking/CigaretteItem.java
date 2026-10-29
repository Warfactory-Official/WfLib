package com.wf.wflib.smoking;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Tap to smoke one ({@link Smoking.Session}); {@link CigaretteSmokedEvent} on the last tick, one consumed. */
public final class CigaretteItem extends Item {

    public CigaretteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (Smoking.session(player) != null) {
            return InteractionResultHolder.fail(stack);
        }
        Smoking.start(player, hand);
        return InteractionResultHolder.consume(stack);
    }
}
