package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A named region a force holds: a vertical cylinder, optionally with a patrol route through it. {@code team} is the side
 * that owns it (empty: anyone's). Soldiers patrol it, guard it, and know when they are standing in it.
 */
public record Territory(String id, String name, String team, String dimension, Vec3 centre, int radius,
                        int minY, int maxY, List<Vec3> route) {
    public static final int MAX_RADIUS = 256, MAX_ROUTE = 32;

    public Territory {
        SoldierTemplate.key(id);
        if (name == null || name.isBlank() || name.length() > 48 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Territory name must be 1-48 printable characters");
        if (team == null || team.length() > 64) throw new IllegalArgumentException("Invalid team");
        if (dimension == null || dimension.isBlank() || dimension.length() > 128) throw new IllegalArgumentException("Invalid dimension");
        if (!Double.isFinite(centre.lengthSqr())) throw new IllegalArgumentException("Finite centre required");
        if (radius < 2 || radius > MAX_RADIUS) throw new IllegalArgumentException("Radius is 2-" + MAX_RADIUS + " blocks");
        if (minY > maxY) throw new IllegalArgumentException("minY above maxY");
        if (route.size() > MAX_ROUTE) throw new IllegalArgumentException("At most " + MAX_ROUTE + " waypoints");
        route = List.copyOf(route);
    }

    public boolean contains(Vec3 p) {
        double dx = p.x - centre.x, dz = p.z - centre.z;
        return dx * dx + dz * dz <= (double) radius * radius && p.y >= minY && p.y <= maxY;
    }

    public Territory withRoute(List<Vec3> waypoints) {
        return new Territory(id, name, team, dimension, centre, radius, minY, maxY, waypoints);
    }

    public Territory withTeam(String owner) {
        return new Territory(id, name, owner, dimension, centre, radius, minY, maxY, route);
    }

    /** A point to walk to: the next waypoint of the route, or a random one in the disc of {@code wander} blocks around the centre. */
    public Vec3 patrolPoint(int index, Vec3 last, RandomSource rng, double wander) {
        if (!route.isEmpty()) return route.get(Math.floorMod(index, route.size()));
        double r = Math.min(radius, wander);
        Vec3 best = centre;
        for (int i = 0; i < 6; i++) {
            double angle = rng.nextDouble() * Math.PI * 2, dist = Math.sqrt(rng.nextDouble()) * r;
            best = new Vec3(centre.x + Math.cos(angle) * dist, centre.y, centre.z + Math.sin(angle) * dist);
            if (last == null || best.distanceToSqr(last) >= r * r * 0.16) break;   // not just a step from where it stands
        }
        return best;
    }

    public JsonObject json() {
        JsonObject j = new JsonObject();
        j.addProperty("id", id);
        j.addProperty("name", name);
        j.addProperty("team", team);
        j.addProperty("dimension", dimension);
        j.add("centre", point(centre));
        j.addProperty("radius", radius);
        j.addProperty("minY", minY);
        j.addProperty("maxY", maxY);
        JsonArray r = new JsonArray();
        route.forEach(v -> r.add(point(v)));
        j.add("route", r);
        return j;
    }

    public static Territory read(JsonObject j) {
        List<Vec3> route = new ArrayList<>();
        j.getAsJsonArray("route").forEach(e -> route.add(vec(e.getAsJsonArray())));
        return new Territory(j.get("id").getAsString(), j.get("name").getAsString(), j.get("team").getAsString(),
                j.get("dimension").getAsString(), vec(j.getAsJsonArray("centre")), j.get("radius").getAsInt(),
                j.get("minY").getAsInt(), j.get("maxY").getAsInt(), route);
    }

    private static JsonArray point(Vec3 v) {
        JsonArray a = new JsonArray();
        a.add(v.x);
        a.add(v.y);
        a.add(v.z);
        return a;
    }

    private static Vec3 vec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
}
