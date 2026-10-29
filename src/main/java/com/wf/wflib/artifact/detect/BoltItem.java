package com.wf.wflib.artifact.detect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** {@code wflib:bolt}: a bolt for packs without another mod's ({@link Bolts}). */
public class BoltItem extends Item {
    public BoltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return Bolts.toss(level, player, hand);
    }
}
