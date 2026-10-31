package com.wf.wflib.drone.api;

import com.mojang.logging.LogUtils;
import com.wf.wflib.missile.api.WFEventType;
import com.wf.wflib.core.api.ViewRange;
import com.wf.wflib.sim.api.SimKind;
import com.wf.wflib.sim.api.SimTargets;
import com.wf.wflib.sim.api.SimTier;
import com.wf.wflib.sim.api.SimWorld;
import com.wf.wflib.missile.api.WFTelemetryService;
import com.wf.wflib.cam.api.CameraNet;
import com.wf.wflib.missile.api.MissileListenerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.core.api.WFLib;

/** Drones entity <-> {@link SimDrone} record. Records keep flying on the real AI ({@link DroneAiScheduler}). */
public final class SimDroneManager implements SimKind<SimDrone> {

    public static final SimDroneManager KIND = new SimDroneManager();

    /**
     * Slack between the offload and onload ranges so a drone near the boundary doesn't flicker.
     */
    public static final double ONLOAD_MARGIN = 32.0;
    /**
     * Distance from its waypoint at which a simulated drone must become real again, so the delivery, the landing
     * and the crate all happen in a loaded world.
     */
    public static final double WAYPOINT_ONLOAD_RANGE = 96.0;
    /**
     * Ticks a drone must be airborne and travelling before it may offload, so it doesn't offload during the first
     * moments of a launch.
     */
    public static final int OFFLOAD_DELAY_TICKS = 40;
    /** How long a camera drone's feed must have gone unwatched before the drone may leave the world. */
    public static final int CAMERA_QUIET_TICKS = 200;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Testing switch: with this off, drones never leave the world for the simulation. */
    private static volatile boolean offloadEnabled = true;

    public static boolean isOffloadEnabled() {
        return offloadEnabled;
    }

    public static void setOffloadEnabled(boolean on) {
        offloadEnabled = on;
    }

    private SimDroneManager() {
    }

    public static SimTier<SimDrone> tier(ServerLevel level) {
        return SimWorld.get(level).tier(KIND);
    }

    public static void collect(ServerLevel level, List<DroneCarrier> out) {
        out.addAll(tier(level).view());
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "drone");
    }

    @Override
    public CompoundTag save(SimDrone record) {
        return record.save();
    }

    @Override
    public SimDrone load(CompoundTag tag) {
        return SimDrone.load(tag);
    }

    @Override
    public void resolve(ServerLevel level, SimTier<SimDrone> tier) {
        offloadEligible(level);
        onloadEligible(level);
    }

    private static void offloadEligible(ServerLevel level) {
        List<DroneEntity> drones = new ArrayList<>(DroneTracker.drones(level));
        for (DroneEntity drone : drones) {
            if (!canOffload(level, drone)) {
                continue;
            }
            SimDrone sd = SimDrone.fromEntity(drone, drone.hold().hasCargo() ? drone.hold().saveCargo() : null);
            tier(level).add(sd);
            drone.recordEvent(WFEventType.OFFLOAD, "to drone sim");
            LOGGER.debug("[wflib] drone {} offloaded to simulation at {}", sd.id, sd.pos);
            drone.leaveWorld();
        }
    }

    private static boolean canOffload(ServerLevel level, DroneEntity drone) {
        if (!offloadEnabled) {
            return false;
        }
        if (!drone.isAlive() || drone.isRemoved() || SimTargets.kept(drone)) {
            return false;
        }
        if (drone.cameraSpec() != null
                && CameraNet.quietFor(level, drone.getId()) < CAMERA_QUIET_TICKS) {
            return false;
        }
        if (drone.orders().getExchangeId() != null || drone.orders().isClassified()) {
            return false;
        }
        DroneState state = drone.flight().getDroneState();
        if (state != DroneState.TRANSIT && state != DroneState.EXFIL) {
            return false;
        }
        if (drone.tickCount < OFFLOAD_DELAY_TICKS) {
            return false;
        }
        Vec3 pos = drone.position();
        if (horizontalDistance(pos, waypoint(drone)) <= WAYPOINT_ONLOAD_RANGE + ONLOAD_MARGIN) {
            return false;
        }
        return !nearWatcher(level, pos, ViewRange.blocks());
    }

    private static void onloadEligible(ServerLevel level) {
        SimTier<SimDrone> registry = tier(level);
        List<SimDrone> all = new ArrayList<>(registry.view());
        for (SimDrone sd : all) {
            boolean travelling = sd.state == DroneState.TRANSIT || sd.state == DroneState.EXFIL;
            boolean nearWaypoint = horizontalDistance(sd.pos, waypoint(sd)) <= WAYPOINT_ONLOAD_RANGE;
            boolean watched = nearWatcher(level, sd.pos, ViewRange.blocks() - ONLOAD_MARGIN);
            if (travelling && !nearWaypoint && !watched) {
                continue;
            }
            respawn(level, sd);
            registry.remove(sd);
        }
    }

    @Override
    public boolean wake(ServerLevel level, java.util.UUID id) {
        return onload(level, id);
    }

    @Override
    public void wakeWhere(ServerLevel level, java.util.function.Predicate<net.minecraft.world.phys.Vec3> where) {
        List<java.util.UUID> hit = new ArrayList<>();
        for (SimDrone sd : tier(level).view()) {
            if (where.test(sd.pos)) {
                hit.add(sd.id);
            }
        }
        for (java.util.UUID id : hit) {
            SimTargets.onload(level, id);
        }
    }

    /**
     * Bring one drone back into the world because somebody asked to see through it.
     *
     * @return true if it was off-world and is now a real entity again. False means it was never in the
     *      simulation, and the caller should try the slower recovery.
     */
    public static boolean onload(ServerLevel level, java.util.UUID id) {
        SimTier<SimDrone> registry = tier(level);
        SimDrone sd = registry.find(d -> d.id.equals(id));
        if (sd == null) {
            return false;
        }
        DroneEntity drone = respawn(level, sd);
        registry.remove(sd);
        if (drone.cameraSpec() != null) {
            CameraNet.markWatched(level, drone.getId());
        }
        return true;
    }

    private static DroneEntity respawn(ServerLevel level, SimDrone sd) {
        DroneEntity drone = sd.toEntity(level, sd.pos);
        ChunkPos cp = drone.chunkPosition();
        MissileListenerData.CHUNK_TICKET.forceChunk(level, drone, cp.x, cp.z, true, true);
        level.getChunk(cp.x, cp.z);
        level.addFreshEntity(drone);
        WFTelemetryService.record(sd.id, WFEventType.ONLOAD, level.getGameTime(), sd.pos, false, "from drone sim");
        LOGGER.debug("[wflib] simulated drone {} respawned at {}", sd.id, sd.pos);
        return drone;
    }

    /**
     * @return true if a player is close enough that the drone should be a real, visible entity.
     */
    private static boolean nearWatcher(ServerLevel level, Vec3 pos, double range) {
        double r2 = range * range;
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(pos) <= r2) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the waypoint of the leg currently being flown. On the way home that is the exfil point, not
     *      the destination the drone still remembers: using the wrong one leaves a returning drone simulated
     *      right through its own landing.
     */
    private static Vec3 waypoint(DroneState state, @Nullable Vec3 destination, Vec3 exfil) {
        return (state == DroneState.EXFIL || destination == null) ? exfil : destination;
    }

    private static Vec3 waypoint(DroneEntity drone) {
        return waypoint(drone.flight().getDroneState(), drone.route().getDestination(), drone.route().getExfil());
    }

    private static Vec3 waypoint(SimDrone sd) {
        return waypoint(sd.state, sd.destination, sd.exfil);
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

}
