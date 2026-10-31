package com.wf.wflib.round.gametest;

import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.round.pen.BlockDamage;
import com.wf.wflib.round.pen.PenMaterial;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.wf.wflib.core.api.WFLib;

/**
 * Impulse block damage. Ball: 4 g, 800 m/s, blockPen 10 => through a plank at {@code sqrt(0.4)} speed, 1.18 kg m/s
 * left. Slug: 28 g, 480 m/s, blockPen 3 < plank 6 => stops, 13.44 left. Plank toughness 36.
 */
@PrefixGameTestTemplate(false)
public class BlockDamageGameTest {

    private static final String TEMPLATE = "empty";
    private static final ResourceLocation BALL = KineticPresets.rl("test_damage_ball");
    private static final ResourceLocation SLUG = KineticPresets.rl("test_damage_slug");
    private static final double BALL_SPEED = 40.0;
    private static final double SLUG_SPEED = 24.0;

    public static void register() {
        KineticPresets.registerItemless(KineticPreset.builder(BALL, null, Warheads.rl("inert"))
                .speed(BALL_SPEED).drag(0.0).gravity(0.0).life(100).mass(0.004).energyDamage(0.01).caliber(5.56)
                .blockPen(10.0).noChunkLoading().build());
        KineticPresets.registerItemless(KineticPreset.builder(SLUG, null, Warheads.rl("inert"))
                .speed(SLUG_SPEED).drag(0.0).gravity(0.0).life(100).mass(0.028).energyDamage(0.01).caliber(18.5)
                .blockPen(3.0).noChunkLoading().build());
    }

    /** ceil(36 / 1.176) = 31 passing ball vs ceil(36 / 13.44) = 3 stopped slugs. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void slugsBreakAPlankBallPassesCleanly(GameTestHelper helper) {
        int ball = shotsToBreak(helper, BALL, BALL_SPEED, Blocks.OAK_PLANKS, 1);
        int slug = shotsToBreak(helper, SLUG, SLUG_SPEED, Blocks.OAK_PLANKS, 3);
        double lost = 0.004 * BALL_SPEED * 20.0 * (1.0 - Math.sqrt(1.0 - PenMaterial.WOOD.resistance / 10.0));
        helper.assertTrue(ball == (int) Math.ceil(PenMaterial.WOOD.toughness / lost), "ball shots " + ball);
        helper.assertTrue(slug == 3, "slug shots " + slug);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void glassShattersOnOneHit(GameTestHelper helper) {
        helper.assertTrue(shotsToBreak(helper, BALL, BALL_SPEED, Blocks.GLASS, 1) == 1, "glass survived a hit");
        helper.succeed();
    }

    /** Stopped ball: 3.2 of 360 kg m/s; stone stays. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void stoneKeepsTheImpulseOfAStoppedRound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wall = wall(helper, 1);
        level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        fire(helper, BALL, BALL_SPEED, 1);
        float damage = BlockDamage.damage(level, wall);
        boolean intact = level.getBlockState(wall).is(Blocks.STONE);
        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        helper.assertTrue(intact, "stone destroyed");
        helper.assertTrue(Math.abs(damage - 0.004 * BALL_SPEED * 20.0) < 1.0e-4, "damage " + damage);
        helper.succeed();
    }

    /** Shots, one step each, until {@code block} at column {@code dx} goes; 100 => cap. */
    private static int shotsToBreak(GameTestHelper helper, ResourceLocation preset, double speed, Block block,
                                    int dx) {
        ServerLevel level = helper.getLevel();
        BlockPos wall = wall(helper, dx);
        level.setBlockAndUpdate(wall, block.defaultBlockState());
        int n = 0;
        while (n < 100 && level.getBlockState(wall).is(block)) {
            fire(helper, preset, speed, dx);
            n++;
        }
        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        return n;
    }

    private static void fire(GameTestHelper helper, ResourceLocation preset, double speed, int dx) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        long key = Rounds.launch(level, KineticPresets.get(preset),
                new Vec3(o.getX() + dx + 0.5, o.getY() + 5.0, o.getZ() + 0.5), new Vec3(0.0, speed, 0.0), null, null);
        Rounds.step(level, key);
        Rounds.destroy(level, key);
    }

    private static BlockPos wall(GameTestHelper helper, int dx) {
        return helper.absolutePos(BlockPos.ZERO).offset(dx, 10, 0);
    }
}
