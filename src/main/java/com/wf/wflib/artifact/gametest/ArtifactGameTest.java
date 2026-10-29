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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Damage types -> STALKER hit kinds; a Zone artefact shows to, and is taken by, only a detector that finds it. */
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
                at.x, at.y, at.z);
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
}
