package com.wf.wflib.round.api;

import com.wf.wflib.round.RoundPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rounds on the wire: one spawn batch and one end batch per player per tick. Client flies the same recurrence; an
 * end reaching the client before the round's first client tick = its whole life, replayed as one segment.
 */
final class RoundNetwork {

    private static final Map<ResourceKey<Level>, Map<ServerPlayer, Outbox>> PENDING = new HashMap<>();

    private RoundNetwork() {
    }

    private static final class Outbox {
        final List<RoundSpawn> spawns = new ArrayList<>();
        final List<RoundEnded> ends = new ArrayList<>();
        final List<RoundPierce> pierces = new ArrayList<>();
        final List<RoundRicochet> ricochets = new ArrayList<>();
    }

    static void pierced(ServerLevel level, RoundBatch b, int i, BlockHitResult entry, BlockPen.Pass pass) {
        Vec3 at = entry.getLocation();
        RoundPierce p = new RoundPierce(b.key[i], entry.getBlockPos().asLong(), at.x, at.y, at.z, entry.getDirection(),
                pass.point().x, pass.point().y, pass.point().z, pass.exited());
        for (ServerPlayer player : recipients(level, b, i)) {
            outbox(level, player).pierces.add(p);
        }
    }

    static void ricocheted(ServerLevel level, RoundBatch b, int i, Vec3 at, Vec3 n, RoundSurface surface, long block) {
        RoundRicochet r = new RoundRicochet(b.key[i], at.x, at.y, at.z, (float) n.x, (float) n.y, (float) n.z, surface, block);
        for (ServerPlayer player : recipients(level, b, i)) {
            outbox(level, player).ricochets.add(r);
        }
    }

    /** Spawn, or a state change the client cannot predict (landing, burrowing, pierce, ricochet): replaces its copy. */
    static void sync(ServerLevel level, RoundBatch b, int i) {
        KineticPreset preset = b.preset[i];
        RoundSpawn spawn = new RoundSpawn(b.key[i], preset.id(), b.x[i], b.y[i], b.z[i], b.vx[i], b.vy[i], b.vz[i], b.life[i],
                b.rest[i] >= 0, b.shooter[i], b.seq[i], b.ricochets[i]);
        List<ServerPlayer> to = recipients(level, b, i);
        UUID[] viewers = new UUID[to.size()];
        for (int k = 0; k < viewers.length; k++) {
            ServerPlayer player = to.get(k);
            outbox(level, player).spawns.add(spawn);
            viewers[k] = player.getUUID();
        }
        b.viewers[i] = viewers;
    }

    static void ended(ServerLevel level, RoundBatch b, int i, Vec3 at, RoundEnd reason) {
        RoundEnded end = new RoundEnded(b.key[i], at.x, at.y, at.z, reason);
        for (ServerPlayer player : recipients(level, b, i)) {
            outbox(level, player).ends.add(end);
        }
    }

    /** Audience of round {@code i} now, plus its viewers still in the level. */
    private static List<ServerPlayer> recipients(ServerLevel level, RoundBatch b, int i) {
        List<ServerPlayer> out = Rounds.audience(level, b.preset[i], b.x[i], b.z[i]);
        for (UUID id : b.viewers[i]) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer player && !out.contains(player)) {
                out.add(player);
            }
        }
        return out;
    }

    private static Outbox outbox(ServerLevel level, ServerPlayer player) {
        return PENDING.computeIfAbsent(level.dimension(), k -> new IdentityHashMap<>())
                .computeIfAbsent(player, p -> new Outbox());
    }

    static void flush(ServerLevel level) {
        Map<ServerPlayer, Outbox> byPlayer = PENDING.remove(level.dimension());
        if (byPlayer == null) {
            return;
        }
        for (Map.Entry<ServerPlayer, Outbox> e : byPlayer.entrySet()) {
            // Unnegotiated channel => send throws; FakePlayer connection has no netty channel => hasChannel NPEs.
            if (!(e.getKey() instanceof FakePlayer) && e.getKey().connection.hasChannel(RoundPacket.TYPE)) {
                Outbox box = e.getValue();
                Rounds.send(e.getKey(), new RoundPacket(box.spawns, box.ends, box.pierces, box.ricochets));
            }
        }
    }
}
