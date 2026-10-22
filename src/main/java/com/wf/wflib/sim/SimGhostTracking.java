package com.wf.wflib.sim;

import com.wf.wflib.MissileEntity;
import com.wf.wflib.WFLib;
import com.wf.wflib.config.ViewRange;
import com.wf.wflib.drone.sim.SimDrone;
import com.wf.wflib.drone.sim.SimDroneManager;
import com.wf.wflib.network.SimGhostSyncPacket;
import com.wf.wflib.network.WFNetwork;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Off-world missiles + drones -> {@link SimGhostSyncPacket} for players within {@link ViewRange#sim}. */
@EventBusSubscriber(modid = WFLib.MODID)
public final class SimGhostTracking {

    private static final Map<ResourceKey<Level>, Map<UUID, Ghost>> LEVELS = new HashMap<>();
    /** Per player: handles its client holds. */
    private static final Map<UUID, IntOpenHashSet> KNOWN = new HashMap<>();
    private static int nextHandle;

    private static final class Ghost {
        final SimGhostSyncPacket.Added add;
        double x, y, z;
        float vx, vy, vz;
        long tick = -1L;
        boolean seen;

        Ghost(SimGhostSyncPacket.Added add) {
            this.add = add;
        }
    }

    private SimGhostTracking() {
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % SimGhostSyncPacket.INTERVAL != 0L) {
            return;
        }
        Map<UUID, Ghost> ghosts = LEVELS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        List<ServerPlayer> players = level.players();
        ghosts.values().forEach(g -> g.seen = false);
        if (!players.isEmpty() && ViewRange.sim > 0.0) {
            for (SimMissile sm : SimMissileManager.tier(level).view()) {
                observe(ghosts, sm.id, sm.pos, now, () -> missile(sm));
                for (SimMissile member : sm.swarmMembers) {
                    observe(ghosts, member.id, sm.pos.add(member.formationOffset), now, () -> missile(member));
                }
            }
            for (SimDrone sd : SimDroneManager.tier(level).view()) {
                observe(ghosts, sd.id, sd.pos, now, () -> new SimGhostSyncPacket.Added(nextHandle++, sd.id,
                        SimGhostSyncPacket.Kind.DRONE, sd.modelId, 0));
            }
        }
        ghosts.values().removeIf(g -> !g.seen);

        double r2 = ViewRange.sim * ViewRange.sim;
        for (ServerPlayer player : players) {
            IntOpenHashSet known = KNOWN.getOrDefault(player.getUUID(), new IntOpenHashSet());
            IntOpenHashSet visible = new IntOpenHashSet();
            List<SimGhostSyncPacket.Added> added = new ArrayList<>();
            List<SimGhostSyncPacket.Moved> moved = new ArrayList<>();
            for (Ghost g : ghosts.values()) {
                if (player.distanceToSqr(g.x, g.y, g.z) > r2) {
                    continue;
                }
                visible.add(g.add.handle());
                if (!known.contains(g.add.handle())) {
                    added.add(g.add);
                }
                moved.add(new SimGhostSyncPacket.Moved(g.add.handle(), g.x, g.y, g.z, g.vx, g.vy, g.vz));
            }
            int[] removed = known.intStream().filter(h -> !visible.contains(h)).toArray();
            if (!moved.isEmpty() || removed.length > 0) {
                WFNetwork.sendToPlayer(player, new SimGhostSyncPacket(added, moved, removed));
            }
            KNOWN.put(player.getUUID(), visible);
        }
    }

    /** Sim UUID behind {@code handle}, if the player was sent it. */
    @Nullable
    static UUID sim(ServerPlayer player, int handle) {
        IntOpenHashSet known = KNOWN.get(player.getUUID());
        Map<UUID, Ghost> ghosts = LEVELS.get(player.level().dimension());
        if (known == null || ghosts == null || !known.contains(handle)) {
            return null;
        }
        for (Ghost g : ghosts.values()) {
            if (g.add.handle() == handle) {
                return g.add.sim();
            }
        }
        return null;
    }

    private static void observe(Map<UUID, Ghost> ghosts, UUID id, Vec3 pos, long now,
                                Supplier<SimGhostSyncPacket.Added> describe) {
        Ghost g = ghosts.computeIfAbsent(id, k -> new Ghost(describe.get()));
        if (g.tick >= 0L && now > g.tick) {
            float dt = now - g.tick;
            g.vx = (float) (pos.x - g.x) / dt;
            g.vy = (float) (pos.y - g.y) / dt;
            g.vz = (float) (pos.z - g.z) / dt;
        }
        g.x = pos.x;
        g.y = pos.y;
        g.z = pos.z;
        g.tick = now;
        g.seen = true;
    }

    private static SimGhostSyncPacket.Added missile(SimMissile sm) {
        int exhaust = sm.body != null && sm.body.contains("ExhaustColor") ? sm.body.getInt("ExhaustColor")
                : MissileEntity.DEFAULT_EXHAUST_COLOR;
        return new SimGhostSyncPacket.Added(nextHandle++, sm.id, SimGhostSyncPacket.Kind.MISSILE, sm.modelId, exhaust);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        KNOWN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LEVELS.clear();
        KNOWN.clear();
    }
}
