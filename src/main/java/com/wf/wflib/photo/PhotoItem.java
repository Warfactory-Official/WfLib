package com.wf.wflib.photo;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A printed photograph. Use opens the viewer on the client; the server serves the image to anyone who asks by id. */
public final class PhotoItem extends Item {
    public PhotoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PhotoRef ref = stack.get(PhotoSystem.PHOTO_REF.get());
        if (level.isClientSide && ref != null) {
            com.wf.wflib.photo.client.PhotoClient.view(ref.id());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        PhotoRef ref = stack.get(PhotoSystem.PHOTO_REF.get());
        if (ref != null) {
            lines.add(Component.translatable("wflib.photo.by", ref.author()).withStyle(ChatFormatting.GRAY));
        }
    }
}
