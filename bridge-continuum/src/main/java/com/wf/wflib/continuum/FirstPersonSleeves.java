package com.wf.wflib.continuum;

import com.hbm.client.render.armor.ArmorRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.wflib.view.api.PlayerArms;
import dev.golem.core.render.RecordingSubmitSink;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Matrix3f;

/**
 * Continuum's modelled chestplates show on WFLib's first-person arms (the arms a gun mod such as MineAssault draws in place of vanilla's): the suit's arm
 * pieces on the posed upper arm and forearm, cut at the elbow. Client only.
 */
final class FirstPersonSleeves {
    private FirstPersonSleeves() {
    }

    static void install() {
        PlayerArms.SLEEVES.add((player, arms, base, light, pass) -> {
            var chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (chest.isEmpty()) return 0;
            PoseStack pose = new PoseStack();
            pose.last().pose().set(base);
            pose.last().normal().set(new Matrix3f(base).normal());
            RecordingSubmitSink sink = new RecordingSubmitSink();
            if (!ArmorRenderLayer.submitFirstPersonArms(chest, pose, sink, light, arms)) return 0;
            sink.flush(Minecraft.getInstance().renderBuffers().bufferSource(), player.level());
            return 3;
        });
        ContinuumBridge.LOG.info("Armour: Continuum's sleeves show on the first-person arms");
    }
}
