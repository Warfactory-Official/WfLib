package com.wf.wflib.cam;

import com.wf.wflib.pda.api.Pda;
import com.wf.wflib.cam.api.CameraMonitorBlock;
import com.wf.wflib.cam.api.CameraMonitorBlockEntity;
import com.wf.wflib.cam.api.CameraChannels;
import com.wf.wflib.cam.api.FeedTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import com.wf.wflib.cam.api.CameraContent;

/** Picks a camera up and puts it down on a panel. */
public class CameraLinkerItem extends Item {

    /** How far it will reach for a drone. Roughly arm's length; a drone on its pad, not one in the air. */
    private static final double REACH = 6.0;

    public CameraLinkerItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();

        if (state.getBlock() instanceof SecurityCameraBlock) {
            if (!level.isClientSide) {
                stack.set(CamSystem.CAMERA_LINK_TARGET.get(), FeedTarget.ofCamera(level, pos));
                say(player, Component.literal(
                                String.format("holding camera at %d %d %d", pos.getX(), pos.getY(), pos.getZ()))
                        .withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.getBlock() instanceof CameraMonitorBlock) {
            if (level instanceof ServerLevel sl) {
                bind(sl, CameraMonitorBlock.originOf(pos, state), player, stack);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                FeedTarget held = stack.get(CamSystem.CAMERA_LINK_TARGET.get());
                if (Pda.is(other) && held != null && !held.empty()) {
                    CameraChannels after = add(other.getOrDefault(CameraContent.CHANNELS.get(),
                            CameraChannels.EMPTY), held, player);
                    if (after != null) {
                        other.set(CameraContent.CHANNELS.get(), after);
                        stack.remove(CamSystem.CAMERA_LINK_TARGET.get());
                    }
                } else {
                    stack.remove(CamSystem.CAMERA_LINK_TARGET.get());
                    say(player, Component.literal("linker cleared").withStyle(ChatFormatting.GRAY));
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        Entity drone = FeedHosts.lookedAt(level, player, REACH);
        if (drone == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            if (FeedHosts.spec(drone) == null) {
                say(player, Component.literal("that drone has no camera fitted").withStyle(ChatFormatting.RED));
            } else {
                stack.set(CamSystem.CAMERA_LINK_TARGET.get(), FeedTarget.ofDrone(drone.getUUID()));
                say(player, Component.literal("holding drone camera").withStyle(ChatFormatting.AQUA));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Put the held camera onto this panel. */
    private static void bind(ServerLevel level, BlockPos origin, @Nullable Player player, ItemStack stack) {
        if (!(level.getBlockEntity(origin) instanceof CameraMonitorBlockEntity monitor)) {
            return;
        }
        FeedTarget held = stack.get(CamSystem.CAMERA_LINK_TARGET.get());
        if (held == null || held.empty()) {
            say(player, Component.literal("linker is empty: use it on a camera first")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        CameraChannels after = add(monitor.channels(), held, player);
        if (after != null) {
            monitor.setChannels(after, level);
            stack.remove(CamSystem.CAMERA_LINK_TARGET.get());
        }
    }

    /**
     * @return the panel with this camera added, or null if it was refused, in which case the player has
     *      already been told which refusal it was. Shared by the monitor block and the handheld receiver so the
     *      cap, and the wording of hitting it, are one thing rather than two that drift apart.
     */
    @Nullable
    private static CameraChannels add(CameraChannels before, FeedTarget held, @Nullable Player player) {
        if (before.has(held)) {
            say(player, Component.literal("already on this panel").withStyle(ChatFormatting.YELLOW));
            return null;
        }
        int cap = CameraChannels.maxBound();
        if (before.full(cap)) {
            say(player, Component.literal("panel full: " + cap + " cameras is the limit")
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        CameraChannels after = before.with(held, cap);
        say(player, Component.literal(String.format("bound to channel %d of %d", after.size(), cap))
                .withStyle(ChatFormatting.GREEN));
        return after;
    }

    private static void say(@Nullable Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        FeedTarget held = stack.get(CamSystem.CAMERA_LINK_TARGET.get());
        if (held == null || held.empty()) {
            lines.add(Component.literal("empty: use on a camera").withStyle(ChatFormatting.DARK_GRAY));
        } else if (held.camera().isPresent()) {
            BlockPos at = held.camera().get().pos();
            lines.add(Component.literal(String.format("camera at %d %d %d", at.getX(), at.getY(), at.getZ()))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.literal("drone camera").withStyle(ChatFormatting.AQUA));
        }
    }
}
