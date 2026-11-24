package com.wf.wflib.missile.api;

import com.wf.wflib.missile.debug.MissileDebug;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WFTelemetryService {

    public static final int MAX_TRACKED = 128;

    /** Lock-free reads; ORDER (eldest first) guarded by itself, mutations of both under it. */
    private static final ConcurrentHashMap<UUID, WFTelemetry> TRACKED = new ConcurrentHashMap<>();
    private static final ArrayDeque<UUID> ORDER = new ArrayDeque<>();

    private static volatile boolean autoOpen = false;

    private WFTelemetryService() {
    }

    public static boolean autoOpen() {
        return autoOpen;
    }

    public static void setAutoOpen(boolean value) {
        autoOpen = value;
    }

    public static WFTelemetry open(UUID id, long gameTime) {
        WFTelemetry existing = TRACKED.get(id);
        if (existing != null) {
            return existing;
        }
        synchronized (ORDER) {
            existing = TRACKED.get(id);
            if (existing != null) {
                return existing;
            }
            if (ORDER.size() >= MAX_TRACKED) {
                TRACKED.remove(ORDER.pollFirst());
            }
            WFTelemetry created = new WFTelemetry(id, gameTime);
            TRACKED.put(id, created);
            ORDER.addLast(id);
            return created;
        }
    }

    public static WFTelemetry get(UUID id) {
        return TRACKED.get(id);
    }

    public static boolean isTracked(UUID id) {
        return TRACKED.containsKey(id);
    }

    public static Collection<WFTelemetry> all() {
        synchronized (ORDER) {
            List<WFTelemetry> out = new ArrayList<>(ORDER.size());
            for (UUID id : ORDER) {
                out.add(TRACKED.get(id));
            }
            return out;
        }
    }

    public static void close(UUID id) {
        synchronized (ORDER) {
            if (TRACKED.remove(id) != null) {
                ORDER.remove(id);
            }
        }
    }

    public static void clear() {
        synchronized (ORDER) {
            TRACKED.clear();
            ORDER.clear();
        }
    }

    public static void record(WFTelemetry cached, UUID id, WFEventType type, long gameTime, Vec3 pos,
                              boolean simulated, String detail) {
        if (cached != null) {
            cached.record(type, gameTime, pos, simulated, detail);
        }
        MissileDebug.onEvent(id, type, gameTime, pos, simulated, detail);
    }

    public static void record(UUID id, WFEventType type, long gameTime, Vec3 pos, boolean simulated,
                              String detail) {
        record(TRACKED.get(id), id, type, gameTime, pos, simulated, detail);
    }
}
