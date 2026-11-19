package com.wf.wflib.photo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Use fires the shutter on the server ({@link PhotoServer#shutter}); the client then renders and uploads the picture. */
public final class CameraItem extends Item {
    public CameraItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            player.getCooldowns().addCooldown(this, PhotoConfig.COOLDOWN_TICKS.get());
            PhotoServer.shutter(sp, PhotoLimits.FOV);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
