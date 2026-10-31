package com.wf.wflib.physics.api;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of carrier vehicles per level. Copy-on-write: written on tick thread only, off-thread
 * solves read frozen snapshots.
 */
public final class CarrierDecks {

    private static final Map<Level, List<DeckBody>> BY_LEVEL = new ConcurrentHashMap<>();

    private CarrierDecks() {}

    /** Idempotent: repeat calls with the same flag are no-ops. */
    public static void setRegistered(DeckBody vehicle, boolean carrier) {
        Level level = vehicle.level();
        List<DeckBody> carriers = BY_LEVEL.get(level);
        if (!carrier) {
            if (carriers != null && carriers.contains(vehicle)) {
                remove(level, vehicle);
            }
            return;
        }
        if (carriers != null && carriers.contains(vehicle)) {
            return;
        }
        add(level, vehicle);
    }

    public static void unregister(DeckBody vehicle) {
        remove(vehicle.level(), vehicle);
    }

    /** {@link com.wf.wflib.physics.api.event.PhysicsLevelUnloadEvent}. */
    public static void forget(Level level) {
        BY_LEVEL.remove(level);
    }

    private static synchronized void add(Level level, DeckBody vehicle) {
        List<DeckBody> current = BY_LEVEL.get(level);
        List<DeckBody> next = current == null ? new ArrayList<>(2) : new ArrayList<>(current);
        if (next.contains(vehicle)) {
            return;
        }
        next.add(vehicle);
        BY_LEVEL.put(level, List.copyOf(next));
    }

    private static synchronized void remove(Level level, DeckBody vehicle) {
        List<DeckBody> current = BY_LEVEL.get(level);
        if (current == null || !current.contains(vehicle)) {
            return;
        }
        List<DeckBody> next = new ArrayList<>(current);
        next.remove(vehicle);
        if (next.isEmpty()) {
            BY_LEVEL.remove(level);
        } else {
            BY_LEVEL.put(level, List.copyOf(next));
        }
    }

    /** Never null; empty when none registered. */
    public static List<DeckBody> in(Level level) {
        List<DeckBody> carriers = BY_LEVEL.get(level);
        return carriers == null ? Collections.emptyList() : carriers;
    }

    public static boolean any(Level level) {
        return BY_LEVEL.containsKey(level);
    }

    /**
     * Most overlap first. Tick thread only; returned list is reused, valid only until the next call.
     * Excludes vehicle and anything parked on it (a parked carrier must not become its parent's floor).
     */
    public static List<DeckBody> overlapping(DeckBody vehicle, AABB region) {
        List<DeckBody> carriers = in(vehicle.level());
        List<DeckBody> out = CANDIDATES.get();
        out.clear();
        double[] overlaps = OVERLAPS.get();
        for (int i = 0, size = carriers.size(); i < size; i++) {
            DeckBody carrier = carriers.get(i);
            if (carrier == vehicle || carrier.isRemoved() || !carrier.collides()
                    || carrier.level() != vehicle.level()
                    || carrier.mountedOn() == vehicle) {
                continue;
            }
            double overlap = overlapVolume(carrier.getBoundingBox(), region);
            if (overlap <= 0) {
                continue;
            }
            if (out.size() == overlaps.length) {
                overlaps = java.util.Arrays.copyOf(overlaps, overlaps.length * 2);
                OVERLAPS.set(overlaps);
            }
            int at = out.size();
            out.add(carrier);
            while (at > 0 && overlaps[at - 1] < overlap) {
                overlaps[at] = overlaps[at - 1];
                out.set(at, out.get(at - 1));
                at--;
            }
            overlaps[at] = overlap;
            out.set(at, carrier);
        }
        return out;
    }

    // Client and server tick threads differ in single player.
    private static final ThreadLocal<List<DeckBody>> CANDIDATES = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<double[]> OVERLAPS = ThreadLocal.withInitial(() -> new double[4]);

    private static double overlapVolume(AABB a, AABB b) {
        double x = Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX);
        if (x <= 0) {
            return 0;
        }
        double y = Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY);
        if (y <= 0) {
            return 0;
        }
        double z = Math.min(a.maxZ, b.maxZ) - Math.max(a.minZ, b.minZ);
        return z <= 0 ? 0 : x * y * z;
    }

}
