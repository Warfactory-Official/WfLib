package com.wf.wflib.testkit.gametest;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.testkit.api.ForcedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** A held lane, waited to entity-tick, carries an entity across all of its chunks. */
@PrefixGameTestTemplate(false)
public class ForcedChunksGameTest {

    private static final int LANE_ORIGIN = 9000;
    private static final double LENGTH = 112;

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 300)
    public static void anArrowCrossesAHeldLane(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 from = Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO).offset(0, 60, LANE_ORIGIN));
        Vec3 to = from.add(LENGTH, 0, 0);
        List<ChunkPos> lane = ForcedChunks.along(from, to);
        helper.assertTrue(lane.size() >= 7, "lane chunks " + lane.size());
        ForcedChunks.set(level, lane, true);
        Arrow[] arrow = new Arrow[1];
        helper.startSequence()
                .thenWaitUntil(ForcedChunks.ticking(level, lane))
                .thenExecute(() -> {
                    arrow[0] = EntityType.ARROW.create(level);
                    arrow[0].moveTo(from.x, from.y, from.z);
                    arrow[0].setNoGravity(true);
                    arrow[0].setDeltaMovement(4, 0, 0);
                    level.addFreshEntity(arrow[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(arrow[0].getX() - from.x > LENGTH - 8,
                        "arrow at +" + (arrow[0].getX() - from.x) + ", removed " + arrow[0].getRemovalReason()))
                .thenExecute(() -> {
                    arrow[0].discard();
                    ForcedChunks.set(level, lane, false);
                })
                .thenSucceed();
    }
}
