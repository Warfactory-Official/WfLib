package com.wf.wflib.demolition;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import com.wf.wflib.demolition.api.DetonatorItem;
import com.wf.wflib.demolition.api.IDetonatableEntity;

/** Gives the detonator first claim on a right-click aimed at anything it can fire. */
public final class DetonatorInteractions {

    private DetonatorInteractions() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof IDetonatableEntity)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof DetonatorItem)) {
            return;
        }
        Player player = event.getEntity();
        if (player.level() instanceof ServerLevel level) {
            if (!DetonatorItem.interactWithEntity(level, player, stack, event.getTarget())) {
                return;
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
    }
}
