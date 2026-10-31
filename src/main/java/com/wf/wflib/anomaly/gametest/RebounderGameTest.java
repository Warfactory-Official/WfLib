package com.wf.wflib.anomaly.gametest;

import com.wf.wflib.anomaly.AnomalyBlockEntity;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;
import com.wf.wflib.testkit.api.ForcedChunks;
import java.util.List;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.wf.wflib.anomaly.api.AnomalyContent;
import com.wf.wflib.core.api.WFLib;

/**
 * A round fired at a Rebounder from 40 blocks, 30 blocks a tick: after the anomaly's tick it sits where it met the
 * sphere, heading back out at half speed. One fired from inside goes out untouched.
 */
@PrefixGameTestTemplate(false)
public class RebounderGameTest {
    private static final ResourceLocation SLUG = KineticPresets.rl("test_rebounder_slug");
    private static final double SPEED = 30.0D;
    private static final int UP = 40;

    public static void register() {
        KineticPresets.registerItemless(KineticPreset.builder(SLUG, null, Warheads.rl("inert"))
                .speed(SPEED).drag(0.0).gravity(0.0).life(100).mass(0.004).energyDamage(0.01).caliber(5.56)
                .blockPen(10.0).noChunkLoading().build());
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 200)
    public static void roundsGlanceOffAndShotsFromInsideGoOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO).above(UP);
        // Launch point 40 west can sit outside the arena grid: unloaded => Rounds.step defers.
        List<ChunkPos> lane = ForcedChunks.along(Vec3.atCenterOf(pos).add(-41.0D, 0.0D, 0.0D), Vec3.atCenterOf(pos));
        ForcedChunks.set(level, lane, true);
        helper.startSequence().thenWaitUntil(ForcedChunks.ticking(level, lane)).thenExecute(() -> {
            BlockState state = AnomalyContent.REBOUNDER.get().defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            try {
                AnomalyBlockEntity be = (AnomalyBlockEntity) level.getBlockEntity(pos);
                helper.assertTrue(be != null, "no anomaly block entity");
                Vec3 c = be.center();
                double r = be.behaviour().radius();
                long in = Rounds.launch(level, KineticPresets.get(SLUG), c.add(-40.0D, 0.5D, 0.0D),
                        new Vec3(SPEED, 0.0D, 0.0D), null, null);
                long out = Rounds.launch(level, KineticPresets.get(SLUG), c.add(1.0D, 0.5D, 0.0D),
                        new Vec3(SPEED, 0.0D, 0.0D), null, null);
                // Out of reach this tick (40 - 30 > r): left alone.
                AnomalyBlockEntity.serverTick(level, pos, state, be);
                helper.assertTrue(Rounds.velocity(level, in).x == SPEED, "turned too early: " + Rounds.velocity(level, in));
                helper.assertTrue(Rounds.step(level, in), "round ended on its way in");
                AnomalyBlockEntity.serverTick(level, pos, state, be);
                Vec3 at = Rounds.position(level, in);
                Vec3 v = Rounds.velocity(level, in);
                helper.assertTrue(Math.abs(at.distanceTo(c) - r) < 1.0E-3D, "not stopped at the sphere: " + at);
                helper.assertTrue(v.x < 0.0D && Math.abs(v.length() - SPEED / 2.0D) < 1.0E-6D, "not sent back: " + v);
                helper.assertTrue(Rounds.velocity(level, out).x == SPEED, "a shot from inside was turned");
                Rounds.destroy(level, in);
                Rounds.destroy(level, out);
            } finally {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                ForcedChunks.set(level, lane, false);
            }
        }).thenSucceed();
    }
}
