package com.wf.wflib.backpack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wf.wflib.backpack.api.BackpackItem;
import com.wf.wflib.backpack.api.Backpacks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

/** Server behaviour around the worn backpack: wear/lock toggles, open, drops, tracking sync, {@code /wflib backpack}. */
final class BackpackServer {
    private BackpackServer() {
    }

    /** Key {@code key.wflib.backpack}: held backpack -> worn; worn -> inventory (full => refused). */
    static void toggleWorn(ServerPlayer player) {
        ItemStack worn = Backpacks.worn(player);
        if (worn.isEmpty()) {
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof BackpackItem)) {
                player.displayClientMessage(Component.translatable("wflib.backpack.none_held"), true);
                return;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            Backpacks.setWorn(player, held);
            return;
        }
        if (!player.getInventory().add(worn.copy())) {
            player.displayClientMessage(Component.translatable("wflib.backpack.no_room"), true);
            return;
        }
        Backpacks.setWorn(player, ItemStack.EMPTY);
    }

    static void toggleLock(ServerPlayer player) {
        ItemStack worn = Backpacks.worn(player);
        if (!worn.isEmpty()) {
            worn.set(Backpacks.LOCKED.get(), !Backpacks.locked(worn));
            Backpacks.sync(player);
        }
    }

    /** Op: {@code /wflib backpack wear|lock <players>} = the key / lock button for them. */
    static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("wflib").then(Commands.literal("backpack")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("wear").then(Commands.argument("players", EntityArgument.players())
                        .executes(c -> each(c, BackpackServer::toggleWorn))))
                .then(Commands.literal("lock").then(Commands.argument("players", EntityArgument.players())
                        .executes(c -> each(c, BackpackServer::toggleLock))))));
    }

    private static int each(CommandContext<CommandSourceStack> c, Consumer<ServerPlayer> action)
            throws CommandSyntaxException {
        var players = EntityArgument.getPlayers(c, "players");
        players.forEach(action);
        return players.size();
    }

    static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer) {
            CustomPacketPayload p = new BackpackPackets.Worn(target.getId(), Backpacks.shown(Backpacks.worn(target)));
            PacketDistributor.sendToPlayer(viewer, p);
        }
    }

    static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)
                || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        ItemStack worn = Backpacks.worn(player);
        if (!worn.isEmpty()) {
            e.getDrops().add(new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(),
                    worn.copy()));
            player.setData(Backpacks.WORN, ItemStack.EMPTY);
        }
    }

    /** Probe action: {@code viewer} opens {@code target}'s worn backpack. */
    static boolean open(ServerPlayer viewer, Entity target) {
        if (!(target instanceof ServerPlayer owner) || owner == viewer
                || owner.distanceTo(viewer) > Backpacks.VIEW_RANGE) {
            return false;
        }
        ItemStack worn = Backpacks.worn(owner);
        if (worn.isEmpty() || !(worn.getItem() instanceof BackpackItem item)) {
            return false;
        }
        if (Backpacks.locked(worn)) {
            viewer.displayClientMessage(Component.translatable("wflib.backpack.locked"), true);
            return false;
        }
        int slots = item.spec().slots();
        viewer.openMenu(new SimpleMenuProvider((id, inv, p) -> new com.wf.wflib.backpack.api.BackpackMenu(id, inv, owner, worn, slots),
                Component.translatable("wflib.backpack.of", owner.getDisplayName(), worn.getHoverName())), buf -> {
            buf.writeVarInt(owner.getId());
            buf.writeVarInt(slots);
        });
        return true;
    }
}
