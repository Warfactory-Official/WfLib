package com.wf.wflib.soldier;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where a soldier tick goes: rays, searches and time per phase, over the last {@link #WINDOW} ticks. Server thread only.
 * It is what decides which part of the AI to move off the thread first.
 */
public final class SoldierProfiler {
    static final int WINDOW = 400;

    // counters of the tick in progress
    static long rays, routes, nodes, covers, routeNanos, coverNanos, driveNanos;

    private static final long[] TICK = new long[WINDOW], DRIVE = new long[WINDOW], ROUTE = new long[WINDOW],
            COVER = new long[WINDOW], RAYS = new long[WINDOW], NODES = new long[WINDOW], ROUTES = new long[WINDOW],
            COVERS = new long[WINDOW], SOLDIERS = new long[WINDOW];
    private static int at, filled;
    /** Named sections of the tick: nanos of the tick in progress, and a ring of past ticks per section. */
    private static final Map<String, long[]> SECTIONS = new LinkedHashMap<>();
    private static final Map<String, long[]> SECTION_RING = new LinkedHashMap<>();

    static long begin() {
        return System.nanoTime();
    }

    /** For the soldier body (the player tick), which runs inside the level tick before the AI's handler. */
    public static void record(String section, long nanos) {
        SECTIONS.computeIfAbsent(section, k -> new long[1])[0] += nanos;
    }

    static void end(String section, long start) {
        SECTIONS.computeIfAbsent(section, k -> new long[1])[0] += System.nanoTime() - start;
    }

    private SoldierProfiler() {
    }

    /** Close the tick: {@code total} nanos spent in the AI's tick handler for {@code soldiers} soldiers. */
    static void endTick(long total, int soldiers) {
        int i = at;
        TICK[i] = total;
        DRIVE[i] = driveNanos;
        ROUTE[i] = routeNanos;
        COVER[i] = coverNanos;
        RAYS[i] = rays;
        NODES[i] = nodes;
        ROUTES[i] = routes;
        COVERS[i] = covers;
        SOLDIERS[i] = soldiers;
        for (Map.Entry<String, long[]> e : SECTIONS.entrySet()) {
            SECTION_RING.computeIfAbsent(e.getKey(), k -> new long[WINDOW])[i] = e.getValue()[0];
            e.getValue()[0] = 0;
        }
        for (Map.Entry<String, long[]> e : SECTION_RING.entrySet()) {
            if (!SECTIONS.containsKey(e.getKey())) e.getValue()[i] = 0;
        }
        at = (at + 1) % WINDOW;
        filled = Math.min(WINDOW, filled + 1);
        rays = routes = nodes = covers = routeNanos = coverNanos = driveNanos = 0;
    }

    private static double ms(long[] a, int n) {
        return Arrays.stream(a, 0, n).average().orElse(0) / 1e6;
    }

    private static double avg(long[] a, int n) {
        return Arrays.stream(a, 0, n).average().orElse(0);
    }

    /** Averages over the window, the worst tick and the 95th percentile of the tick handler. */
    public static JsonObject report(boolean reset) {
        int n = filled;
        JsonObject o = new JsonObject();
        o.addProperty("ticks", n);
        if (n == 0) return o;
        long[] sorted = Arrays.copyOf(TICK, n);
        Arrays.sort(sorted);
        o.addProperty("soldiers_avg", Math.round(avg(SOLDIERS, n) * 10) / 10.0);
        o.addProperty("tick_ms_avg", round(ms(TICK, n)));
        o.addProperty("tick_ms_p95", round(sorted[(int) (n * 0.95) - (n > 20 ? 1 : 0)] / 1e6));
        o.addProperty("tick_ms_max", round(sorted[n - 1] / 1e6));
        o.addProperty("drive_ms_avg", round(ms(DRIVE, n)));
        o.addProperty("route_ms_avg", round(ms(ROUTE, n)));
        o.addProperty("cover_ms_avg", round(ms(COVER, n)));
        o.addProperty("rays_per_tick", Math.round(avg(RAYS, n)));
        o.addProperty("routes_per_tick", round(avg(ROUTES, n)));
        o.addProperty("route_nodes_per_tick", Math.round(avg(NODES, n)));
        o.addProperty("cover_searches_per_tick", round(avg(COVERS, n)));
        JsonObject sections = new JsonObject();
        for (Map.Entry<String, long[]> e : SECTION_RING.entrySet()) sections.addProperty(e.getKey(), round(ms(e.getValue(), n)));
        o.add("sections_ms_avg", sections);
        if (reset) {
            at = filled = 0;
            SECTION_RING.values().forEach(r -> Arrays.fill(r, 0));
        }
        return o;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
