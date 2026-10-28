package com.wf.wflib.smoking;

import com.norwood.ahf.hook.AhfHooks;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

public final class Smoking {
    private Smoking() {
    }

    /** Common setup, both sides. */
    public static void bootstrap() {
        AhfHooks.addHandTargets(Smoking::targets);
    }

    private static int targets(LivingEntity e, AhfHooks.HandFrame frame, long tick, Vector3f right, Vector3f left) {
        if (!(e instanceof Player p) || !p.isUsingItem() || !(p.getUseItem().getItem() instanceof CigaretteItem)) {
            return 0;
        }
        HumanoidArm arm = p.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? p.getMainArm() : p.getMainArm().getOpposite();
        float seconds = (SmokingTimeline.TICKS - p.getUseItemRemainingTicks()) / 20.0f;
        Vector3f out = arm == HumanoidArm.RIGHT ? right : left;
        SmokingTimeline.wrist(seconds, out);
        if (arm == HumanoidArm.LEFT) {
            out.x = -out.x;
        }
        return 1 << arm.ordinal();
    }
}
