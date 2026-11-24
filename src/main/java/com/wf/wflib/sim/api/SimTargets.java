package com.wf.wflib.sim.api;

import com.wf.wflib.missile.api.SimGhostSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import com.wf.wflib.missile.api.SimGhosts;
import com.wf.wflib.missile.api.SimMissile;
import com.wf.wflib.missile.api.SimMissileManager;

/**
 * Locks on off-world missiles/drones: lock => {@link #onload}; tracked entity {@link #keep}s => no offload while a
 * lock or seeker holds it. Client ghosts carry id {@link SimGhostSyncPacket#GHOST_ID_BASE} + handle =>
 * {@link #resolve} for a client-picked target id.
 */
public final class SimTargets {

    /** Ticks a {@link #keep} lasts; refreshed every tick by holders. */
    public static final int KEEP_TICKS = 40;

    private static final ConcurrentHashMap<UUID, AtomicLong> KEPT = new ConcurrentHashMap<>();

    private SimTargets() {
    }

    /** Client-sent target id -> entity; ghost id the player was sent => sim onloaded first. */
    @Nullable
    public static Entity resolve(ServerPlayer player, int id) {
        ServerLevel level = player.serverLevel();
        if (!SimGhostSyncPacket.isGhostId(id)) {
            return level.getEntity(id);
        }
        UUID sim = SimGhosts.simOf(player, id - SimGhostSyncPacket.GHOST_ID_BASE);
        return sim == null ? null : onload(level, sim);
    }

    /** Every sim at a position {@code where} accepts -> real entity; for server-side lock scans. */
    public static void onloadWhere(ServerLevel level, Predicate<Vec3> where) {
        List<UUID> hit = new ArrayList<>();
        for (SimMissile sm : SimMissileManager.tier(level).view()) {
            if (where.test(sm.pos)) {
                hit.add(sm.id);
                continue;
            }
            for (SimMissile member : sm.swarmMembers) {
                if (where.test(sm.pos.add(member.formationOffset))) {
                    hit.add(member.id);
                    break;
                }
            }
        }
        for (UUID id : hit) {
            onload(level, id);
        }
        for (SimKind<?> kind : SimKinds.all()) {
            kind.wakeWhere(level, where);
        }
    }

    /** Sim (missile, swarm member => whole formation, drone) -> real entity with the same UUID, kept. */
    @Nullable
    public static Entity onload(ServerLevel level, UUID id) {
        if (!SimMissileManager.onload(level, id) && !wakeKind(level, id)) {
            return null;
        }
        Entity e = level.getEntity(id);
        if (e != null) {
            keep(e);
        }
        return e;
    }

    private static boolean wakeKind(ServerLevel level, UUID id) {
        for (SimKind<?> kind : SimKinds.all()) {
            if (kind.wake(level, id)) {
                return true;
            }
        }
        return false;
    }

    public static void keep(Entity e) {
        long until = e.level().getGameTime() + KEEP_TICKS;
        AtomicLong slot = KEPT.get(e.getUUID());
        if (slot == null) {
            slot = KEPT.putIfAbsent(e.getUUID(), new AtomicLong(until));
            if (slot == null) {
                return;
            }
        }
        slot.lazySet(until);
    }

    public static boolean kept(Entity e) {
        AtomicLong until = KEPT.get(e.getUUID());
        return until != null && until.get() > e.level().getGameTime();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        if (now % KEEP_TICKS == 0L) {
            KEPT.values().removeIf(until -> until.get() <= now);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        KEPT.clear();
    }
}
