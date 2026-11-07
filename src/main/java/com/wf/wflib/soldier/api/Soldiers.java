package com.wf.wflib.soldier.api;

import com.mojang.authlib.GameProfile;
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
    /** A soldier is a loaded chunk-loading player and a full AI tick; this many at once is plenty. */
    public static final int CAP = 40;

    private Soldiers() {
    }

    /** True while a soldier's join or death would otherwise show in chat. */
    public static boolean quiet() {
        return SoldierPlayer.quiet();
    }

    /**
     * Spawns a soldier standing at {@code at}.
     *
     * @param owner (nullable) who a friendly follows
     * @return the soldier, or null when the cap is reached
     */
    public static ServerPlayer spawn(ServerLevel level, Vec3 at, float yaw, SoldierSpec spec, ServerPlayer owner) {
        MinecraftServer server = level.getServer();
        if (SoldierAi.soldiers() >= CAP) return null;
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
        com.wf.wflib.soldier.Headlamp.issue(p);
        SoldierAi.spawned(server, name, spec, owner == null ? null : owner.getUUID(), at, yaw);
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
            String name = prefix + "_" + n;
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
