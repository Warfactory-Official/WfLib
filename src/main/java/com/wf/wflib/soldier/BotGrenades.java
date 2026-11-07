package com.wf.wflib.soldier;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Only confirmed sights and audible bounces update a soldier's last-known grenade positions. */
final class BotGrenades {
    static final double SIGHT_RANGE = 18, SAFE_DISTANCE = 12;
    static final int MEMORY_TICKS = 100;
    record Notice(Vec3 at, long noticed, long reactAt, String sense) {}
    private final Map<UUID, Notice> known = new HashMap<>();

    static boolean visible(Vec3 eye, Vec3 look, Vec3 at, boolean clear) {
        Vec3 delta = at.subtract(eye);
        return clear && delta.lengthSqr() <= SIGHT_RANGE * SIGHT_RANGE
                && look.dot(delta.normalize()) >= Math.cos(Math.toRadians(70));
    }

    static boolean audible(Vec3 eye, Vec3 at, boolean clear, float volume) {
        double range = 12 * Math.min(1, Math.max(0, volume)) * (clear ? 1 : 0.35);
        return range > 0 && eye.distanceToSqr(at) <= range * range;
    }

    void notice(UUID id, Vec3 at, long now, String sense) {
        Notice previous = known.get(id);
        long reactAt = previous == null ? now + 6 + Math.floorMod(id.hashCode(), 5) : previous.reactAt;
        known.put(id, new Notice(at, now, reactAt, sense));
    }

    void expire(long now, Predicate<UUID> live) {
        known.entrySet().removeIf(e -> now - e.getValue().noticed > MEMORY_TICKS || !live.test(e.getKey()));
    }

    List<Notice> reacting(long now) {
        return known.values().stream().filter(n -> now >= n.reactAt).toList();
    }

    int count() { return known.size(); }
    void forget(UUID id) { known.remove(id); }
    void clear() { known.clear(); }

    static double clearance(Vec3 at, List<Vec3> dangers) {
        return dangers.stream().mapToDouble(at::distanceTo).min().orElse(Double.POSITIVE_INFINITY);
    }
}
