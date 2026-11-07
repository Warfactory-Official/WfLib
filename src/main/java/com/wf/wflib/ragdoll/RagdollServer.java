package com.wf.wflib.ragdoll;

import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hit.AhfHitSource;
import com.norwood.ahf.lagcomp.EntityHistory;
import com.norwood.ahf.lagcomp.Snapshot;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import com.wf.wflib.physics.api.BoxBuffer;
import com.wf.wflib.physics.api.ChunkCollisionCache;
import com.wf.wflib.ragdoll.sim.Ragdoll;
import com.wf.wflib.ragdoll.sim.RagdollPose;
import com.wf.wflib.ragdoll.sim.Skeleton;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix3d;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/** Tick slot {@code TickOrder.RAGDOLL}. Tick thread. */
public final class RagdollServer {

    private static final int BLEND_TICKS = 14;
    /** Held impulse lifetime: the hit lands before the medical tick that downs the player. */
    private static final int PENDING_TICKS = 30;
    private static final double MAX_START_SPEED = 30.0;
    private static final double SAFE_FALL = 3.0;
    /** Rounds carry little momentum; the shove is feel, capped so a limb never whips. */
    private static final double HIT_IMPULSE_PER_DAMAGE = 1.2, HIT_IMPULSE_MAX = 22.0;
    private static final double BLAST_SPEED_PER_DAMAGE = 0.35, BLAST_SPEED_MAX = 14.0;

    private static final class Body implements Ragdoll.World {
        final ServerPlayer player;
        final net.minecraft.world.level.Level level;
        boolean wanted = true;
        Ragdoll sim;
        final BoxBuffer buffer = new BoxBuffer();
        double[] flat = new double[64 * 6];
        ChunkCollisionCache cache;
        double fall;
        double lastY;
        long section = Long.MIN_VALUE;
        boolean sentRest;
        long epoch = -1, support;

        Body(ServerPlayer player) {
            this.player = player;
            this.level = player.level();
        }

        @Override
        public int collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            buffer.clear();
            AABB bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            cache.prepare(player.level(), bounds);
            cache.collectBoxes(bounds, buffer);
            int n = buffer.size();
            if (flat.length < n * 6) {
                flat = new double[n * 12];
            }
            for (int i = 0; i < n; i++) {
                flat[i * 6] = buffer.minX(i);
                flat[i * 6 + 1] = buffer.minY(i);
                flat[i * 6 + 2] = buffer.minZ(i);
                flat[i * 6 + 3] = buffer.maxX(i);
                flat[i * 6 + 4] = buffer.maxY(i);
                flat[i * 6 + 5] = buffer.maxZ(i);
            }
            return n;
        }

        @Override
        public double[] boxes() {
            return flat;
        }
    }

    private record Pending(long tick, Vec3 at, Vec3 impulse, boolean blast) {
    }

    private static final Map<Integer, Body> BODIES = new ConcurrentHashMap<>();
    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static volatile Skeleton skeleton;
    private static long stepNanos, steps;
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private RagdollServer() {
    }

    /** Awake step cost, cumulative. */
    public static long stepNanos() {
        return stepNanos;
    }

    public static long steps() {
        return steps;
    }

    public static Skeleton skeleton() {
        Skeleton s = skeleton;
        if (s == null) {
            s = new Skeleton(HumanoidRig.baseSpec(HitboxPart.HEAD), HumanoidRig.baseSpec(HitboxPart.TORSO),
                    HumanoidRig.baseSpec(HitboxPart.LEFT_ARM), HumanoidRig.baseSpec(HitboxPart.RIGHT_ARM),
                    HumanoidRig.baseSpec(HitboxPart.LEFT_LEG), HumanoidRig.baseSpec(HitboxPart.RIGHT_LEG));
            skeleton = s;
        }
        return s;
    }

    public static void set(ServerPlayer player, boolean on) {
        int id = player.getId();
        Body body = BODIES.get(id);
        if (on) {
            if (body == null) {
                BODIES.put(id, new Body(player));
            } else {
                body.wanted = true;
            }
        } else if (body != null) {
            body.wanted = false;
            RagdollStore store = RagdollStore.of(false);
            RagdollStore.Track track = store.track(id);
            if (body.sim != null && track != null && !track.released()) {
                stand(store, body);
            }
        }
    }

    public static void impulse(ServerPlayer player, Vec3 at, Vec3 impulse) {
        Body body = BODIES.get(player.getId());
        if (body != null && body.sim != null) {
            shove(body.sim, at, impulse);
            body.sentRest = false;
        } else {
            PENDING.put(player.getId(), new Pending(player.level().getGameTime(), at, impulse, false));
        }
    }

    public static void blast(ServerPlayer player, Vec3 velocity) {
        Body body = BODIES.get(player.getId());
        if (body != null && body.sim != null) {
            throwBody(body.sim, velocity);
            body.sentRest = false;
        } else {
            PENDING.put(player.getId(), new Pending(player.level().getGameTime(), Vec3.ZERO, velocity, true));
        }
    }

    private static void shove(Ragdoll sim, Vec3 at, Vec3 impulse) {
        int best = Skeleton.TORSO;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < Skeleton.BODIES; i++) {
            double d = Mth.lengthSquared(sim.x[i * 3] - at.x, sim.x[i * 3 + 1] - at.y, sim.x[i * 3 + 2] - at.z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        sim.impulse(best, at.x, at.y, at.z, impulse.x, impulse.y, impulse.z);
    }

    /** Feet resist the push on the ground, arms lag it: rotational kick per limb on top of the linear throw. */
    private static void throwBody(Ragdoll sim, Vec3 velocity) {
        sim.kick(velocity.x, velocity.y, velocity.z);
        double speed = velocity.length();
        if (speed < 1.0e-3) {
            return;
        }
        Vec3 dir = velocity.scale(1.0 / speed);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 armAxis = dir.cross(up);
        double arm = speed * 0.9, leg = speed * 0.45;
        for (int limb : new int[]{Skeleton.LEFT_ARM, Skeleton.RIGHT_ARM, Skeleton.LEFT_FOREARM, Skeleton.RIGHT_FOREARM}) {
            double k = arm * (0.5 + RANDOM.nextDouble());
            sim.spin(limb, armAxis.x * k, armAxis.y * k, armAxis.z * k);
        }
        for (int limb : new int[]{Skeleton.LEFT_LEG, Skeleton.RIGHT_LEG, Skeleton.LEFT_SHIN, Skeleton.RIGHT_SHIN}) {
            double k = -leg * (0.5 + RANDOM.nextDouble());
            sim.spin(limb, armAxis.x * k, armAxis.y * k, armAxis.z * k);
        }
    }

    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (!PENDING.isEmpty()) {
            PENDING.values().removeIf(p -> now - p.tick > PENDING_TICKS);
        }
        if (BODIES.isEmpty()) {
            return;
        }
        RagdollStore store = RagdollStore.of(false);
        for (Iterator<Map.Entry<Integer, Body>> it = BODIES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Body> e = it.next();
            Body body = e.getValue();
            ServerPlayer p = body.player;
            if (body.level != level) {
                continue;
            }
            if (p.level() != level || p.isRemoved() || p.isSpectator() || p.isPassenger() && body.sim != null) {
                drop(store, e.getKey(), body);
                it.remove();
                continue;
            }
            RagdollStore.Track track = store.track(e.getKey());
            if (track != null && track.released()) {
                track.tickBlend();
                if (track.blendDone()) {
                    store.close(e.getKey());
                    it.remove();
                }
                continue;
            }
            if (body.sim == null) {
                if (!body.wanted) {
                    it.remove();
                } else if (!p.isPassenger()) {
                    begin(store, body, now);
                }
                continue;
            }
            step(level, store, body, now);
        }
    }

    private static void begin(RagdollStore store, Body body, long now) {
        ServerPlayer p = body.player;
        Ragdoll sim = new Ragdoll(skeleton());
        HumanoidRig.LocalRig rig = HumanoidRig.compute(p);
        double yaw = Math.toRadians(p.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx;
        Matrix3d m = new Matrix3d();
        Quaterniond out = new Quaterniond();
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            Obb o = slot.get(rig);
            int i = slot.ordinal();
            Vec3 c = o.center();
            sim.x[i * 3] = p.getX() + c.x * rx + c.z * fx;
            sim.x[i * 3 + 1] = p.getY() + c.y;
            sim.x[i * 3 + 2] = p.getZ() + c.x * rz + c.z * fz;
            m.setColumn(0, world(o.axisX(), rx, rz, fx, fz));
            m.setColumn(1, world(o.axisY(), rx, rz, fx, fz));
            m.setColumn(2, world(o.axisZ(), rx, rz, fx, fz));
            m.getNormalizedRotation(out);
            sim.q[i * 4] = out.x;
            sim.q[i * 4 + 1] = out.y;
            sim.q[i * 4 + 2] = out.z;
            sim.q[i * 4 + 3] = out.w;
        }
        Snapshot back = EntityHistory.snapshot(p, now - 3);
        if (back != null) {
            Vec3 v = p.position().subtract(back.pos()).scale(20.0 / 3.0);
            double speed = v.length();
            if (speed > MAX_START_SPEED) {
                v = v.scale(MAX_START_SPEED / speed);
            }
            sim.kick(v.x, v.y, v.z);
        }
        Pending pending = PENDING.remove(p.getId());
        if (pending != null && now - pending.tick <= PENDING_TICKS) {
            if (pending.blast) {
                throwBody(sim, pending.impulse);
            } else {
                shove(sim, pending.at, pending.impulse);
            }
        }
        body.sim = sim;
        body.cache = ChunkCollisionCache.of(p.level());
        body.lastY = sim.x[Skeleton.TORSO * 3 + 1];
        RagdollStore.Track track = store.open(p.getId());
        track.push(now).from(sim);
        send(body, now, track.newest());
    }

    private static org.joml.Vector3d world(Vec3 axis, double rx, double rz, double fx, double fz) {
        return new org.joml.Vector3d(axis.x * rx + axis.z * fx, axis.y, axis.x * rz + axis.z * fz);
    }

    private static void step(ServerLevel level, RagdollStore store, Body body, long now) {
        ServerPlayer p = body.player;
        Ragdoll sim = body.sim;
        RagdollStore.Track track = store.track(p.getId());
        if (sim.asleep) {
            long epoch = body.cache.epoch();
            if (epoch == body.epoch) {
                return;
            }
            body.epoch = epoch;
            long support = support(body);
            if (support == body.support) {
                return;
            }
            body.support = support;
            sim.wake();
        }
        double tx = sim.x[Skeleton.TORSO * 3], tz = sim.x[Skeleton.TORSO * 3 + 2];
        if (!level.hasChunksAt(Mth.floor(tx - 3), Mth.floor(tz - 3), Mth.floor(tx + 3), Mth.floor(tz + 3))) {
            return;
        }
        for (int i = 0; i < Skeleton.BODIES; i++) {
            CURSOR.set(Mth.floor(sim.x[i * 3]), Mth.floor(sim.x[i * 3 + 1]), Mth.floor(sim.x[i * 3 + 2]));
            FluidState fluid = level.getFluidState(CURSOR);
            sim.submerged[i] = fluid.isEmpty() || !fluid.is(FluidTags.WATER) && !fluid.is(FluidTags.LAVA) ? 0.0
                    : Mth.clamp((CURSOR.getY() + fluid.getHeight(level, CURSOR) - sim.x[i * 3 + 1]) * 3.0 + 0.5, 0.0, 1.0);
        }
        long started = System.nanoTime();
        sim.step(body);
        stepNanos += System.nanoTime() - started;
        steps++;
        double y = sim.x[Skeleton.TORSO * 3 + 1];
        if (!Double.isFinite(y) || !Double.isFinite(sim.x[Skeleton.TORSO * 3])) {
            throw new IllegalStateException("ragdoll of " + p.getScoreboardName() + " diverged at " + now);
        }
        if (y < body.lastY) {
            body.fall += body.lastY - y;
        } else if (y > body.lastY + 0.02 || sim.asleep) {
            if (body.fall > SAFE_FALL) {
                p.causeFallDamage((float) body.fall, 1.0F, p.damageSources().fall());
            }
            body.fall = 0;
        }
        body.lastY = y;
        RagdollPose pose = track.push(now);
        pose.from(sim);
        p.setPos(pose.x, pose.y - pose.footDrop, pose.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        long section = SectionPos.asLong(p.blockPosition());
        if (section != body.section) {
            body.section = section;
            if (!(p instanceof FakePlayer)) {
                level.getChunkSource().move(p);
            }
        }
        if (sim.asleep) {
            body.epoch = body.cache.epoch();
            body.support = support(body);
        }
        send(body, now, pose);
    }

    /** Hash of the block boxes around the body; a level-wide edit counter alone would wake every sleeper on any edit. */
    private static long support(Body body) {
        double x = body.sim.x[Skeleton.TORSO * 3], y = body.sim.x[Skeleton.TORSO * 3 + 1], z = body.sim.x[Skeleton.TORSO * 3 + 2];
        int n = body.collect(x - 2, y - 2, z - 2, x + 2, y + 2, z + 2);
        long h = n;
        for (int i = 0; i < n * 6; i++) {
            h = h * 31 + Double.doubleToLongBits(body.flat[i]);
        }
        return h;
    }

    /**
     * Stands up where the body lies: first free spot around the torso; vanilla pushes out anything left. At release, not
     * next tick: a posture lock released with the entity at the body's low point reads no room and forces prone.
     */
    private static void stand(RagdollStore store, Body body) {
        ServerPlayer p = body.player;
        RagdollPose pose = store.track(p.getId()).newest();
        Vec3 spot = standSpot(p, pose);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.yBodyRot = p.getYRot();
        p.yBodyRotO = p.getYRot();
        if (p instanceof FakePlayer || p.connection == null) {
            p.setPos(spot);
        } else {
            p.connection.teleport(spot.x, spot.y, spot.z, p.getYRot(), p.getXRot());
        }
        store.track(p.getId()).release(BLEND_TICKS);
        broadcastStop(p, BLEND_TICKS);
    }

    private static Vec3 standSpot(ServerPlayer p, RagdollPose pose) {
        double feet = pose.y - pose.footDrop;
        AABB box = p.getDimensions(Pose.STANDING).makeBoundingBox(0, 0, 0);
        double[][] rings = {{0, 0}, {0.5, 0}, {-0.5, 0}, {0, 0.5}, {0, -0.5}, {0.7, 0.7}, {-0.7, 0.7}, {0.7, -0.7}, {-0.7, -0.7}};
        for (double[] ring : rings) {
            for (double up = 0.01; up <= 1.26; up += 0.25) {
                Vec3 at = new Vec3(pose.x + ring[0], feet + up, pose.z + ring[1]);
                if (p.level().noCollision(p, box.move(at))) {
                    return at;
                }
            }
        }
        return new Vec3(pose.x, feet, pose.z);
    }

    private static void drop(RagdollStore store, int id, Body body) {
        store.close(id);
        if (body.sim != null) {
            broadcastStop(body.player, 0);
        }
    }

    private static void send(Body body, long stamp, RagdollPose pose) {
        RagdollPackets.Pose packet = RagdollPackets.Pose.of(body.player.getId(), stamp, body.sim.asleep, pose);
        PacketDistributor.sendToPlayersTrackingEntity(body.player, packet);
        if (!(body.player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(body.player, packet);
        }
    }

    private static void broadcastStop(ServerPlayer p, int blend) {
        RagdollPackets.Stop packet = new RagdollPackets.Stop(p.getId(), blend);
        PacketDistributor.sendToPlayersTrackingEntity(p, packet);
        if (!(p instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(p, packet);
        }
    }

    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer viewer) {
            RagdollStore.Track track = RagdollStore.of(false).track(target.getId());
            Body body = BODIES.get(target.getId());
            if (track != null && body != null && body.sim != null && !track.empty()) {
                PacketDistributor.sendToPlayer(viewer, RagdollPackets.Pose.of(target.getId(),
                        target.level().getGameTime(), body.sim.asleep, track.newest()));
            }
        }
    }

    /** Observes even when another handler later cancels: medical turns most damage into its own model. */
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        DamageSource source = event.getSource();
        float damage = event.getAmount();
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            Vec3 from = source.getSourcePosition();
            if (from != null) {
                Vec3 away = p.position().add(0, 0.5, 0).subtract(from);
                double speed = Math.min(damage * BLAST_SPEED_PER_DAMAGE, BLAST_SPEED_MAX);
                if (away.lengthSqr() > 1.0e-6) {
                    blast(p, away.normalize().add(0, 0.35, 0).normalize().scale(speed));
                }
            }
            return;
        }
        if (source instanceof AhfHitSource hit) {
            Vec3 along = hit.hitTo().subtract(hit.hitFrom());
            if (along.lengthSqr() > 1.0e-6) {
                impulse(p, nearestOnSegment(p, hit.hitFrom(), hit.hitTo()),
                        along.normalize().scale(Math.min(damage * HIT_IMPULSE_PER_DAMAGE, HIT_IMPULSE_MAX)));
            }
        }
    }

    /** Segment point nearest the torso; the sim then picks the nearest body. */
    private static Vec3 nearestOnSegment(ServerPlayer p, Vec3 from, Vec3 to) {
        Body body = BODIES.get(p.getId());
        Vec3 target = body != null && body.sim != null
                ? new Vec3(body.sim.x[Skeleton.TORSO * 3], body.sim.x[Skeleton.TORSO * 3 + 1], body.sim.x[Skeleton.TORSO * 3 + 2])
                : p.position().add(0, 0.9, 0);
        Vec3 d = to.subtract(from);
        double t = Mth.clamp(target.subtract(from).dot(d) / d.lengthSqr(), 0.0, 1.0);
        return from.add(d.scale(t));
    }

    /**
     * The body rests with its low point up to ~0.09 inside the floor (sim contact slop), and mods that spawn something
     * at the death position (Corpse) then see it inside a block and let it fall through a thin floor. Lift it onto the
     * surface before they read the position.
     */
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !owned(p)) {
            return;
        }
        AABB probe = new AABB(p.getX() - 0.3, p.getY(), p.getZ() - 0.3, p.getX() + 0.3, p.getY() + 0.01, p.getZ() + 0.3);
        double lift = 0;
        for (VoxelShape shape : p.level().getBlockCollisions(p, probe)) {
            lift = Math.max(lift, shape.max(net.minecraft.core.Direction.Axis.Y) - p.getY());
        }
        if (lift > 0 && lift <= 0.5) {
            p.setPos(p.getX(), p.getY() + lift, p.getZ());
        }
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        BODIES.remove(event.getEntity().getId());
        RagdollStore.of(false).close(event.getEntity().getId());
    }

    /** Pose for the movement mixin: client packets ignored while present. */
    public static boolean owned(Player p) {
        return !BODIES.isEmpty() && BODIES.containsKey(p.getId()) && BODIES.get(p.getId()).sim != null;
    }

    static void stopped() {
        BODIES.clear();
        PENDING.clear();
        RagdollStore.of(false).clear();
    }

    /** Solver on/off comparison and tests. Null when not simulating. */
    public static Ragdoll simOf(Player p) {
        Body body = BODIES.get(p.getId());
        return body == null ? null : body.sim;
    }
}
