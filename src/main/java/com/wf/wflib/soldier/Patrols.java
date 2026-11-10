package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Patrol and guard: walk a territory (its route, or points picked inside it), stop and look about at each, go on.
 * Fighting is not this class's business: while anything threatens the soldier it leaves the movement to the AI,
 * and the walk resumes after.
 */
final class Patrols {
    /** A patrol point that takes this long to reach (game ticks) is given up on. */
    static final long STALL = 1200;
    /** How far from the centre a guard strays between stops. */
    static final double GUARD_WANDER = 8;
    private static final RandomSource RNG = RandomSource.create(11L);

    private Patrols() {}

    static void tick(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b, long now) {
        Territory t = TerritoryBook.get(server).get(b.order.region());
        if (t == null || !t.dimension().equals(p.level().dimension().location().toString())) {
            Orders.apply(b, p.position(), SoldierOrder.FOLLOW);     // the territory is gone: back to the owner
            return;
        }
        boolean calm = b.threats.isEmpty() && !b.hunting;
        b.sprint = !calm && SoldierRole.tactics(b.role) != SoldierRole.HEAVY;   // a patrol walks between its stops; it runs only in a fight
        if (!calm) return;
        boolean guard = b.order.kind() == SoldierOrder.Kind.GUARD;
        if (b.patrolPoint != null && now - b.patrolSince > STALL) b.patrolPoint = null;
        if (b.patrolPoint == null) {
            if (b.dwell > 0) {                                      // still looking about
                b.dwell -= 10;
                return;
            }
            Vec3 next = t.patrolPoint(b.patrolIndex++, b.patrolPoint == null ? p.position() : b.patrolPoint, RNG, guard ? GUARD_WANDER : t.radius());
            b.patrolPoint = onGround(p.serverLevel(), t, next, !t.route().isEmpty());
            b.patrolSince = now;
            b.anchor = b.patrolPoint;
            b.dwell = -1;
            return;
        }
        if (horizontal(p.position(), b.patrolPoint) <= 2.5 + b.holdRadius) {
            if (b.dwell < 0) {                                      // just arrived: stop and look around
                b.dwell = (guard ? 300 : 60) + RNG.nextInt(guard ? 400 : 140);
                b.watchYaw = RNG.nextFloat() * 360f;
            } else if ((b.dwell -= 10) <= 0) {
                b.patrolPoint = null;                               // done looking: next stop
                b.dwell = -1;
            }
        }
    }

    /** A point of the territory on the ground. Route waypoints keep the height they were laid at. */
    static Vec3 onGround(ServerLevel level, Territory t, Vec3 at, boolean exact) {
        if (exact) return at;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        double y = top.getY() >= t.minY() && top.getY() <= t.maxY() ? top.getY() : t.centre().y;
        return new Vec3(at.x, y, at.z);
    }

    static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
