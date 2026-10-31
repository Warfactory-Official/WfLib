package com.wf.wflib.anomaly.gametest;

import com.wf.wflib.anomaly.api.AnomalyCaves;
import com.wf.wflib.artifact.api.ZoneArtifact;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.wf.wflib.core.api.WFLib;

/** Cave fields built into the test world: chamber cut, anomalies set, exactly the budgeted artefacts spawned. */
@PrefixGameTestTemplate(false)
public class CaveFieldGameTest {

    @GameTest(templateNamespace = WFLib.MODID, template = "empty", batch = "anomaly_caves", timeoutTicks = 400)
    public static void fieldHoldsExactlyItsArtefactsInACutChamber(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int tier = 1; tier <= 4; tier++) {
            int artefacts = tier * 3;
            BlockPos at = new BlockPos(tier * 200 + 5000, 70, 5000);
            AnomalyCaves.Made made = AnomalyCaves.place(level, at, tier, artefacts);
            helper.assertTrue(made.anomalies() > 0, "t" + tier + ": no anomalies");
            helper.assertTrue(made.artefacts() == artefacts, "t" + tier + ": " + made.artefacts() + " artefacts");
            int r = made.plan().radius() + 8;
            AABB box = new AABB(at.getX() - r, at.getY() - 20, at.getZ() - r, at.getX() + r, at.getY() + 30,
                    at.getZ() + r);
            int found = level.getEntitiesOfClass(ZoneArtifact.class, box).size();
            helper.assertTrue(found == artefacts, "t" + tier + ": " + found + " artefact entities, want " + artefacts);
            int anomalyBlocks = 0;
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                if (level.getBlockState(p).getBlock().builtInRegistryHolder().key().location().getNamespace()
                        .equals(WFLib.MODID) && !level.getBlockState(p).isAir()) {
                    anomalyBlocks++;
                }
            }
            helper.assertTrue(anomalyBlocks >= made.anomalies(), "t" + tier + ": " + anomalyBlocks + " wflib blocks, "
                    + made.anomalies() + " anomalies");
        }
        helper.succeed();
    }
}
