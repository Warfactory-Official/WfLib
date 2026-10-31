package com.wf.wflib.fire.client;

import com.wf.wflib.fire.api.FireLingeringEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import com.wf.wflib.fx.api.WFDynamicLight;
import com.wf.wflib.fx.api.WFLights;
import com.wf.wflib.core.api.WFLib;

@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class FireLightHandler {

    private FireLightHandler() {
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof FireLingeringEntity fire) {
            WFDynamicLight.remove(fire.getId());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            WFDynamicLight.clear();
            WFLights.clear();
            com.wf.wflib.explosion.api.client.Removals.clear();
            com.wf.wflib.explosion.api.client.BlastDebris.clear();
        }
    }
}
