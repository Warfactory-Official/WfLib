package com.wf.wflib.missile.gametest;

import com.wf.wflib.armor.lab.ExplosiveProducer;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.explosion.api.EntityProcessorCross;
import com.wf.wflib.explosion.api.ExplosionAEF;
import com.wf.wflib.explosion.api.PlayerProcessorStandard;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.missile.api.MissilePreset;
import com.wf.wflib.missile.api.MissilePresets;
import com.wf.wflib.missile.lab.MissileVulnerabilityProducer;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** {@code missile_vulnerability} row == live blasts at a held, armed missile until it is downed. */
@PrefixGameTestTemplate(false)
public class MissileLabGameTest {

    private static final String TEMPLATE = "empty";
    private static final float SIZE = 4.0f;
    private static final double DISTANCE = 3.0;

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, timeoutTicks = 100)
    public static void liveBlastsDownAMissileInTheLabsHitCount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MissilePreset missile = MissilePresets.get(MissilePresets.rl("cruise"));
        helper.assertTrue(missile != null, "wflib:cruise not live");
        EntityProcessorCross processor = new EntityProcessorCross();
        Float dmg = ExplosiveProducer.damage(processor, SIZE, DISTANCE);
        helper.assertTrue(dmg != null, "lab: out of range");
        Integer lab = MissileVulnerabilityProducer.hits(level, missile, level.damageSources().explosion(null), dmg)
                .toDown();
        helper.assertTrue(lab != null && lab > 1, "lab hits to down " + lab + ", want > 1");

        Vec3 at = helper.absoluteVec(new Vec3(1.5, 8.0, 1.5));
        MissileEntity target = missile.builder(level, at.add(0.0, -6.0, 0.0)).detonation(Warheads.rl("inert"))
                .startArmed().build();
        target.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        level.addFreshEntity(target);
        int[] blasts = {0};
        int[] downedAt = {-1};
        helper.onEachTick(() -> {
            if (downedAt[0] >= 0) {
                return;
            }
            if (target.damage().isDowned() || target.fuze().detonated()) {
                downedAt[0] = blasts[0];
                return;
            }
            target.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
            target.setDeltaMovement(Vec3.ZERO);
            AABB box = target.getBoundingBox();
            Vec3 c = box.getCenter();
            new ExplosionAEF(level, c.x, c.y, box.minZ - DISTANCE, SIZE)
                    .setEntityProcessor(new EntityProcessorCross())
                    .setPlayerProcessor(new PlayerProcessorStandard())
                    .explode();
            blasts[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(downedAt[0] >= 0, "not downed after " + blasts[0] + " blasts");
            target.discard();
            helper.assertTrue(downedAt[0] == lab, "live " + downedAt[0] + " blasts, lab " + lab);
        });
    }
}
