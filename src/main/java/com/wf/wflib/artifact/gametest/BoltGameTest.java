package com.wf.wflib.artifact.gametest;

import com.wf.wflib.WFLib;
import com.wf.wflib.anomaly.AnomalyBlockEntity;
import com.wf.wflib.anomaly.AnomalyConfig;
import com.wf.wflib.artifact.detect.ArtifactEntities;
import com.wf.wflib.artifact.detect.BoltEntity;
import com.wf.wflib.artifact.detect.Bolts;
import com.wf.wflib.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

/**
 * Thrown bolts: rest flat on flat ground, on stairs and after a wall; set anomalies off; come back as the same stack;
 * a throw takes one. Each test builds at its own height: arenas in one batch stand a few blocks apart.
 */
@GameTestHolder(WFLib.MODID)
@PrefixGameTestTemplate(false)
public class BoltGameTest {
    private static final String BATCH = "bolts";

    private static BoltEntity drop(GameTestHelper helper, ItemStack stack, Vec3 relative, Vec3 velocity) {
        ServerLevel level = helper.getLevel();
        BoltEntity bolt = new BoltEntity(ArtifactEntities.BOLT.get(), level);
        bolt.setItem(stack);
        Vec3 at = helper.absoluteVec(relative);
        bolt.setPos(at.x, at.y, at.z);
        bolt.setDeltaMovement(velocity);
        level.addFreshEntity(bolt);
        return bolt;
    }

    private static void floor(GameTestHelper helper, int y, int from, int to) {
        for (int x = from; x <= to; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    private static boolean flat(BoltEntity bolt) {
        return Math.abs(bolt.orientation().transform(new Vector3f(0.0F, 0.0F, 1.0F)).y) > 0.999F;
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void restsFlatOnFlatGround(GameTestHelper helper) {
        floor(helper, 100, -2, 2);
        BoltEntity bolt = drop(helper, new ItemStack(ArtifactEntities.BOLT_ITEM.get()), new Vec3(0.5D, 102.0D, 0.5D),
                new Vec3(0.12D, 0.0D, 0.04D));
        helper.succeedWhen(() -> {
            helper.assertTrue(bolt.resting(), "still moving: " + bolt.getDeltaMovement());
            helper.assertTrue(flat(bolt), "not lying flat");
            helper.assertTrue(bolt.getY() - helper.absoluteVec(Vec3.ZERO).y < 101.1D, "not on the floor: " + bolt.getY());
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void comesToRestOnStairs(GameTestHelper helper) {
        // Four steps down toward +x, then floor.
        for (int i = 0; i < 4; i++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 110; y < 114 - i; y++) {
                    helper.setBlock(new BlockPos(i, y, z), Blocks.STONE);
                }
                helper.setBlock(new BlockPos(i, 114 - i, z), Blocks.STONE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
            }
        }
        for (int x = 4; x <= 6; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(new BlockPos(x, 110, z), Blocks.STONE);
            }
        }
        BoltEntity bolt = drop(helper, new ItemStack(ArtifactEntities.BOLT_ITEM.get()), new Vec3(0.5D, 116.0D, 0.5D),
                new Vec3(0.15D, 0.0D, 0.0D));
        helper.succeedWhen(() -> {
            helper.assertTrue(bolt.resting(), "still moving: " + bolt.getDeltaMovement() + " at " + bolt.position());
            double y = bolt.getY() - helper.absoluteVec(Vec3.ZERO).y;
            helper.assertTrue(y >= 111.0D && y <= 115.1D, "fell off the stairs: y " + y);
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void bouncesOffAWallAndRests(GameTestHelper helper) {
        floor(helper, 120, -2, 3);
        for (int y = 121; y <= 124; y++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
            }
        }
        BoltEntity bolt = drop(helper, new ItemStack(ArtifactEntities.BOLT_ITEM.get()), new Vec3(-1.0D, 122.0D, 0.5D),
                new Vec3(0.6D, 0.05D, 0.0D));
        double wall = helper.absoluteVec(new Vec3(3.0D, 0.0D, 0.0D)).x;
        boolean[] back = {false};
        helper.onEachTick(() -> back[0] |= bolt.getDeltaMovement().x < 0.0D);
        helper.succeedWhen(() -> {
            helper.assertTrue(back[0], "never came back off the wall");
            helper.assertTrue(bolt.resting(), "still moving: " + bolt.getDeltaMovement());
            helper.assertTrue(bolt.getX() < wall, "went through the wall");
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void setsOffASpringboard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper, 130, -2, 2);
        BlockPos pos = helper.absolutePos(new BlockPos(0, 131, 0));
        boolean was = AnomalyConfig.enabled;
        AnomalyConfig.enabled = true;
        level.setBlockAndUpdate(pos, ModBlocks.SPRINGBOARD.get().defaultBlockState());
        AnomalyBlockEntity be = (AnomalyBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(be != null, "no anomaly block entity");
        drop(helper, new ItemStack(ArtifactEntities.BOLT_ITEM.get()), new Vec3(0.5D, 133.0D, 0.5D),
                new Vec3(0.0D, -0.2D, 0.0D));
        helper.succeedWhen(() -> {
            helper.assertTrue(be.phase() == AnomalyBlockEntity.Phase.BLOWOUT, "not set off: " + be.phase());
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            AnomalyConfig.enabled = was;
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void walkingOverItGivesTheSameStackBack(GameTestHelper helper) {
        floor(helper, 140, -2, 2);
        ItemStack named = new ItemStack(ArtifactEntities.BOLT_ITEM.get());
        named.set(DataComponents.CUSTOM_NAME, Component.literal("lucky"));
        BoltEntity ours = drop(helper, named.copy(), new Vec3(0.5D, 141.5D, 0.5D), Vec3.ZERO);
        // Any other mod's bolt rides along as itself.
        BoltEntity theirs = drop(helper, new ItemStack(Items.IRON_NUGGET), new Vec3(-0.5D, 141.5D, 0.5D), Vec3.ZERO);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.succeedWhen(() -> {
            helper.assertTrue(ours.resting() && theirs.resting(), "not landed");
            ours.playerTouch(player);
            theirs.playerTouch(player);
            helper.assertTrue(ours.isRemoved() && theirs.isRemoved(), "not picked up");
            helper.assertTrue(ItemStack.isSameItemSameComponents(player.getInventory().getItem(0), named),
                    "came back as " + player.getInventory().getItem(0));
            helper.assertTrue(player.getInventory().getItem(1).is(Items.IRON_NUGGET),
                    "came back as " + player.getInventory().getItem(1));
        });
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void throwingTakesOneUnlessCreative(GameTestHelper helper) {
        for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
            Player player = helper.makeMockPlayer(mode);
            player.getAbilities().instabuild = mode == GameType.CREATIVE;
            player.setPos(helper.absoluteVec(new Vec3(0.5D, 150.0D, 0.5D)));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArtifactEntities.BOLT_ITEM.get(), 3));
            helper.assertTrue(Bolts.toss(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult()
                    .consumesAction(), mode + ": not thrown");
            int left = player.getMainHandItem().getCount();
            helper.assertTrue(left == (mode == GameType.CREATIVE ? 3 : 2), mode + ": " + left + " left");
            helper.assertFalse(Bolts.toss(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult()
                    .consumesAction(), mode + ": thrown again inside the cooldown");
            helper.getLevel().getEntitiesOfClass(BoltEntity.class, player.getBoundingBox().inflate(3.0D))
                    .forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.assertTrue(Bolts.isBolt(new ItemStack(ArtifactEntities.BOLT_ITEM.get())), "wflib:bolt not a bolt");
        helper.assertFalse(Bolts.isBolt(new ItemStack(Items.IRON_NUGGET)), "a nugget is a bolt");
        helper.succeed();
    }
}
