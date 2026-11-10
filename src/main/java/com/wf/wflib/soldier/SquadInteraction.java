package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Sneak and use with an empty hand: recruit a friendly soldier, or ask one of yours what it is doing. */
final class SquadInteraction {
    private SquadInteraction() {}

    static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer player) || player instanceof SoldierPlayer || !(e.getTarget() instanceof SoldierPlayer soldier)) return;
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return;
        SoldierAi.Bot b = SoldierAi.botOf(soldier.getGameProfile().getName());
        if (b == null || !b.soldier || b.huntsPlayers) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        try {
            if (b.companion == null) {
                Companion c = Squads.recruit(player, soldier);
                SoldierVoices.say(soldier, b, "greet");
                player.displayClientMessage(Component.literal("Recruited " + soldier.getGameProfile().getName() + " to " + Companion.TEAMS[c.fireTeam]), true);
            } else if (player.getUUID().equals(b.ownerId)) {
                SoldierVoices.say(soldier, b, "talk");
                player.displayClientMessage(Component.literal(soldier.getGameProfile().getName() + " (" + Companion.TEAMS[b.fireTeam] + "): " + b.order.label()), true);
            }
        } catch (IllegalArgumentException ex) {
            player.displayClientMessage(Component.literal(ex.getMessage()), true);
        }
    }
}
