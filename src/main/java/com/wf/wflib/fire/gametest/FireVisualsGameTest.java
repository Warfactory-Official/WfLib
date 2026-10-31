package com.wf.wflib.fire.gametest;

import com.wf.wflib.explosion.api.EMPCreator;
import com.wf.wflib.fire.api.FireLingeringEntity;
import com.wf.wflib.fire.api.FireType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.wf.wflib.core.api.WFLib;

/** Server side of the X-Ray fire and EMP visuals: the spawns and packets they hang off run clean. */
@PrefixGameTestTemplate(false)
public class FireVisualsGameTest {
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", batch = "fire")
    public static void everyFireTypeSpawnsAndEmpSendsItsPackets(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        for (FireType type : FireType.values()) {
            FireLingeringEntity fire = FireLingeringEntity.spawn(helper.getLevel(), at.x, at.y, at.z, 3.0F, 2.0F, 40,
                    type);
            helper.assertTrue(fire.isAlive() && fire.getFireType() == type, "fire " + type + " did not spawn");
        }
        EMPCreator.compose(helper.getLevel(), at.x, at.y, at.z, 48);
        EMPCreator.composeStun(helper.getLevel(), at.x, at.y, at.z);
        helper.succeed();
    }
}
