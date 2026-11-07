package com.wf.wflib.round.gametest;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.labs.api.LabCover;
import com.wf.wflib.labs.api.LabFlight;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.round.api.event.RoundPierceEvent;
import com.wf.wflib.testkit.api.ForcedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** WFLabs rows == live rounds: flight recurrence and block penetration. */
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = WFLib.MODID)
public class RoundLabGameTest {
    private static final String TEMPLATE = "empty";
    private static final List<RoundPierceEvent> PIERCES = new CopyOnWriteArrayList<>();

    @SubscribeEvent
    public static void onPierce(RoundPierceEvent event) {
        PIERCES.add(event);
    }

    private record Case(String round, String block, int blocks) {
    }

    /** Live pierce events of one real shot straight up through {@code blocks} stacked blocks == {@link LabCover}. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = "wflib_labs")
    public static void coverRowsEqualLivePierces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Case[] cases = {
                new Case("wflib:5.56x45/ball", "minecraft:oak_planks", 1),
                new Case("wflib:5.56x45/ball", "minecraft:oak_planks", 2),
                new Case("wflib:5.56x45/ball", "minecraft:stone", 1),
                new Case("wflib:12.7x99/ball", "minecraft:stone", 1),
                new Case("wflib:12.7x99/ball", "minecraft:oak_planks", 2),
                new Case("wflib:12.7x99/ball", "minecraft:dirt", 2),
                new Case("wflib:12.7x99/ap", "minecraft:iron_block", 1)};
        for (Case c : cases) {
            KineticPreset p = KineticPresets.get(ResourceLocation.parse(c.round()));
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(c.block()));
            BlockPos wall = BlockPos.containing(centre(helper, 10.0));
            for (int i = 0; i < c.blocks(); i++) {
                level.setBlockAndUpdate(wall.above(i), block.defaultBlockState());
            }
            PIERCES.clear();
            long key = Rounds.launch(level, p, centre(helper, 5.0), new Vec3(0.0, p.muzzleSpeed(), 0.0), null, null);
            Rounds.step(level, key);
            Rounds.destroy(level, key);
            for (int i = 0; i < c.blocks(); i++) {
                level.setBlockAndUpdate(wall.above(i), Blocks.AIR.defaultBlockState());
            }
            List<RoundPierceEvent> live = PIERCES.stream().filter(e -> e.key() == key).toList();
            helper.assertTrue(!live.isEmpty(), c + ": no pierce event");
            RoundPierceEvent last = live.get(live.size() - 1);
            LabCover.Result lab = LabCover.evaluate(p, p.muzzleSpeed() * 20.0, LabCover.material(c.block()),
                    c.blocks(), 0.0, 1, 1L);
            if (last.pass().exited()) {
                double exit = last.pass().velocity().length() * 20.0;
                double slack = live.size() == 1 ? 1.0e-9 : 0.01;
                helper.assertTrue(live.size() == c.blocks() && lab.pPerforate() == 1.0
                                && exit >= lab.exitSpeed() * (1.0 - 1.0e-9) && exit <= lab.exitSpeed() * (1.0 + slack),
                        c + ": live exit " + exit + ", lab " + lab);
            } else {
                double depth = 0.0;
                for (RoundPierceEvent e : live) {
                    depth += e.entry().getLocation().distanceTo(e.pass().point());
                }
                helper.assertTrue(lab.pStop() == 1.0 && Math.abs(lab.depth() - depth) <= 1.0e-9,
                        c + ": live depth " + depth + ", lab " + lab);
            }
        }
        helper.succeed();
    }

    /** Positions and speeds after each live step == {@link LabFlight} at the live range. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = "wflib_labs", timeoutTicks = 300)
    public static void flightRowsEqualLiveSteps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KineticPreset p = KineticPresets.get(ResourceLocation.parse("wflib:7.62x51/ball"));
        double muzzle = 120.0;
        Vec3 from = new Vec3(helper.absolutePos(BlockPos.ZERO).getX() + 0.5, helper.absolutePos(BlockPos.ZERO).getY() + 60,
                helper.absolutePos(BlockPos.ZERO).getZ() + 20000.5);
        Vec3 to = from.add(60.0, 0.0, 0.0);
        List<ChunkPos> lane = ForcedChunks.along(from, to);
        helper.startSequence()
                .thenExecute(() -> ForcedChunks.set(level, lane, true))
                .thenWaitUntil(ForcedChunks.ticking(level, lane))
                .thenExecute(() -> {
                    long key = Rounds.launch(level, p, from, new Vec3(muzzle / 20.0, 0.0, 0.0), null, null);
                    for (int k = 1; k <= 8; k++) {
                        Vec3 before = Rounds.velocity(level, key);
                        helper.assertTrue(Rounds.step(level, key), "round ended at step " + k);
                        Vec3 at = Rounds.position(level, key);
                        double range = at.x - from.x - 1.0e-7;
                        LabFlight.Sample s = LabFlight.fly(p, muzzle, new double[]{range})[0];
                        helper.assertTrue(s.reached() && s.tick() == k, "tick " + s.tick() + " != " + k);
                        helper.assertTrue(Math.abs(s.speed() - before.length() * 20.0) <= 1.0e-6 * s.speed(),
                                "step " + k + " speed " + s.speed() + " live " + before.length() * 20.0);
                        helper.assertTrue(Math.abs(s.drop() - (from.y - at.y)) <= 1.0e-6,
                                "step " + k + " drop " + s.drop() + " live " + (from.y - at.y));
                    }
                    Rounds.destroy(level, key);
                    ForcedChunks.set(level, lane, false);
                })
                .thenSucceed();
    }

    private static Vec3 centre(GameTestHelper helper, double height) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new Vec3(origin.getX() + 0.5, origin.getY() + height, origin.getZ() + 0.5);
    }
}
