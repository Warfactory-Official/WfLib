package com.wf.wflib.corpse;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.corpse.api.CorpseEntity;
import com.wf.wflib.ragdoll.api.Ragdolls;
import com.wf.wflib.corpse.api.Corpses;
import com.wf.wflib.testkit.api.ForcedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * A killed soldier leaves a corpse; a lead drags it, hauls it up, flint and steel burns it away. Each test gets its own
 * open stone strip high above the world (the arena template is walled and roofed with barriers, which a dragged body hits).
 */
@PrefixGameTestTemplate(false)
public class CorpseGameTest {
    private static final int Y = 250, LENGTH = 48;

    /** A strip of floor {@code LENGTH} long (+z) at {@code lane}, forced loaded. */
    private record Lane(ServerLevel level, BlockPos at, List<ChunkPos> chunks) {
        void build() {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= LENGTH; z++) {
                    level.setBlockAndUpdate(at.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                    for (int up = 1; up <= 12; up++) {
                        level.setBlockAndUpdate(at.offset(x, up, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        Vec3 stand(double x, double up, double z) {
            return new Vec3(at.getX() + 0.5 + x, Y + 1 + up, at.getZ() + 0.5 + z);
        }
    }

    private static void inLane(GameTestHelper h, int lane, BiConsumer<Lane, GameTestSequence> body) {
        ServerLevel level = h.getLevel();
        BlockPos at = new BlockPos(3000 + lane * 40, Y, 9000);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int z = -16; z <= LENGTH + 16; z += 16) {
            chunks.add(new ChunkPos(at.offset(0, 0, z)));
        }
        ForcedChunks.set(level, chunks, true);
        Lane l = new Lane(level, at, chunks);
        GameTestSequence seq = h.startSequence().thenWaitUntil(ForcedChunks.ticking(level, chunks)).thenExecute(l::build);
        body.accept(l, seq);
    }


    private static CorpseEntity makeCorpse(GameTestHelper h, Lane lane, String id, String name) {
        // the lane's chunk is saved in the test world: bodies left by an earlier run (still burning, still leashed) come back with it
        lane.level().getEntitiesOfClass(CorpseEntity.class, new AABB(lane.at()).inflate(10)).forEach(CorpseEntity::discard);
        ServerPlayer dead = FakePlayerFactory.get(lane.level(), new GameProfile(UUID.nameUUIDFromBytes(id.getBytes()), name));
        dead.moveTo(lane.stand(0, 0, 0));
        Ragdolls.set(dead, true);                                            // a body to hand over
        h.assertTrue(Corpses.claim(dead, dead.getGameProfile(), dead), "the corpse system is off");
        var found = lane.level().getEntitiesOfClass(CorpseEntity.class, new AABB(lane.at()).inflate(6));
        h.assertTrue(!found.isEmpty(), "no corpse");
        for (int i = 1; i < found.size(); i++) {
            found.get(i).discard();
        }
        return found.get(0);
    }

    private static ServerPlayer holder(Lane lane, String name, double x, double up, double z) {
        ServerPlayer p = FakePlayerFactory.get(lane.level(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.moveTo(lane.stand(x, up, z));
        return p;
    }

    private static void release(Lane lane) {
        ForcedChunks.set(lane.level(), lane.chunks(), false);
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 600)
    public static void aLeadDragsACorpseBehindAWalkingHolder(GameTestHelper h) {
        inLane(h, 0, (lane, seq) -> {
            CorpseEntity[] c = new CorpseEntity[1];
            ServerPlayer[] p = new ServerPlayer[1];
            List<Double> z = new ArrayList<>();
            StringBuilder trace = new StringBuilder();
            double[] y0 = new double[1], maxY = {0};
            seq.thenExecute(() -> {
                c[0] = makeCorpse(h, lane, "drag_corpse", "DragCorpse");
                p[0] = holder(lane, "DragHolder", 0, 0, 2);
                h.assertTrue(c[0].canHaveALeashAttachedToIt(), "a corpse takes a lead");
                c[0].setLeashedTo(p[0], true);
            }).thenIdle(40).thenExecute(() -> {
                y0[0] = c[0].getY();
                h.assertTrue(Ragdolls.active(c[0]), "the body is simulating");
            }).thenExecuteFor(240, () -> {
                p[0].moveTo(p[0].getX(), p[0].getY(), p[0].getZ() + 0.15);       // 3 blocks/s, 36 blocks
                z.add(c[0].getZ());
                maxY[0] = Math.max(maxY[0], c[0].getY() - y0[0]);
                if (z.size() % 20 == 1) {
                    trace.append(String.format("t%d holder%.1f corpse%.1f y+%.2f %b; ", z.size(), p[0].getZ() - lane.at().getZ(),
                            c[0].getZ() - lane.at().getZ(), c[0].getY() - y0[0], c[0].isLeashed()));
                }
            }).thenExecute(() -> {
                h.assertTrue(c[0].isLeashed(), "the lead held: " + trace);
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (int i = 100; i + 10 < z.size(); i++) {                       // steady state, over half a second, blocks/s
                    double v = (z.get(i + 10) - z.get(i)) * 2;
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
                h.assertTrue(lo > 1.0 && hi < 5.0, String.format("pace swings from %.2f to %.2f blocks/s: ", lo, hi) + trace);
                h.assertTrue(maxY[0] < 0.7, "the body floated " + maxY[0] + " blocks up: " + trace);
                h.assertTrue(p[0].getZ() - c[0].getZ() < 6, "the body trails " + (p[0].getZ() - c[0].getZ()) + " blocks behind: " + trace);
                release(lane);
            }).thenSucceed();
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 600)
    public static void aLeadHaulsACorpseUpAndLetsItDownAgain(GameTestHelper h) {
        inLane(h, 1, (lane, seq) -> {
            CorpseEntity[] c = new CorpseEntity[1];
            ServerPlayer[] p = new ServerPlayer[1];
            double[] y0 = new double[1];
            seq.thenExecute(() -> {
                c[0] = makeCorpse(h, lane, "haul_corpse", "HaulCorpse");
                p[0] = holder(lane, "HaulHolder", 0, 0, 1);
                c[0].setLeashedTo(p[0], true);
            }).thenIdle(40).thenExecute(() -> {
                y0[0] = c[0].getY();
                p[0].moveTo(p[0].getX(), p[0].getY() + 3, p[0].getZ());          // on a ledge three blocks up
            }).thenWaitUntil(() -> h.assertTrue(c[0].getY() > y0[0] + 1.2, "hauled only " + (c[0].getY() - y0[0]) + " blocks toward a holder 3 above; leashed " + c[0].isLeashed()
                    + " active " + Ragdolls.active(c[0]) + " torso " + Ragdolls.torsoY(c[0]) + " holder " + p[0].getY() + " corpse " + c[0].getY()))
                    .thenExecute(() -> {
                p[0].moveTo(p[0].getX(), p[0].getY() - 3, p[0].getZ() + 4);      // back on the ground, further off
            }).thenIdle(160).thenExecute(() -> {
                h.assertTrue(c[0].getY() < y0[0] + 0.6, "left hanging " + (c[0].getY() - y0[0]) + " blocks up");
                release(lane);
            }).thenSucceed();
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 400)
    public static void aLeashedCorpseSurvivesSaving(GameTestHelper h) {
        inLane(h, 2, (lane, seq) -> seq.thenExecute(() -> {
            var corpse = makeCorpse(h, lane, "save_corpse", "SaveCorpse");
            corpse.setLeashedTo(holder(lane, "SaveHolder", 0, 0, 2), true);
            var tag = corpse.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            h.assertTrue(tag.contains("leash"), "the lead is written: " + tag.getAllKeys());
            release(lane);
        }).thenSucceed());
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 600)
    public static void flintAndSteelBurnsACorpseAway(GameTestHelper h) {
        inLane(h, 3, (lane, seq) -> {
            CorpseEntity[] c = new CorpseEntity[1];
            seq.thenExecute(() -> {
                lane.level().setWeatherParameters(6000, 0, false, false);      // rain would put it out: clear sky, and a roof in case another test makes it rain
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        lane.level().setBlockAndUpdate(lane.at().offset(dx, 4, dz), Blocks.STONE.defaultBlockState());
                    }
                }
                c[0] = makeCorpse(h, lane, "burn_corpse", "BurnCorpse");
                var player = holder(lane, "BurnHolder", 0, 0, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
                h.assertTrue(!c[0].burning(), "not burning yet");
                c[0].interact(player, InteractionHand.MAIN_HAND);
                h.assertTrue(c[0].burning(), "flint and steel lights it");
                h.assertTrue(player.getMainHandItem().getDamageValue() == 1, "the steel is worn");
            }).thenWaitUntil(() -> h.assertTrue(c[0].isRemoved(), "the corpse is still there after burning"))
                    .thenExecute(() -> release(lane)).thenSucceed();
        });
    }
}
