package com.wf.wflib.warhead.gametest;

import com.mojang.logging.LogUtils;
import com.wf.wflib.MissileEntity;
import com.wf.wflib.WFLib;
import com.wf.wflib.item.MissilePresetRegistry;
import com.wf.wflib.util.ForcedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * `cluster` preset onto flat / sloped ground, spawned in a 45° dive or launched from a pad: every missilelet ends,
 * leaves land near the aim.
 */
@GameTestHolder(WFLib.MODID)
@PrefixGameTestTemplate(false)
public class ClusterSlopeGameTest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TEMPLATE = "empty";
    /** Past ThermobaricGameTest (6400). */
    private static final int LANE_ORIGIN = 7400;
    private static final int LANE_SPACING = 320;
    private static final int ALTITUDE = 80;
    /** Slope half-extent along z; along x {@link #REACH}. */
    private static final int HALF = 72;
    private static final int REACH = 208;
    /** Launch site: horizontal range from the aim. */
    private static final double LAUNCH_RANGE = 180.0;
    private static final int RELIEF = 48;
    private static final int SHELL = 6;
    private static final double START_RANGE = 70.0;
    private static final int TICKS = 600;
    /** 2 splits x 4 => 16; a downed d1 dud (DEFAULT_DUD_CHANCE) takes its 4. */
    private static final int LEAVES = 16;
    private static final double MAX_LEAF_MISS = 45.0;

    @GameTest(template = TEMPLATE, timeoutTicks = TICKS + 60)
    public static void clusterOnFlatGround(GameTestHelper helper) {
        run(helper, 0, 0.0, 0.0);
    }

    /** Dive into the rising face. */
    @GameTest(template = TEMPLATE, timeoutTicks = TICKS + 60)
    public static void clusterIntoA45DegreeUpslope(GameTestHelper helper) {
        run(helper, 1, 45.0, 0.0);
    }

    /** Dive from over the crest down the falling face. */
    @GameTest(template = TEMPLATE, timeoutTicks = TICKS + 60)
    public static void clusterDownA30DegreeSlope(GameTestHelper helper) {
        run(helper, 2, 30.0, 180.0);
    }

    /** Dive along the contour of a 45° slope. */
    @GameTest(template = TEMPLATE, timeoutTicks = TICKS + 60)
    public static void clusterAcrossA45DegreeSlope(GameTestHelper helper) {
        run(helper, 3, 45.0, 90.0);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TICKS + 60)
    public static void launchedClusterOnFlatGround(GameTestHelper helper) {
        run(helper, 4, 0.0, 0.0, true);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TICKS + 60)
    public static void launchedClusterUpA45DegreeHill(GameTestHelper helper) {
        run(helper, 5, 45.0, 0.0, true);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TICKS + 60)
    public static void launchedClusterDownA45DegreeHill(GameTestHelper helper) {
        run(helper, 6, 45.0, 180.0, true);
    }

    private static void run(GameTestHelper helper, int index, double slopeDeg, double azimuthDeg) {
        run(helper, index, slopeDeg, azimuthDeg, false);
    }

    /**
     * @param slopeDeg ground rises along +x at this grade through the aim point
     * @param azimuthDeg approach heading: 0 = travelling +x (into the upslope), 180 = -x, 90 = +z
     * @param launched true = from a ground pad {@link #LAUNCH_RANGE} out, own ascent/cruise/attack; false = spawned
     *      in ATTACK on a 45° line {@link #START_RANGE} out
     */
    private static void run(GameTestHelper helper, int index, double slopeDeg, double azimuthDeg,
                            boolean launched) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(0, ALTITUDE, LANE_ORIGIN + index * LANE_SPACING);
        force(level, origin, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(origin), "lane loading"))
                .thenExecute(() -> fire(helper, level, origin, slopeDeg, azimuthDeg, launched));
    }

    private static void fire(GameTestHelper helper, ServerLevel level, BlockPos origin, double slopeDeg,
                             double azimuthDeg, boolean launched) {
        double grade = Math.tan(Math.toRadians(slopeDeg));
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int reach = launched ? REACH : HALF;
        for (int x = -reach; x <= reach; x++) {
            int top = Mth.clamp(Mth.floor(x * grade), -RELIEF, RELIEF);
            for (int z = -HALF; z <= HALF; z++) {
                for (int y = -RELIEF - SHELL; y <= RELIEF + 2; y++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState want = y <= top && y > top - SHELL ? stone : air;
                    if (!level.getBlockState(cursor).equals(want)) {
                        level.setBlock(cursor, want, 2);
                    }
                }
            }
        }
        Vec3 aim = new Vec3(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5);
        double az = Math.toRadians(azimuthDeg);
        double c = Math.cos(Math.toRadians(45.0));
        Vec3 travel = new Vec3(Math.cos(az) * c, -Math.sin(Math.toRadians(45.0)), Math.sin(az) * c);
        var preset = MissilePresetRegistry.get(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "cluster"));
        MissileEntity missile;
        if (launched) {
            double px = -Math.cos(az) * LAUNCH_RANGE;
            double pz = -Math.sin(az) * LAUNCH_RANGE;
            int ground = Mth.clamp(Mth.floor(Mth.floor(px) * grade), -RELIEF, RELIEF);
            missile = preset.build(level, aim);
            missile.moveTo(aim.x + px, origin.getY() + ground + 2.0, aim.z + pz, 0.0f, 0.0f);
        } else {
            Vec3 from = aim.subtract(travel.scale(START_RANGE));
            missile = preset.builder(level, aim).attackAngle(45.0).attackFrom(-travel.x, -travel.z).startInAttack()
                    .build();
            missile.moveTo(from.x, from.y, from.z, 0.0f, 0.0f);
            missile.setDeltaMovement(travel.scale(1.5));
        }
        level.addFreshEntity(missile);

        AABB box = new AABB(aim, aim).inflate(REACH + 40.0);
        double[] split = {Double.NaN, Double.NaN, Double.NaN};
        Map<Integer, MissileEntity> alive = new HashMap<>();
        StringBuilder leaves = new StringBuilder();
        int[] counts = new int[3];
        int[] duds = {0};
        Set<Integer> dudIds = new HashSet<>();
        Map<Integer, Vec3> born = new HashMap<>();
        StringBuilder far = new StringBuilder();
        double[] worstMiss = {0.0};
        helper.onEachTick(() -> {
            for (MissileEntity m : level.getEntitiesOfClass(MissileEntity.class, box)) {
                if (!dudIds.contains(m.getId()) && alive.putIfAbsent(m.getId(), m) == null) {
                    born.put(m.getId(), m.position().subtract(aim));
                }
            }
            alive.values().removeIf(m -> {
                if (m.isDud()) {
                    duds[0]++;
                    dudIds.add(m.getId());
                    return true;
                }
                if (!m.isRemoved()) {
                    return false;
                }
                int depth = m.swarm().getSplitDepth();
                if (depth == 2) {
                    split[0] = m.getX() - aim.x;
                    split[1] = m.getY() - aim.y;
                    split[2] = m.getZ() - aim.z;
                }
                counts[Math.min(depth, 2)]++;
                if (depth == 0) {
                    double miss = Math.hypot(m.getX() - aim.x, m.getZ() - aim.z);
                    worstMiss[0] = Math.max(worstMiss[0], miss);
                    if (miss > MAX_LEAF_MISS) {
                        Vec3 b = born.get(m.getId());
                        Vec3 t = m.flight().getTarget().subtract(aim);
                        far.append(String.format(Locale.ROOT, " [born (%.1f,%.1f,%.1f) tgt (%.1f,%.1f,%.1f) age %d]",
                                b.x, b.y, b.z, t.x, t.y, t.z, m.tickCount));
                    }
                    leaves.append(String.format(Locale.ROOT, " (%.0f,%.0f,%.0f)",
                            m.getX() - aim.x, m.getY() - aim.y, m.getZ() - aim.z));
                }
                return true;
            });
        });
        helper.runAfterDelay(TICKS, () -> {
            StringBuilder stuck = new StringBuilder();
            for (MissileEntity m : alive.values()) {
                stuck.append(String.format(Locale.ROOT, " d%d %s (%.0f,%.0f,%.0f) v=%.2f",
                        m.swarm().getSplitDepth(), m.flight().getPhase(), m.getX() - aim.x, m.getY() - aim.y,
                        m.getZ() - aim.z, m.getDeltaMovement().length()));
                m.discard();
            }
            force(level, origin, false);
            String summary = String.format(Locale.ROOT, "slope %.0f az %.0f launched %s: split (%.0f,%.0f,%.0f);"
                    + " gone d2=%d d1=%d d0=%d duds %d, worst miss %.1f; leaves%s; far%s; stuck%s", slopeDeg, azimuthDeg,
                    launched, split[0], split[1], split[2], counts[2], counts[1], counts[0], duds[0], worstMiss[0],
                    leaves, far, stuck);
            LOGGER.info("[cluster-slope] {}", summary);
            if (!alive.isEmpty()) {
                helper.fail(alive.size() + " missilelets still flying after " + TICKS + " ticks: " + summary);
            } else if (counts[0] < LEAVES / 2) {
                helper.fail(counts[0] + " of " + LEAVES + " leaves detonated: " + summary);
            } else if (worstMiss[0] > MAX_LEAF_MISS) {
                helper.fail("leaf landed past " + MAX_LEAF_MISS + ": " + summary);
            } else {
                helper.succeed();
            }
        });
    }

    private static void force(ServerLevel level, BlockPos origin, boolean forced) {
        int cx = SectionPos.blockToSectionCoord(origin.getX());
        int cz = SectionPos.blockToSectionCoord(origin.getZ());
        int rx = (REACH >> 4) + 2;
        for (int ox = -rx; ox <= rx; ox++) {
            for (int oz = -6; oz <= 6; oz++) {
                ForcedChunks.set(level, cx + ox, cz + oz, forced);
            }
        }
    }
}
