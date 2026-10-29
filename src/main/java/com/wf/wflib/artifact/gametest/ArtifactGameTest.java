package com.wf.wflib.artifact.gametest;

import com.wf.wflib.WFLib;
import com.wf.wflib.artifact.Artifact;
import com.wf.wflib.artifact.Artifacts;
import com.wf.wflib.artifact.detect.ArtifactEntities;
import com.wf.wflib.artifact.detect.Detection;
import com.wf.wflib.artifact.detect.ZoneArtifact;
import com.wf.wflib.damage.WFDamageTypes;
import com.wf.wflib.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Damage types -> STALKER hit kinds; a Zone artefact shows to, and is taken by, only a detector that finds it; drifts
 * about home unseen and settles once shown; a dropped one rolls as itself and is walked up. Arenas at their own heights.
 */
@GameTestHolder(WFLib.MODID)
@PrefixGameTestTemplate(false)
public class ArtifactGameTest {
    private static final Map<ResourceKey<DamageType>, Artifact.Hit> HITS = Map.ofEntries(
            Map.entry(WFDamageTypes.ELECTRIC, Artifact.Hit.SHOCK), Map.entry(DamageTypes.LIGHTNING_BOLT, Artifact.Hit.SHOCK),
            Map.entry(DamageTypes.IN_FIRE, Artifact.Hit.BURN), Map.entry(WFDamageTypes.FIRE, Artifact.Hit.BURN),
            Map.entry(DamageTypes.EXPLOSION, Artifact.Hit.EXPLOSION),
            Map.entry(WFDamageTypes.EXPLOSIVE, Artifact.Hit.EXPLOSION),
            Map.entry(WFDamageTypes.ACID, Artifact.Hit.CHEMICAL), Map.entry(WFDamageTypes.GAS, Artifact.Hit.CHEMICAL),
            Map.entry(DamageTypes.ARROW, Artifact.Hit.BULLET),
            Map.entry(WFDamageTypes.GRAVITY_ANOMALY, Artifact.Hit.STRIKE), Map.entry(DamageTypes.FALL, Artifact.Hit.STRIKE),
            Map.entry(DamageTypes.FLY_INTO_WALL, Artifact.Hit.STRIKE),
            Map.entry(DamageTypes.MOB_ATTACK, Artifact.Hit.WOUND), Map.entry(DamageTypes.PLAYER_ATTACK, Artifact.Hit.WOUND));

    @GameTest(template = "empty", batch = "artifacts")
    public static void damageTypesMapToStalkerHits(GameTestHelper helper) {
        var types = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        HITS.forEach((key, hit) -> {
            Artifact.Hit got = Artifacts.hit(new DamageSource(types.getHolderOrThrow(key)));
            helper.assertTrue(got == hit, key.location() + " -> " + got + ", expected " + hit);
        });
        helper.assertTrue(Artifacts.hit(new DamageSource(types.getHolderOrThrow(DamageTypes.STARVE))) == null,
                "starving is no STALKER hit");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "artifacts")
    public static void onlyADetectorThatFindsItRevealsAndTakesIt(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        ZoneArtifact hidden = new ZoneArtifact(helper.getLevel(), new ItemStack(ModItems.ARTIFACTS.get("fireball").get()),
                at.x, at.y, at.z, BlockPos.containing(at));
        helper.getLevel().addFreshEntity(hidden);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(at.x + 2.0D, at.y - 1.0D, at.z);

        helper.assertFalse(Detection.revealed(player, hidden), "shown with no detector");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ArtifactEntities.DETECTOR_ECHO.get()));
        helper.assertFalse(Detection.revealed(player, hidden), "the Echo found a rank-3 artefact");
        hidden.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(hidden.isAlive(), "taken without being found");

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ArtifactEntities.DETECTOR_VELES.get()));
        helper.assertTrue(Detection.revealed(player, hidden), "the Veles did not show it 2 blocks off");
        player.setPos(at.x + 8.0D, at.y - 1.0D, at.z);
        helper.assertFalse(Detection.revealed(player, hidden), "shown past the reveal radius");
        helper.assertTrue(Detection.heard(player, Detection.held(player)).contains(hidden), "not heard 8 blocks off");

        player.setPos(at.x + 2.0D, at.y - 1.0D, at.z);
        hidden.interact(player, InteractionHand.MAIN_HAND);
        helper.assertFalse(hidden.isAlive(), "not taken");
        helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.ARTIFACTS.get("fireball").get())),
                "not in the inventory");
        helper.succeed();
    }

    private static void floor(GameTestHelper helper, int y, int half) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    private static boolean still(ZoneArtifact a) {
        return a.onGround() && a.getDeltaMovement().lengthSqr() == 0.0D;
    }

    @GameTest(template = "empty", batch = "artifacts", timeoutTicks = 200)
    public static void aDroppedArtefactRollsAsItselfAndIsWalkedUp(GameTestHelper helper) {
        floor(helper, 130, 3);
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 132.0D, 0.5D));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z,
                new ItemStack(ModItems.ARTIFACTS.get("battery").get()));
        item.setDeltaMovement(0.15D, 0.0D, 0.0D);
        helper.getLevel().addFreshEntity(item);
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(at, at).inflate(4.0D);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(),
                "an item entity stayed");
        var found = helper.getLevel().getEntitiesOfClass(ZoneArtifact.class, around);
        helper.assertTrue(found.size() == 1 && found.get(0).isDropped(), "not swapped the same tick: " + found);
        ZoneArtifact[] dropped = {found.get(0)};
        helper.succeedWhen(() -> {
            helper.assertTrue(dropped[0] != null && still(dropped[0]), "still rolling");
            ZoneArtifact a = dropped[0];
            helper.assertTrue(a.getX() > at.x + 0.3D, "never rolled: " + a.position());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(a.getX(), a.getY(), a.getZ());
            a.playerTouch(player);
            helper.assertFalse(a.isAlive(), "not walked up");
            helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.ARTIFACTS.get("battery").get())),
                    "not in the inventory");
        });
    }

    @GameTest(template = "empty", batch = "artifacts", timeoutTicks = 300)
    public static void anUnseenZoneArtefactDriftsAboutHome(GameTestHelper helper) {
        int half = (int) ZoneArtifact.RANGE + 6;
        floor(helper, 140, half);
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 141.0D, 0.5D));
        ZoneArtifact a = new ZoneArtifact(helper.getLevel(), new ItemStack(ModItems.ARTIFACTS.get("soul").get()),
                at.x, at.y, at.z, BlockPos.containing(at));
        helper.getLevel().addFreshEntity(a);
        double[] path = {0.0D};
        Vec3[] last = {at};
        helper.onEachTick(() -> {
            path[0] += a.position().subtract(last[0]).horizontalDistance();
            last[0] = a.position();
            double d = a.position().subtract(at).horizontalDistance();
            helper.assertTrue(d < ZoneArtifact.RANGE + 5.0D, "drifted off: " + d);
            helper.assertTrue(a.getY() > at.y - 1.5D, "fell through: " + a.getY());
        });
        helper.succeedWhen(() -> helper.assertTrue(path[0] > 8.0D, "barely moved: " + path[0]));
    }

    @GameTest(template = "empty", batch = "artifacts", timeoutTicks = 200)
    public static void aZoneArtefactShownByADetectorSettles(GameTestHelper helper) {
        floor(helper, 150, 8);
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 151.0D, 0.5D));
        ZoneArtifact a = new ZoneArtifact(helper.getLevel(), new ItemStack(ModItems.ARTIFACTS.get("soul").get()),
                at.x, at.y, at.z, BlockPos.containing(at));
        helper.getLevel().addFreshEntity(a);
        ServerPlayer player = com.wf.wflib.radiation.gametest.NeutronActivationGameTest.mockServerPlayer(helper);
        player.setPos(at.x + 2.0D, at.y, at.z);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ArtifactEntities.DETECTOR_VELES.get()));
        helper.runAfterDelay(150, () -> {
            try {
                helper.assertTrue(still(a), "still moving: " + a.getDeltaMovement());
                helper.assertTrue(a.position().subtract(at).horizontalDistance() < 1.0D, "drifted while shown: "
                        + a.position().subtract(at));
            } finally {
                com.wf.wflib.radiation.gametest.NeutronActivationGameTest.remove(player);
            }
            helper.succeed();
        });
    }
}
