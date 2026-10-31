package com.wf.wflib.carry;

import com.wf.wflib.carry.api.Carry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;

/** Carry restrictions and sync listeners. */
final class CarryServer {
    private CarryServer() {
    }

    static void onTick(PlayerTickEvent.Pre e) {
        Player player = e.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!Carry.carried(player).isEmpty() && !Carry.carrying(player) && player instanceof ServerPlayer sp) {
            Carry.release(sp);
            return;
        }
        Carry.refresh(player);
        if (Carry.carrying(player)) {
            player.setSprinting(false);
        }
    }

    static void onAttack(AttackEntityEvent e) {
        if (Carry.carrying(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (Carry.carrying(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    static void onBreak(BlockEvent.BreakEvent e) {
        if (Carry.carrying(e.getPlayer())) {
            e.setCanceled(true);
        }
    }

    static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (Carry.carrying(e.getEntity())) {
            e.setCancellationResult(InteractionResult.FAIL);
            e.setCanceled(true);
        }
    }

    static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (Carry.carrying(e.getEntity()) && !e.getItemStack().isEmpty()) {
            e.setCancellationResult(InteractionResult.FAIL);
            e.setCanceled(true);
        }
    }

    /** Block use stays; the hotbar item never fires. Storage: server deposits, vanilla screen suppressed. */
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Player player = e.getEntity();
        if (!Carry.carrying(player)) {
            return;
        }
        e.setUseItem(TriState.FALSE);
        if (e.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = e.getPos();
        IItemHandler storage = e.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, e.getFace());
        if (storage == null) {
            return;
        }
        if (player instanceof ServerPlayer sp) {
            deposit(sp, storage);
        }
        e.setCancellationResult(InteractionResult.SUCCESS);
        e.setCanceled(true);
    }

    static void deposit(ServerPlayer player, IItemHandler storage) {
        ItemStack stack = Carry.carried(player);
        ItemStack left = ItemHandlerHelper.insertItem(storage, stack.copy(), false);
        if (left.getCount() == stack.getCount()) {
            player.displayClientMessage(Component.translatable("wflib.carry.no_room"), true);
            return;
        }
        Carry.set(player, left);
    }

    static void onPickup(ItemEntityPickupEvent.Pre e) {
        if (Carry.carrying(e.getPlayer())) {
            e.setCanPickup(TriState.FALSE);
        }
    }

    static void onMount(EntityMountEvent e) {
        if (e.isMounting() && e.getEntityMounting() instanceof Player p && Carry.carrying(p)) {
            e.setCanceled(true);
        }
    }

    static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)
                || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        ItemStack stack = Carry.carried(player);
        if (!stack.isEmpty()) {
            e.getDrops().add(new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(),
                    stack.copy()));
            player.setData(Carry.CARRIED, ItemStack.EMPTY);
        }
    }

    static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer) {
            PacketDistributor.sendToPlayer(viewer, new CarryPackets.Carried(target.getId(), Carry.carried(target).copy()));
        }
    }
}
