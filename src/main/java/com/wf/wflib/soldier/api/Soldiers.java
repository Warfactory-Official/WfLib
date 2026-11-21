package com.wf.wflib.soldier.api;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.soldier.Headlamp;
import com.wf.wflib.soldier.SoldierAi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Puts AI soldiers into the world as players and takes them out again. Server thread only. */
public final class Soldiers {
    /** No limit any more (each soldier is a chunk-loading player and a full AI tick, so the server's tick rate is the limit); kept so callers compile. */
    @Deprecated
    public static final int CAP = Integer.MAX_VALUE;

    private Soldiers() {
    }

    /** The role a saved variant plays ("rifle", "sniper", "mg", "medic", "heavy", "assault", "grenadier"), or "rifle" for one that is gone. */
    public static String roleOf(MinecraftServer server, String variant) {
        var unit = com.wf.wflib.soldier.SoldierRoster.get(server).units().get(variant);
        return unit == null ? "rifle" : unit.role().id;
    }

    /** The soldier's person name (custom name, else the profile name): what maps and squad lists show. */
    public static String displayName(ServerPlayer soldier) {
        var n = soldier.getCustomName();
        // older saved variants carry the grade in brackets ("Ivan Petrov (Conscript)"); a person's name has none
        return n == null ? soldier.getGameProfile().getName() : n.getString().replaceFirst("\\s*\\((Conscript|Regular|Veteran|Elite)\\)$", "");
    }

    /**
     * Takes a soldier out of every client's tab list (it stays a player for everything else). A client treats an unlisted
     * player entry as a soldier, which is how the maps tell them from people.
     */
    public static void unlist(MinecraftServer server, ServerPlayer soldier) {
        var packet = unlisted(server, soldier);
        if (packet != null) server.getPlayerList().broadcastAll(packet);
    }

    /** Tells a player who just joined which soldiers are not listed (the join packets list everyone). */
    public static void unlistAllFor(ServerPlayer human) {
        var server = human.getServer();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p instanceof SoldierPlayer)) continue;
            var packet = unlisted(server, p);
            if (packet != null) human.connection.send(packet);
        }
    }

    private static net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket unlisted(MinecraftServer server, ServerPlayer soldier) {
        // the packet has no public constructor for an unlisted entry, so it is written the way the wire has it and read back
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
        try {
            buf.writeEnumSet(java.util.EnumSet.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                    net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.class);
            buf.writeCollection(List.of(soldier), (b, p) -> {
                b.writeUUID(p.getUUID());
                b.writeBoolean(false);
            });
            return net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    /** True while a soldier's join or death would otherwise show in chat. */
    public static boolean quiet() {
        return SoldierPlayer.quiet();
    }

    /**
     * Spawns a soldier standing at {@code at}.
     *
     * @param owner (nullable) who a friendly follows
     * @return the soldier
     */
    public static ServerPlayer spawn(ServerLevel level, Vec3 at, float yaw, SoldierSpec spec, ServerPlayer owner) {
        MinecraftServer server = level.getServer();
        String name = freeName(server, spec.namePrefix());
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("wflib:soldier:" + name).getBytes(StandardCharsets.UTF_8)), name);
        forgetFiles(server, profile.getId());                       // leftovers of an earlier soldier of that name
        SoldierPlayer p = new SoldierPlayer(server, level, profile);
        SoldierPlayer.quiet(true);
        try {
            server.getPlayerList().placeNewPlayer(SoldierConnection.INSTANCE, p,
                    new CommonListenerCookie(profile, 0, p.clientInformation(), false));
        } finally {
            SoldierPlayer.quiet(false);
        }
        p.setGameMode(GameType.SURVIVAL);
        p.teleportTo(level, at.x, at.y, at.z, yaw, 0);
        p.setHealth(20);
        p.markPlaced();
        p.showSkinLayers();
        p.connection.resetPosition();
        level.getChunkSource().move(p);
        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(p, (byte) (yaw * 256 / 360)), level.dimension());
        server.getPlayerList().broadcastAll(new ClientboundTeleportEntityPacket(p), level.dimension());
        wear(p, spec.armour());
        Headlamp.issue(p);
        SoldierAi.spawned(server, name, spec, owner == null ? null : owner.getUUID(), at, yaw);
        // a person's name for maps and lists (the profile name stays unique and short: Vasya_Ace_3); never drawn over the head
        p.setCustomName(net.minecraft.network.chat.Component.literal(SoldierNames.display(new java.util.Random(name.hashCode()))));
        p.setCustomNameVisible(false);
        unlist(server, p);
        return p;
    }

    /** Takes a soldier out of the world (the player list entry goes; nothing of it is kept on disk). */
    public static void remove(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null || server.getPlayerList().getPlayer(p.getUUID()) != p) return;
        SoldierPlayer.quiet(true);
        try {
            server.getPlayerList().remove(p);
        } finally {
            SoldierPlayer.quiet(false);
        }
        forgetFiles(server, p.getUUID());
    }

    /** Server shutting down: no soldier is saved with the world. */
    public static void removeAll(MinecraftServer server) {
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (p instanceof SoldierPlayer) remove(p);
        }
        SoldierAi.forget(server);
    }

    /** The lowest unused {@code Ally_1}, {@code Hostile_2}, ... */
    static String freeName(MinecraftServer server, String prefix) {
        for (int n = 1; ; n++) {
            String suffix = "_" + n;
            String safe = prefix.replaceAll("[^A-Za-z0-9_]", "_");
            if (safe.isEmpty()) safe = "Soldier";
            String name = safe.substring(0, Math.min(safe.length(), 16-suffix.length())) + suffix;
            if (server.getPlayerList().getPlayerByName(name) == null) return name;
        }
    }

    private static void wear(ServerPlayer p, Map<EquipmentSlot, ResourceLocation> armour) {
        for (var entry : armour.entrySet()) {
            ResourceLocation id = entry.getValue();
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                Item piece = BuiltInRegistries.ITEM.get(id);
                p.setItemSlot(entry.getKey(), new ItemStack(piece));
            }
        }
    }

    private static void forgetFiles(MinecraftServer server, UUID id) {
        for (Path file : List.of(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat"),
                server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat_old"),
                server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(id + ".json"),
                server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(id + ".json"))) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                // a stale file is harmless: the next soldier of that name starts from a clean slate anyway
            }
        }
    }
}
