package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierRole;
import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Soldiers that belong to no roster survive a restart. Their state is captured into the {@link PostBook} every half minute
 * and when the server stops, and put back when it starts again, a few a tick (each one's chunk is loaded to receive it).
 * Server thread only.
 */
final class Posts {
    /** How often the world's soldiers are written down (ticks), and how many come back per tick. */
    static final int CAPTURE_EVERY = 600, RESTORE_PER_TICK = 8;
    private static final Deque<PostBook.Post> PENDING = new ArrayDeque<>();
    private static MinecraftServer running;

    private Posts() {}

    static void start(MinecraftServer server) {
        PENDING.clear();
        PENDING.addAll(PostBook.get(server).posts());
        running = server;
    }

    static void stop(MinecraftServer server) {
        if (running == server) capture(server);                 // before the soldiers are removed with the server
        PENDING.clear();
        running = null;
    }

    static void onServerTick(ServerTickEvent.Post e) {
        var server = e.getServer();
        if (running != server) return;
        for (int i = 0; i < RESTORE_PER_TICK && !PENDING.isEmpty(); i++) restore(server, PENDING.poll());
        if (server.getTickCount() % CAPTURE_EVERY == 0) capture(server);
    }

    /** How many soldiers a territory's garrison still has waiting to come back. */
    static int pending(String garrison) {
        int n = 0;
        for (PostBook.Post p : PENDING) if (p.garrison().equals(garrison)) n++;
        return n;
    }

    /** The soldiers still to come back plus every unowned one in the world now, written to the book. */
    static void capture(MinecraftServer server) {
        if (SoldierAi.filming(server)) return;
        PostBook.get(server).replace(snapshot(server));
    }

    static List<PostBook.Post> snapshot(MinecraftServer server) {
        List<PostBook.Post> out = new ArrayList<>(PENDING);
        for (SoldierAi.Bot b : SoldierAi.soldierBots()) {
            if (b.server != server || b.companion != null) continue;
            ServerPlayer p = server.getPlayerList().getPlayerByName(b.name);
            if (p == null || !p.isAlive() || !SoldierAi.scripted(p)) continue;
            out.add(of(p, b));
        }
        return out;
    }

    static PostBook.Post of(ServerPlayer p, SoldierAi.Bot b) {
        Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = p.getItemBySlot(slot);
            if (!piece.isEmpty()) worn.put(slot, piece.copy());
        }
        SoldierOrder order = b.order.kind() == SoldierOrder.Kind.RESUPPLY && b.resume != null ? b.resume : b.order;   // the errand is not the assignment
        String name = b.name.contains("_") ? b.name.substring(0, b.name.lastIndexOf('_')) : b.name;
        return new PostBook.Post(b.team, name, b.huntsPlayers, b.gun, b.grenades, b.mags, SoldierRole.tactics(b.role), (int) b.range, b.spreadDeg,
                b.voice, b.light, p.hasCustomName() ? p.getCustomName().getString() : "", b.ownerId, p.level().dimension().location().toString(),
                p.position(), p.getYRot(), b.spawn == null ? p.position() : b.spawn, worn, order, b.garrison);
    }

    /** Puts one soldier back where it stood, loading the chunk to receive it. A post in a dimension that is gone is dropped. */
    static void restore(MinecraftServer server, PostBook.Post post) {
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(post.dimension())));
        if (level == null) return;
        var at = net.minecraft.core.BlockPos.containing(post.pos());
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        ServerPlayer owner = post.owner() == null ? null : server.getPlayerList().getPlayer(post.owner());
        var p = Soldiers.spawn(level, post.pos(), post.yaw(), post.spec(), owner);
        for (var e : post.armour().entrySet()) p.setItemSlot(e.getKey(), e.getValue().copy());
        harden(p, post.role());
        if (!post.name().isEmpty()) {
            p.setCustomName(Component.literal(post.name()));
            p.setCustomNameVisible(true);
        }
        SoldierAi.Bot b = SoldierAi.botOf(p.getGameProfile().getName());
        b.ownerId = post.owner();
        b.range = post.range();
        b.spreadDeg = post.spread();
        b.voice = post.voice();
        b.light = post.light();
        b.garrison = post.garrison();
        b.spawn = post.post();
        if (post.order().kind() == SoldierOrder.Kind.FOLLOW) {
            if (post.hunts()) b.anchor = post.post();                  // an enemy goes back to its post, a friendly stays where it stands
        } else if (!post.order().kind().needsRegion || TerritoryBook.get(server).get(post.order().region()) != null) {
            Orders.apply(b, p.position(), post.order());
        }
    }

    /** A heavy soldier is tougher than the rest. */
    static void harden(ServerPlayer p, SoldierRole role) {
        if (role != SoldierRole.HEAVY) return;
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
        p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(.6);
        p.setHealth(40);
    }
}
