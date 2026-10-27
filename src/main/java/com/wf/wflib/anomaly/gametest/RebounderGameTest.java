package com.wf.wflib.anomaly.gametest;

import com.wf.wflib.WFLib;
import com.wf.wflib.anomaly.AnomalyBlockEntity;
import com.wf.wflib.anomaly.AnomalyConfig;
import com.wf.wflib.block.ModBlocks;
import com.wf.wflib.kinetic.KineticPreset;
import com.wf.wflib.kinetic.KineticPresetRegistry;
import com.wf.wflib.round.Rounds;
import com.wf.wflib.warhead.WarheadRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A round fired at a Rebounder from 40 blocks, 30 blocks a tick: after the anomaly's tick it sits where it met the
 * sphere, heading back out at half speed. One fired from inside goes out untouched.
 */
@GameTestHolder(WFLib.MODID)
@PrefixGameTestTemplate(false)
public class RebounderGameTest {
    private static final ResourceLocation SLUG = KineticPresetRegistry.rl("test_rebounder_slug");
    private static final double SPEED = 30.0D;
    private static final int UP = 40;

    static {
        KineticPresetRegistry.registerItemless(KineticPreset.builder(SLUG, null, WarheadRegistry.rl("inert"))
                .speed(SPEED).drag(0.0).gravity(0.0).life(100).mass(0.004).energyDamage(0.01).caliber(5.56)
                .blockPen(10.0).noChunkLoading().build());
    }

    @GameTest(template = "empty")
    public static void roundsGlanceOffAndShotsFromInsideGoOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO).above(UP);
        BlockState state = ModBlocks.REBOUNDER.get().defaultBlockState();
        boolean was = AnomalyConfig.enabled;
        AnomalyConfig.enabled = true;
        level.setBlockAndUpdate(pos, state);
        try {
            AnomalyBlockEntity be = (AnomalyBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(be != null, "no anomaly block entity");
            Vec3 c = be.center();
            double r = be.behaviour().radius();
            long in = Rounds.launch(level, KineticPresetRegistry.get(SLUG), c.add(-40.0D, 0.5D, 0.0D),
                    new Vec3(SPEED, 0.0D, 0.0D), null, null);
            long out = Rounds.launch(level, KineticPresetRegistry.get(SLUG), c.add(1.0D, 0.5D, 0.0D),
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
            AnomalyConfig.enabled = was;
        }
        helper.succeed();
    }
}
