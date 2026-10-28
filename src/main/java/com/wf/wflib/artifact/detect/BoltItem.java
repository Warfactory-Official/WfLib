package com.wf.wflib.artifact.detect;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Bolts (STALKER's): thrown ahead to find anomalies by setting them off. There are always more: none are used up. */
public class BoltItem extends Item {
    /** Ticks between throws. */
    private static final int COOLDOWN = 12;

    public BoltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.4F, 1.4F);
        if (!level.isClientSide) {
            BoltEntity bolt = new BoltEntity(level, player);
            bolt.setItem(stack.copyWithCount(1));
            bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.1F, 1.0F);
            level.addFreshEntity(bolt);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
