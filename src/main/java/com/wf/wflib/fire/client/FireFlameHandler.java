package com.wf.wflib.fire.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.fire.api.FireLingeringEntity;
import com.wf.wflib.fx.api.FlywheelEffectManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class FireFlameHandler {

    private FireFlameHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof FireLingeringEntity fire)) return;
        if (!FlywheelEffectManager.isAvailable(event.getLevel())) return;

        FlywheelEffectManager.spawn(new InstancedFlameEffect(fire));
    }
}
