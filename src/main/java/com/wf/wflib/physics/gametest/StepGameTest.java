package com.wf.wflib.physics.gametest;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.physics.api.ConvexHull;
import com.wf.wflib.physics.api.PhysicsSettings;
import com.wf.wflib.physics.api.PhysicsStep;
import com.wf.wflib.testkit.api.ForcedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Synthetic bodies through the per-level step: flat drop, tilted drop over a one-block step, a two-box stack (one
 * island), a sliding hull. Fixed world position (solver origin = first body's COM, float frames) => hashes portable.
 */
@PrefixGameTestTemplate(false)
public class StepGameTest {

    /** Pose/velocity/spin bits after {@link #TICKS} steps; recorded against the source copy of the engine. */
    static final long GOLDEN = -2414873056281720916L;
    static final int TICKS = 100;
    private static final BlockPos FLOOR = new BlockPos(168, 200, 168);

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 400)
    public static void syncAndAsyncStepsAgreeWithTheSource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> lane = List.of(new ChunkPos(FLOOR));
        ForcedChunks.set(level, lane, true);
        long[] hashes = new long[2];
        List<BoxBody> sync = new ArrayList<>(), async = new ArrayList<>();
        GameTestSequence seq = helper.startSequence().thenWaitUntil(ForcedChunks.ticking(level, lane));
        run(helper, seq, sync, 1000, false, hashes, 0)
                .thenExecute(() -> helper.assertTrue(Math.abs(sync.get(0).y - (FLOOR.getY() + 1.5)) < 0.01
                                && sync.get(0).velocity.length() < 1e-3,
                        "flat box rests at " + (sync.get(0).y - FLOOR.getY()) + ", speed " + sync.get(0).velocity.length()))
                .thenExecute(() -> helper.assertTrue(sync.get(3).y > sync.get(2).y + 0.5,
                        "stack fell through: " + sync.get(2).y + " / " + sync.get(3).y));
        run(helper, seq, async, 2000, true, hashes, 1)
                .thenExecute(() -> {
                    PhysicsSettings.override(null);
                    floor(level, false);
                    ForcedChunks.set(level, lane, false);
                    helper.assertTrue(hashes[0] == hashes[1], "sync " + hashes[0] + " != async " + hashes[1]);
                    helper.assertTrue(hashes[0] == GOLDEN, "step hash " + hashes[0] + " != source " + GOLDEN);
                })
                .thenSucceed();
    }

    private static GameTestSequence run(GameTestHelper helper, GameTestSequence seq, List<BoxBody> bodies, int ids,
                                        boolean async, long[] hashes, int slot) {
        ServerLevel level = helper.getLevel();
        seq.thenExecute(() -> {
            PhysicsSettings.override(PhysicsSettings.current().withAsync(async));
            floor(level, true);
            scene(level, bodies, ids);
        });
        return seq.thenExecuteFor(TICKS, () -> bodies.forEach(PhysicsStep::register)).thenIdle(1).thenExecute(() -> {
            long h = 1;
            for (BoxBody body : bodies) {
                h = body.hash(h);
                body.removed = true;
            }
            hashes[slot] = h;
        });
    }

    /** Stone 9x9 at FLOOR.y, east half one block higher; air above. */
    private static void floor(ServerLevel level, boolean build) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 8; dy++) {
                    boolean solid = build && (dy == 0 || dy == 1 && dx > 0);
                    level.setBlockAndUpdate(FLOOR.offset(dx, dy, dz),
                            solid ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void scene(ServerLevel level, List<BoxBody> bodies, int ids) {
        double x = FLOOR.getX() + 0.5, y = FLOOR.getY() + 1, z = FLOOR.getZ() + 0.5;
        bodies.add(new BoxBody(ids + 1, level, bodies, new Vector3f(0.5f), null, 1, x - 2.5, y + 3.5, z - 2.5,
                new Quaternionf()));
        bodies.add(new BoxBody(ids + 2, level, bodies, new Vector3f(0.6f, 0.4f, 0.8f), null, 2, x + 0.2, y + 3, z + 2,
                new Quaternionf().rotateYXZ(0.3f, 0.2f, 0.1f)));
        bodies.add(new BoxBody(ids + 3, level, bodies, new Vector3f(0.7f, 0.4f, 0.7f), null, 3, x - 2.5, y + 0.6, z + 2.5,
                new Quaternionf()));
        bodies.add(new BoxBody(ids + 4, level, bodies, new Vector3f(0.5f, 0.3f, 0.5f), null, 1, x - 2.4, y + 2.2, z + 2.6,
                new Quaternionf().rotateY(0.4f)));
        BoxBody slider = new BoxBody(ids + 5, level, bodies, new Vector3f(0.6f, 0.4f, 0.6f), prism(), 1.5f, x + 2.5, y + 1.6,
                z - 2.5, new Quaternionf().rotateY(-0.7f));
        slider.velocity.set(-0.15f, 0, 0.05f);
        bodies.add(slider);
    }

    /** Hexagonal prism within extents (0.6, 0.4, 0.6). */
    private static ConvexHull prism() {
        int n = 6;
        float[] points = new float[n * 6];
        for (int i = 0; i < n; i++) {
            float a = i * 6.2831855f / n, px = (float) Math.cos(a) * 0.6f, pz = (float) Math.sin(a) * 0.6f;
            points[i * 3] = px;
            points[i * 3 + 1] = -0.4f;
            points[i * 3 + 2] = pz;
            points[(n + i) * 3] = px;
            points[(n + i) * 3 + 1] = 0.4f;
            points[(n + i) * 3 + 2] = pz;
        }
        int[][] faces = new int[n + 2][];
        faces[0] = new int[n];
        faces[1] = new int[n];
        for (int i = 0; i < n; i++) {
            faces[0][i] = n - 1 - i;
            faces[1][i] = n + i;
            faces[2 + i] = new int[]{i, (i + 1) % n, n + (i + 1) % n, n + i};
        }
        return ConvexHull.of(points, faces);
    }
}
