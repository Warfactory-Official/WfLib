package com.wf.wflib.physics.api;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * A rider waiting for the hull it stood on: read back from a save, or left behind when the hull's
 * chunk unloaded first. Held still while waiting, since deck collision needs an attachment and an
 * attachment needs the hull in the level. Server form names the hull by UUID; client form (local
 * player only) names it by entity id once the server has placed the player.
 */
public final class DeckRejoin {

    private static final String TAG = "wflib_deck";
    private static final String TAG_CARRIER = "Carrier";
    private static final String TAG_LOCAL = "Local";
    private static final String TAG_YAW = "Yaw";
    private static final String TAG_CHUNK = "Chunk";

    /** Ticks a rejoin keeps looking once the hull's chunk has its entities loaded. */
    private static final int TIMEOUT = 40;

    /** Player cap regardless of chunk state: a frozen client is worse than a fall. */
    private static final int PLAYER_TIMEOUT = 20 * 10;

    /** Client hold cap after which the local player is let go whatever the server said. */
    private static final int CLIENT_TIMEOUT = 20 * 15;

    /** No chunk recorded => timeout always runs. */
    private static final long UNKNOWN_CHUNK = Long.MIN_VALUE;

    @Nullable
    private final UUID carrier;
    /** Client form: hull id, -1 until the server has placed the player. */
    private int carrierId = -1;
    private final Vector3d local = new Vector3d();
    /** Rider yaw minus hull yaw. */
    private float yaw;
    private final long chunk;
    private int ticks;
    private int age;

    private DeckRejoin(@Nullable UUID carrier, long chunk) {
        this.carrier = carrier;
        this.chunk = chunk;
    }

    @Nullable
    public static DeckRejoin of(Entity entity) {
        return ((DeckRider) entity).wflib$deckRejoin();
    }

    private static void set(Entity entity, @Nullable DeckRejoin rejoin) {
        ((DeckRider) entity).wflib$setDeckRejoin(rejoin);
    }

    /** A rejoin onto the hull the rider now grips, in the hull's current frame. */
    static DeckRejoin capture(Entity entity, DeckBody vehicle) {
        DeckSnapshot deck = vehicle.deckSnapshot();
        DeckRejoin rejoin = new DeckRejoin(vehicle.getUUID(),
                ChunkPos.asLong(BlockPos.containing(vehicle.position())));
        VehicleParenting.toLocal(deck, entity.getX(), entity.getY(), entity.getZ(), rejoin.local);
        rejoin.yaw = entity.getYRot() - deck.yaw();
        return rejoin;
    }

    /** Hull unloading before a rider it carries: the rider waits instead of falling. */
    static void leaveWaiting(Entity entity, DeckBody vehicle) {
        set(entity, capture(entity, vehicle));
    }

    // ---------------------------------------------------------------- persistence

    public static void save(Entity entity, CompoundTag compound) {
        if (!(entity instanceof LivingEntity) || entity.isPassenger()) {
            return;
        }
        DeckRejoin rejoin = of(entity);
        if (rejoin == null) {
            DeckAttachment attachment = VehicleParenting.attachmentOf(entity);
            if (attachment == null || !attachment.gripped() || !VehicleParenting.isUsable(attachment, entity)) {
                return;
            }
            rejoin = capture(entity, attachment.vehicle());
        }
        if (rejoin.carrier == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_CARRIER, rejoin.carrier);
        tag.putDouble(TAG_LOCAL + "X", rejoin.local.x);
        tag.putDouble(TAG_LOCAL + "Y", rejoin.local.y);
        tag.putDouble(TAG_LOCAL + "Z", rejoin.local.z);
        tag.putFloat(TAG_YAW, rejoin.yaw);
        tag.putLong(TAG_CHUNK, rejoin.chunk);
        compound.put(TAG, tag);
    }

    public static void load(Entity entity, CompoundTag compound) {
        if (!compound.contains(TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag tag = compound.getCompound(TAG);
        DeckRejoin rejoin = new DeckRejoin(tag.getUUID(TAG_CARRIER),
                tag.contains(TAG_CHUNK, Tag.TAG_LONG) ? tag.getLong(TAG_CHUNK) : UNKNOWN_CHUNK);
        rejoin.local.set(tag.getDouble(TAG_LOCAL + "X"), tag.getDouble(TAG_LOCAL + "Y"), tag.getDouble(TAG_LOCAL + "Z"));
        rejoin.yaw = tag.getFloat(TAG_YAW);
        set(entity, rejoin);
    }

    // ---------------------------------------------------------------- server

    /** Before any chunk reaches the client, so the player never ticks unheld. */
    public static void onLoggedIn(ServerPlayer player) {
        if (of(player) != null) {
            PacketDistributor.sendToPlayer(player, ServerDeckRejoin.hold());
        }
    }

    /** Places the rider once its hull is in the level with a deck. */
    public static void tick(Entity entity, DeckRejoin rejoin) {
        if (entity.level().isClientSide()) {
            if (entity instanceof Player player && player.isLocalPlayer()) {
                tickClient(player, rejoin);
            } else {
                set(entity, null);
            }
            return;
        }
        ServerLevel level = (ServerLevel) entity.level();
        if (rejoin.carrier == null || entity.isPassenger() || !entity.isAlive()) {
            giveUp(entity);
            return;
        }
        Entity found = level.getEntity(rejoin.carrier);
        if (found != null && !(found instanceof DeckBody)) {
            giveUp(entity);
            return;
        }
        if (found instanceof DeckBody vehicle && ready(vehicle)) {
            place(entity, vehicle, rejoin);
            return;
        }
        // An unloaded hull is not a missing one: wait as long as it stays unloaded.
        boolean chunkLoaded = rejoin.chunk == UNKNOWN_CHUNK || level.areEntitiesLoaded(rejoin.chunk);
        if ((chunkLoaded && ++rejoin.ticks > TIMEOUT)
                || (entity instanceof Player && ++rejoin.age > PLAYER_TIMEOUT)) {
            giveUp(entity);
        }
    }

    private static boolean ready(DeckBody vehicle) {
        return !vehicle.isRemoved() && vehicle.collides() && vehicle.deckSnapshot().count() > 0;
    }

    private static void place(Entity entity, DeckBody vehicle, DeckRejoin rejoin) {
        DeckSnapshot deck = vehicle.deckSnapshot();
        Vec3 at = VehicleParenting.toWorld(deck, rejoin.local, new Vector3d());
        float yaw = deck.yaw() + rejoin.yaw;
        set(entity, null);
        if (entity instanceof ServerPlayer player) {
            // Teleport, not a move: the saved position can be anywhere relative to where the hull went.
            player.connection.teleport(at.x, at.y, at.z, yaw, player.getXRot());
            PacketDistributor.sendToPlayer(player,
                    ServerDeckRejoin.place(vehicle.getId(), rejoin.local.x, rejoin.local.y, rejoin.local.z, rejoin.yaw));
        } else {
            entity.moveTo(at.x, at.y, at.z, yaw, entity.getXRot());
            entity.setYHeadRot(yaw);
            entity.setYBodyRot(yaw);
            entity.setDeltaMovement(Vec3.ZERO);
        }
        entity.fallDistance = 0;
        VehicleParenting.attach(vehicle, deck, entity);
    }

    private static void giveUp(Entity entity) {
        set(entity, null);
        if (entity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, ServerDeckRejoin.release());
        }
    }

    // ---------------------------------------------------------------- client

    public static void onClientMessage(Player player, ServerDeckRejoin message) {
        if (message.carrierId() == ServerDeckRejoin.RELEASE) {
            set(player, null);
            return;
        }
        DeckRejoin rejoin = of(player);
        if (rejoin == null) {
            rejoin = new DeckRejoin(null, UNKNOWN_CHUNK);
            set(player, rejoin);
        }
        if (message.carrierId() >= 0) {
            rejoin.carrierId = message.carrierId();
            rejoin.local.set(message.x(), message.y(), message.z());
            rejoin.yaw = message.yaw();
        }
    }

    /** Placed against the hull as this client has it; the server's teleport used the server's pose. */
    private static void tickClient(Player player, DeckRejoin rejoin) {
        if (++rejoin.age > CLIENT_TIMEOUT) {
            set(player, null);
            return;
        }
        if (rejoin.carrierId < 0
                || !(player.level().getEntity(rejoin.carrierId) instanceof DeckBody vehicle)
                || !ready(vehicle)) {
            return;
        }
        DeckSnapshot deck = vehicle.deckSnapshot();
        Vec3 at = VehicleParenting.toWorld(deck, rejoin.local, new Vector3d());
        set(player, null);
        player.moveTo(at.x, at.y, at.z, deck.yaw() + rejoin.yaw, player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        VehicleParenting.attach(vehicle, deck, player);
    }

    /** Travel hook: a waiting rider does not move, and reports ground so it is not kicked for flying. */
    public static boolean hold(LivingEntity entity) {
        if (of(entity) == null) {
            return false;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setOnGround(true);
        entity.fallDistance = 0;
        return true;
    }

}
