package com.wf.wflib.drone.api;

import com.mojang.logging.LogUtils;
import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.core.api.WorkerPool;
import com.wf.wflib.core.api.WorldThread;
import com.wf.wflib.drone.nav.PlannerStats;
import com.wf.wflib.drone.nav.TerrainCache;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

/** Drives the drone AI for one dimension, one tick at a time. */
public final class DroneAiScheduler {

    /** Ticks a plan stays usable. */
    public static final int MAX_PLAN_AGE = 4;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, DroneAiScheduler> BY_LEVEL = new HashMap<>();
    /** Places drones live; descending priority. */
    public static final OrderedRegistry<DroneCarrierSource> SOURCES = OrderedRegistry.create("drone", "drone.carrier_sources");


    /** Plans waiting to be applied, keyed by drone. */
    private final Map<UUID, DronePlan> ready = new HashMap<>();
    /**
     * In-flight squad jobs, keyed by the leader's id. A squad is never re-dispatched while planning.
     */
    private final Map<UUID, Future<SquadPlan>> inFlight = new HashMap<>();
    /** Each squad's reference frame as of the last plan that landed, keyed by squad. */
    private final Map<Long, SquadAnchor> anchors = new HashMap<>();
    /** In-flight route searches, keyed by drone. */
    private final Map<UUID, Future<DronePath>> pathJobs = new HashMap<>();

    private DroneAiScheduler() {
    }

    /** Start of a server: planner counters reset. */
    public static void startup() {
        BY_LEVEL.clear();
        PlannerStats.reset();
    }

    public static void shutdown() {
        BY_LEVEL.clear();
        TerrainCache.clear();
    }

    public static void tick(ServerLevel level) {
        TerrainCache.beginTick(level);
        List<DroneCarrier> carriers = new ArrayList<>();
        for (DroneCarrierSource source : SOURCES.values()) {
            source.collect(level, carriers);
        }
        DroneAiScheduler scheduler = BY_LEVEL.get(level.dimension());
        if (carriers.isEmpty()) {
            if (scheduler != null && scheduler.isQuiet()) {
                BY_LEVEL.remove(level.dimension());
            }
            return;
        }
        if (scheduler == null) {
            scheduler = new DroneAiScheduler();
            BY_LEVEL.put(level.dimension(), scheduler);
        }
        scheduler.run(level, carriers);
    }

    /**
     * @return true if there is no work outstanding, so this dimension's scheduler can be dropped.
     */
    private boolean isQuiet() {
        return ready.isEmpty() && inFlight.isEmpty() && pathJobs.isEmpty();
    }

    private void run(ServerLevel level, List<DroneCarrier> carriers) {
        long now = level.getGameTime();
        collectFinished();
        collectRoutes(carriers);
        applyPlans(level, carriers, now);
        dispatch(level, carriers, now);
    }

    /**
     * Hand out routes from searches that have finished, whenever they finish.
     */
    private void collectRoutes(List<DroneCarrier> carriers) {
        if (pathJobs.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Future<DronePath>>> it = pathJobs.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Future<DronePath>> entry = it.next();
            if (!entry.getValue().isDone()) {
                continue;
            }
            it.remove();
            try {
                DronePath path = entry.getValue().get();
                if (path == null) {
                    continue;
                }
                for (DroneCarrier carrier : carriers) {
                    if (carrier.droneId().equals(entry.getKey()) && carrier.carrierAlive()) {
                        carrier.adoptPath(path);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.error("[wflib] drone route search failed for {}", entry.getKey(), e);
            }
        }
    }

    /** Drain jobs that finished since last tick. */
    private void collectFinished() {
        for (Iterator<Map.Entry<UUID, Future<SquadPlan>>> it = inFlight.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Future<SquadPlan>> entry = it.next();
            if (!entry.getValue().isDone()) {
                continue;
            }
            it.remove();
            try {
                SquadPlan squadPlan = entry.getValue().get();
                for (DronePlan plan : squadPlan.plans()) {
                    ready.put(plan.droneId(), plan);
                }
                if (squadPlan.squadId() != 0L && squadPlan.anchor() != null) {
                    anchors.put(squadPlan.squadId(), squadPlan.anchor());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.error("[wflib] drone squad planning failed for leader {}", entry.getKey(), e);
            }
        }
    }

    private void applyPlans(ServerLevel level, List<DroneCarrier> carriers, long now) {
        for (DroneCarrier carrier : carriers) {
            if (!carrier.carrierAlive()) {
                ready.remove(carrier.droneId());
                continue;
            }
            DronePlan plan = ready.remove(carrier.droneId());
            if (plan != null && now - plan.sourceGameTime() <= MAX_PLAN_AGE) {
                carrier.apply(level, plan);
            } else {
                carrier.coast(level);
            }
        }
        if (ready.size() > carriers.size()) {
            ready.keySet().removeIf(id -> carriers.stream().noneMatch(c -> c.droneId().equals(id)));
        }
    }

    private void dispatch(ServerLevel level, List<DroneCarrier> carriers, long now) {
        if (WorkerPool.pool() == null) {
            return;
        }
        for (SquadView squad : buildSquads(level, carriers, now)) {
            for (DroneSnapshot member : squad.slots()) {
                if (member.nav().canPlan() && !pathJobs.containsKey(member.id())) {
                    pathJobs.put(member.id(), WorkerPool.pool().submit(() -> DroneBrain.planRoute(member)));
                }
            }
            UUID key = squad.leader().id();
            if (inFlight.containsKey(key)) {
                continue;
            }
            inFlight.put(key, WorkerPool.pool().submit(() -> DroneBrain.planSquad(squad)));
        }
    }

    /**
     * @return this dimension's squads as the planner would see them this tick, for a diagnostic to read.
     */
    public static List<SquadView> squadsFor(ServerLevel level) {
        WorldThread.assertOn("squad diagnostics");
        List<DroneCarrier> carriers = new ArrayList<>();
        for (DroneCarrierSource source : SOURCES.values()) {
            source.collect(level, carriers);
        }
        DroneAiScheduler scheduler = BY_LEVEL.get(level.dimension());
        if (scheduler == null || carriers.isEmpty()) {
            return List.of();
        }
        return scheduler.buildSquads(level, carriers, level.getGameTime());
    }

    /** Group live carriers into squads and snapshot them. */
    private List<SquadView> buildSquads(ServerLevel level, List<DroneCarrier> carriers, long now) {
        Map<Long, List<DroneCarrier>> bySquad = new LinkedHashMap<>();
        List<DroneCarrier> solo = new ArrayList<>();
        for (DroneCarrier carrier : carriers) {
            if (!carrier.carrierAlive()) {
                continue;
            }
            if (carrier.squadId() == 0L) {
                solo.add(carrier);
            } else {
                bySquad.computeIfAbsent(carrier.squadId(), k -> new ArrayList<>()).add(carrier);
            }
        }

        List<SquadView> out = new ArrayList<>(bySquad.size() + solo.size());
        for (DroneCarrier carrier : solo) {
            DroneSnapshot snapshot = carrier.snapshot(level, now);
            out.add(SquadView.solo(snapshot));
        }
        for (Map.Entry<Long, List<DroneCarrier>> entry : bySquad.entrySet()) {
            List<DroneCarrier> members = entry.getValue();
            promoteIfLeaderless(level.getRandom(), members);
            members.sort(Comparator.comparing((DroneCarrier c) -> !c.leader())
                    .thenComparing(c -> c.droneId().toString()));
            List<DroneSnapshot> snapshots = new ArrayList<>(members.size());
            for (DroneCarrier member : members) {
                snapshots.add(member.snapshot(level, now));
            }
            out.add(new SquadView(entry.getKey(), members.get(0).formationId(),
                    Formation.clampSpacing(members.get(0).formationSpacing()),
                    members.get(0).coordinationId(), anchors.get(entry.getKey()), snapshots.get(0),
                    List.copyOf(snapshots)));
        }
        anchors.keySet().retainAll(bySquad.keySet());
        return out;
    }

    /**
     * Hand a leaderless squad to a survivor picked at random, mirroring {@code SwarmManager.promoteSuccessor} on
     * the missile side: losing the drone that was leading is not supposed to end the mission for everyone behind
     * it.
     */
    public static void promoteIfLeaderless(RandomSource random, List<DroneCarrier> members) {
        if (members.isEmpty()) {
            return;
        }
        for (DroneCarrier member : members) {
            if (member.leader()) {
                return;
            }
        }
        members.get(random.nextInt(members.size())).promote();
    }

    /**
     * Where {@link DroneAiScheduler} finds drones. Implementations are called on the world thread.
     */
    public interface DroneCarrierSource {
        void collect(ServerLevel level, List<DroneCarrier> out);
    }
}
