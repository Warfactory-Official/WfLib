package com.wf.wflib.ragdoll.gametest;

import com.mojang.authlib.GameProfile;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hit.HitRegistration;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.hit.RayHit;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigCache;
import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.Posture;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.ragdoll.RagdollServer;
import com.wf.wflib.ragdoll.api.Ragdolls;
import com.wf.wflib.ragdoll.sim.Ragdoll;
import com.wf.wflib.ragdoll.sim.Skeleton;
import com.wf.wflib.testkit.api.ForcedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Real blocks + the real rig; the solver's own behaviour is {@code RagdollTest}. Lanes kept apart, high up. */
@PrefixGameTestTemplate(false)
public class RagdollGameTest {

    private static final int Y = 250;

    private static ServerPlayer player(ServerLevel level, String name, double x, double y, double z) {
        ServerPlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.moveTo(x, y, z, 0, 0);
        p.yBodyRot = 0;
        p.yBodyRotO = 0;
        p.setDeltaMovement(Vec3.ZERO);
        return p;
    }

    private static String state(ServerPlayer p, Ragdoll sim) {
        return sim == null ? "no sim, active " + Ragdolls.active(p) + " removed " + p.isRemoved() + " spectator " + p.isSpectator()
                : "torso " + sim.x[3] + " " + sim.x[4] + " " + sim.x[5] + " fastest " + fastest(sim);
    }

    private static String fastest(Ragdoll sim) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Skeleton.BODIES; i++) {
            b.append(String.format("%d:%.2f/%.2f ", i, Math.sqrt(sim.v[i * 3] * sim.v[i * 3] + sim.v[i * 3 + 1] * sim.v[i * 3 + 1] + sim.v[i * 3 + 2] * sim.v[i * 3 + 2]),
                    Math.sqrt(sim.w[i * 3] * sim.w[i * 3] + sim.w[i * 3 + 1] * sim.w[i * 3 + 1] + sim.w[i * 3 + 2] * sim.w[i * 3 + 2])));
        }
        return b.toString();
    }

    private static void floor(ServerLevel level, BlockPos at, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                level.setBlockAndUpdate(at.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static double lowest(ServerPlayer p, HumanoidRig.LocalRig rig) {
        double low = Double.MAX_VALUE;
        for (Obb o : rig.all()) {
            double y = o.center().y - Math.abs(o.axisX().y) * o.half().x - Math.abs(o.axisY().y) * o.half().y
                    - Math.abs(o.axisZ().y) * o.half().z;
            low = Math.min(low, y + p.getY());
        }
        return low;
    }

    private static Vec3 worldCentre(ServerPlayer p, Obb o) {
        double yaw = Math.toRadians(p.yBodyRot), fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx;
        Vec3 c = o.center();
        return new Vec3(p.getX() + c.x * rx + c.z * fx, p.getY() + c.y, p.getZ() + c.x * rz + c.z * fz);
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", batch = "wflib_ragdoll", timeoutTicks = 500)
    public static void aBodyLiesOnTheFloorAndItsHitboxesLieWithIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(1032, Y, 9032);
        List<ChunkPos> lane = List.of(new ChunkPos(at));
        ForcedChunks.set(level, lane, true);
        ServerPlayer[] p = new ServerPlayer[1];
        helper.startSequence()
                .thenWaitUntil(ForcedChunks.ticking(level, lane))
                .thenExecute(() -> {
                    floor(level, at, 7);
                    p[0] = player(level, "rag-floor", at.getX() + 0.5, Y + 1, at.getZ() + 0.5);
                    AhfStance.setDowned(p[0], true);
                    Ragdolls.set(p[0], true);
                })
                .thenWaitUntil(() -> {
                    Ragdoll sim = RagdollServer.simOf(p[0]);
                    helper.assertTrue(sim != null && sim.asleep, "not settled: " + state(p[0], sim));
                })
                .thenExecute(() -> {
                    Ragdoll sim = RagdollServer.simOf(p[0]);
                    HumanoidRig.LocalRig rig = RigCache.get(p[0]);
                    double low = lowest(p[0], rig);
                    helper.assertTrue(low > Y + 1 - 0.09, "rig sank: " + low);
                    helper.assertTrue(low < Y + 1 + 0.15, "rig floats: " + low);
                    helper.assertTrue(sim.x[Skeleton.TORSO * 3 + 1] < Y + 1 + 0.5, "not lying down");
                    for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
                        Vec3 drawn = worldCentre(p[0], slot.get(rig));
                        int i = slot.ordinal();
                        double gap = drawn.distanceTo(new Vec3(sim.x[i * 3], sim.x[i * 3 + 1], sim.x[i * 3 + 2]));
                        helper.assertTrue(gap < 0.06, slot + " rig off the sim by " + gap);
                    }
                    AABB reg = HitRegistration.registrationBox(p[0]);
                    for (Obb o : rig.all()) {
                        Vec3 c = worldCentre(p[0], o);
                        helper.assertTrue(reg.contains(c), "registration box misses a limb at " + c + " box " + reg);
                    }
                    // Straight down through each drawn limb: the part it belongs to, never a gap.
                    for (HumanoidRig.LocalRig.Slot slot : new HumanoidRig.LocalRig.Slot[]{HumanoidRig.LocalRig.Slot.HEAD,
                            HumanoidRig.LocalRig.Slot.LEFT_SHIN, HumanoidRig.LocalRig.Slot.RIGHT_FOREARM}) {
                        Vec3 c = worldCentre(p[0], slot.get(rig));
                        RayHit hit = HitboxApi.classifyRay(p[0], c.add(0, 3, 0), c.add(0, -0.05, 0));
                        helper.assertTrue(hit != null, slot + " not hit from above");
                    }
                    Ragdolls.set(p[0], false);
                    AhfStance.setDowned(p[0], false);
                    helper.assertTrue(AhfStance.get(p[0]).effective() == Posture.STAND,
                            "released into " + AhfStance.get(p[0]).effective());
                })
                .thenWaitUntil(() -> helper.assertFalse(Ragdolls.active(p[0]), "still ragdolled"))
                .thenExecute(() -> {
                    helper.assertTrue(p[0].level().noCollision(p[0], p[0].getBoundingBox()), "stood up inside a block");
                    helper.assertTrue(p[0].getY() >= Y + 1 - 1.0e-3 && p[0].getY() < Y + 2.5, "stood at y " + p[0].getY());
                    ForcedChunks.set(level, lane, false);
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", batch = "wflib_ragdoll", timeoutTicks = 500)
    public static void aBodyKilledAtRestIsNotLeftInsideTheFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(1064, Y, 9064);
        List<ChunkPos> lane = List.of(new ChunkPos(at));
        ForcedChunks.set(level, lane, true);
        ServerPlayer[] p = new ServerPlayer[1];
        helper.startSequence()
                .thenWaitUntil(ForcedChunks.ticking(level, lane))
                .thenExecute(() -> {
                    floor(level, at, 7);
                    p[0] = player(level, "rag-kill", at.getX() + 0.5, Y + 1, at.getZ() + 0.5);
                    AhfStance.setDowned(p[0], true);
                    Ragdolls.set(p[0], true);
                })
                .thenWaitUntil(() -> {
                    Ragdoll sim = RagdollServer.simOf(p[0]);
                    helper.assertTrue(sim != null && sim.asleep, "not settled: " + state(p[0], sim));
                })
                .thenExecute(() -> {
                    // Posted by hand: a FakePlayer is invulnerable, so die() would never reach the event.
                    double[] seen = {Double.NaN};
                    var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
                    java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> read =
                            e -> seen[0] = e.getEntity().getY();
                    // Corpse reads the position at LOWEST, after this mod's HIGHEST lift.
                    bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, true,
                            net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, read);
                    bus.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p[0], p[0].damageSources().genericKill()));
                    helper.assertTrue(seen[0] >= Y + 1 - 1.0e-6, "died inside the floor at y " + seen[0]);
                    ForcedChunks.set(level, lane, false);
                })
                .thenSucceed();
    }

    /** Hit part classification works at the pose, not only near it. */
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", batch = "wflib_ragdoll", timeoutTicks = 500)
    public static void aHitTakesTheLimbAtItsRagdolledPose(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(1288, Y, 9032);
        List<ChunkPos> lane = List.of(new ChunkPos(at));
        ForcedChunks.set(level, lane, true);
        ServerPlayer[] p = new ServerPlayer[1];
        helper.startSequence()
                .thenWaitUntil(ForcedChunks.ticking(level, lane))
                .thenExecute(() -> {
                    floor(level, at, 7);
                    p[0] = player(level, "rag-hit", at.getX() + 0.5, Y + 1, at.getZ() + 0.5);
                    Ragdolls.set(p[0], true);
                })
                .thenWaitUntil(() -> {
                    Ragdoll sim = RagdollServer.simOf(p[0]);
                    helper.assertTrue(sim != null && sim.asleep, "not settled: " + state(p[0], sim));
                })
                .thenExecute(() -> {
                    HumanoidRig.LocalRig rig = RigCache.get(p[0]);
                    for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
                        Vec3 c = worldCentre(p[0], slot.get(rig));
                        boolean covered = false;
                        for (HumanoidRig.LocalRig.Slot other : HumanoidRig.LocalRig.SLOTS) {
                            Vec3 o = worldCentre(p[0], other.get(rig));
                            covered |= other != slot && o.y > c.y && Math.hypot(o.x - c.x, o.z - c.z) < 0.35;
                        }
                        if (covered) {
                            continue;
                        }
                        RayHit hit = HitboxApi.classifyRay(p[0], c.add(0, 3, 0), c);
                        HitboxPart want = slot.limb;
                        helper.assertTrue(hit != null && hit.part() == want,
                                slot + " classified " + (hit == null ? "none" : hit.part()));
                    }
                    Ragdolls.set(p[0], false);
                    ForcedChunks.set(level, lane, false);
                })
                .thenSucceed();
    }

    /** Built up from the superflat ground (y -61 top block), which it ends on. */
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", batch = "wflib_ragdoll", timeoutTicks = 800)
    public static void aFallDownStairsEndsOnTheStairs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int ground = -60;
        BlockPos at = new BlockPos(1544, ground, 9032);
        List<ChunkPos> lane = List.of(new ChunkPos(at), new ChunkPos(at.offset(16, 0, 0)), new ChunkPos(at.offset(32, 0, 0)),
                new ChunkPos(at.offset(-16, 0, 0)));
        ForcedChunks.set(level, lane, true);
        ServerPlayer[] p = new ServerPlayer[1];
        helper.startSequence()
                .thenWaitUntil(ForcedChunks.ticking(level, lane))
                .thenExecute(() -> {
                    for (int i = 0; i < 12; i++) {
                        for (int z = -7; z <= 6; z++) {
                            for (int y = 0; y < 12 - i; y++) {
                                level.setBlockAndUpdate(at.offset(i, y, z), Blocks.STONE.defaultBlockState());
                            }
                        }
                    }
                    p[0] = player(level, "rag-stairs", at.getX() + 0.5, ground + 12, at.getZ() + 0.5);
                    Ragdolls.set(p[0], true);
                    Ragdolls.blast(p[0], new Vec3(4, 0.5, 0));
                })
                .thenWaitUntil(() -> {
                    Ragdoll sim = RagdollServer.simOf(p[0]);
                    helper.assertTrue(sim != null && sim.asleep, "not settled: " + state(p[0], sim));
                })
                .thenExecute(() -> {
                    Ragdoll sim = RagdollServer.simOf(p[0]);
                    double x = sim.x[Skeleton.TORSO * 3], y = sim.x[Skeleton.TORSO * 3 + 1];
                    helper.assertTrue(x > at.getX() + 1.5, "did not tumble: x " + x);
                    int i = (int) Math.floor(x) - at.getX();
                    double top = ground + Math.max(0, 12 - i);
                    helper.assertTrue(y > top - 0.1, "inside the stairs: y " + y + " top " + top);
                    Ragdolls.set(p[0], false);
                    ForcedChunks.set(level, lane, false);
                })
                .thenSucceed();
    }
}
