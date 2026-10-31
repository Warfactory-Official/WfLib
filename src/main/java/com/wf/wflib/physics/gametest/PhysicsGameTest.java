package com.wf.wflib.physics.gametest;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.geom.api.OBB;
import com.wf.wflib.physics.api.BoxBuffer;
import com.wf.wflib.physics.api.ChunkCollisionCache;
import com.wf.wflib.physics.api.Narrowphase;
import com.wf.wflib.physics.api.SweptHull;
import com.wf.wflib.testkit.api.ForcedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** Leaf engine against the live world. Lanes near the world origin: OBB centres are floats. */
@PrefixGameTestTemplate(false)
public class PhysicsGameTest {

    private static final int Y = 240;

    @GameTest(templateNamespace = WFLib.MODID, template = "empty")
    public static void theNativeNarrowphaseLoads(GameTestHelper helper) {
        helper.assertTrue(Narrowphase.nativeLoaded(),
                "native narrowphase not loaded: natives missing from the jar, JNI symbols unresolved or no build for this CPU");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 300)
    public static void aSnapshotFollowsBlockWrites(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(40, Y, 40);
        List<ChunkPos> lane = List.of(new ChunkPos(at));
        AABB cell = new AABB(at).deflate(0.25);
        ForcedChunks.set(level, lane, true);
        helper.startSequence()
                .thenWaitUntil(ForcedChunks.ticking(level, lane))
                .thenExecute(() -> {
                    ChunkCollisionCache cache = ChunkCollisionCache.of(level);
                    level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
                    cache.prepare(level, cell);
                    helper.assertFalse(cache.anyBoxIn(cell), "air reads solid");
                    level.setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
                    cache.prepare(level, cell);
                    helper.assertTrue(cache.anyBoxIn(cell), "stone write missed by the snapshot");
                    level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
                    cache.prepare(level, cell);
                    helper.assertFalse(cache.anyBoxIn(cell), "air write missed by the snapshot");
                    ForcedChunks.set(level, lane, false);
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 300)
    public static void aBoxCastDownStopsOnTheFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = new BlockPos(72, Y, 40);
        List<ChunkPos> lane = List.of(new ChunkPos(floor));
        ForcedChunks.set(level, lane, true);
        helper.startSequence()
                .thenWaitUntil(ForcedChunks.ticking(level, lane))
                .thenExecute(() -> {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            level.setBlockAndUpdate(floor.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
                            for (int dy = 1; dy <= 8; dy++) {
                                level.setBlockAndUpdate(floor.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                    // Bottom 4 above the floor top.
                    OBB box = new OBB(new Vector3f(floor.getX() + 0.5f, floor.getY() + 5.5f, floor.getZ() + 0.5f),
                            new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf());
                    ChunkCollisionCache cache = ChunkCollisionCache.of(level);
                    cache.prepare(level, new AABB(floor).inflate(3, 8, 3));
                    SweptHull.Broadphase broadphase = new SweptHull.Broadphase();
                    broadphase.init(cache);
                    BoxBuffer boxes = broadphase.near(box, 0, -10, 0);
                    double toi = SweptHull.timeOfImpact(box, boxes, new Vec3(0, -10, 0));
                    helper.assertTrue(Math.abs(toi * 10 - 4) < 0.02, "stopped after " + toi * 10 + " of 4 blocks");
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            level.setBlockAndUpdate(floor.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                        }
                    }
                    ForcedChunks.set(level, lane, false);
                })
                .thenSucceed();
    }
}
