package com.wf.wflib.physics.gametest;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.physics.api.CarrierDecks;
import com.wf.wflib.physics.api.CarrierLink;
import com.wf.wflib.physics.api.DeckAttachment;
import com.wf.wflib.physics.api.DeckCollision;
import com.wf.wflib.physics.api.DeckRejoin;
import com.wf.wflib.physics.api.VehicleParenting;
import com.wf.wflib.testkit.api.ForcedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * Synthetic carrier ({@link DeckHull}) under way (translating, yawing, pitching, rolling) with two riders driven by
 * hand, one before and one after the carrier's tick, plus a body landing on the deck through {@link CarrierLink}.
 * Fixed world position => hash portable.
 */
@PrefixGameTestTemplate(false)
public class DeckGameTest {

    /** Rider poses/velocities/grip, walk steps, rejoin capture, deck casts after {@link #TICKS}; recorded against the source copy. */
    static final long GOLDEN = 745952119207117123L;
    static final int TICKS = 80;
    private static final BlockPos ORIGIN = new BlockPos(232, 200, 168);

    /** Ticks only when the test says so. */
    static final class Rider extends Pig {
        Rider(Level level) {
            super(EntityType.PIG, level);
        }

        @Override
        public void tick() {
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", timeoutTicks = 300)
    public static void ridersAndALanderFollowTheSource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> lane = List.of(new ChunkPos(ORIGIN));
        ForcedChunks.set(level, lane, true);
        DeckHull hull = new DeckHull(level, new Vector3f(5, 1, 8));
        DeckHull lander = new DeckHull(level, new Vector3f(1, 0.5f, 1.5f));
        Rider[] riders = new Rider[2];
        CarrierLink link = new CarrierLink();
        long[] h = {1};
        int[] landed = {0};
        int[] t = {0};
        helper.startSequence()
                .thenWaitUntil(ForcedChunks.ticking(level, lane))
                .thenExecute(() -> {
                    pose(hull, 0);
                    CarrierDecks.setRegistered(hull, true);
                    for (int i = 0; i < 2; i++) {
                        riders[i] = new Rider(level);
                        Vector3d at = new Vector3d(1 - 3 * i, 0.3 * i, 2 - 5 * i);
                        Vec3 world = VehicleParenting.toWorld(hull.deckSnapshot(), at, new Vector3d());
                        riders[i].moveTo(world.x, world.y, world.z, 10 * i, 0);
                        level.addFreshEntity(riders[i]);
                    }
                })
                .thenExecuteFor(TICKS, () -> h[0] = tick(hull, lander, riders, link, t[0]++, h[0], landed))
                .thenExecute(() -> {
                    long hash = h[0];
                    for (Rider rider : riders) {
                        CompoundTag tag = new CompoundTag();
                        DeckRejoin.save(rider, tag);
                        DeckAttachment attachment = VehicleParenting.attachmentOf(rider);
                        helper.assertTrue(attachment != null && attachment.gripped() && attachment.lastCarrySpeedSqr() > 0,
                                "rider not carried: " + rider);
                        helper.assertTrue(tag.contains("wflib_deck"), "no rejoin saved for " + rider);
                        CompoundTag deck = tag.getCompound("wflib_deck");
                        hash = mix(hash, Double.doubleToLongBits(deck.getDouble("LocalX")));
                        hash = mix(hash, Double.doubleToLongBits(deck.getDouble("LocalY")));
                        hash = mix(hash, Double.doubleToLongBits(deck.getDouble("LocalZ")));
                        hash = mix(hash, Float.floatToIntBits(deck.getFloat("Yaw")));
                        hash = mix(hash, deck.getLong("Chunk"));
                    }
                    VehicleParenting.releaseAll(hull, false);
                    CarrierDecks.unregister(hull);
                    for (Rider rider : riders) {
                        rider.discard();
                    }
                    ForcedChunks.set(level, lane, false);
                    helper.assertTrue(landed[0] > 0, "lander never met the deck");
                    helper.assertTrue(hash == GOLDEN, "deck hash " + hash + " != source " + GOLDEN);
                })
                .thenSucceed();
    }

    private static void pose(DeckHull hull, int t) {
        hull.pose(ORIGIN.getX() + 0.5 + 0.35 * t, ORIGIN.getY() + 0.02 * Math.sin(0.2 * t), ORIGIN.getZ() + 0.5 + 0.15 * t,
                0.9f * t, (float) (2 * Math.sin(0.1 * t)), (float) (1.5 * Math.sin(0.13 * t)));
    }

    private static long tick(DeckHull hull, DeckHull lander, Rider[] riders, CarrierLink link, int t, long h,
                             int[] landed) {
        h = walk(riders[0], t, 0, h);
        VehicleParenting.capture(hull);
        pose(hull, t + 1);
        VehicleParenting.tick(hull);
        h = walk(riders[1], t, 1, h);
        for (Rider rider : riders) {
            h = mix(h, Double.doubleToLongBits(rider.getX()));
            h = mix(h, Double.doubleToLongBits(rider.getY()));
            h = mix(h, Double.doubleToLongBits(rider.getZ()));
            h = mix(h, Float.floatToIntBits(rider.getYRot()));
            h = mix(h, Double.doubleToLongBits(rider.getDeltaMovement().x));
            h = mix(h, Double.doubleToLongBits(rider.getDeltaMovement().z));
            DeckAttachment attachment = VehicleParenting.attachmentOf(rider);
            if (attachment == null) {
                h = mix(h, -1);
                continue;
            }
            h = mix(h, attachment.airTicks());
            h = mix(h, Double.doubleToLongBits(attachment.lastCarrySpeedSqr()));
            h = mix(h, Float.floatToIntBits(VehicleParenting.walkStep(rider, attachment, false)));
            h = mix(h, DeckCollision.supportsOffset(rider, hull.deckSnapshot(), attachment, 0.4, -0.3, 1.0f) ? 1 : 0);
        }
        Vec3 at = VehicleParenting.toWorld(hull.deckSnapshot(), new Vector3d(1, 1.6 - 0.03 * t, -2), new Vector3d());
        lander.pose(at.x, at.y, at.z, 0.9f * (t + 1) + 7, 0, 0);
        AABB gather = lander.getBoundingBox().inflate(1);
        h = mix(h, link.refresh(lander, gather) ? 1 : 0);
        double toi = link.timeOfImpact(lander.hullObb(), 0, -0.4, 0.05);
        landed[0] += toi < 1 ? 1 : 0;
        h = mix(h, Double.doubleToLongBits(toi));
        h = mix(h, Double.doubleToLongBits(link.timeOfImpact(lander.hullObb(), 0.3, -0.1, -0.6)));
        h = mix(h, link.overlaps(lander.hullObb()) ? 1 : 0);
        return mix(h, link.deckCount());
    }

    /** Rider's own tick: walk + fall, clipped against the deck, then the entity-tick hook. */
    private static long walk(Rider rider, int t, int i, long h) {
        Vec3 move = new Vec3(0.06 * Math.cos(0.3 * t + i), -0.0784, 0.05 * Math.sin(0.2 * t + i));
        Vec3 clipped = VehicleParenting.clipMovement(rider, move);
        rider.setPos(rider.getX() + clipped.x, rider.getY() + clipped.y, rider.getZ() + clipped.z);
        VehicleParenting.onEntityTicked(rider);
        h = mix(h, Double.doubleToLongBits(clipped.x));
        h = mix(h, Double.doubleToLongBits(clipped.y));
        return mix(h, Double.doubleToLongBits(clipped.z));
    }

    private static long mix(long h, long v) {
        return h * 0x9E3779B97F4A7C15L + v;
    }
}
