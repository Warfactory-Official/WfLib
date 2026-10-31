package com.wf.wflib.fire;

import com.wf.wflib.damage.api.DamageClass;
import com.wf.wflib.damage.api.WFDamageSources;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import com.wf.wflib.fire.api.WFFire;
import com.wf.wflib.fire.api.WFFireData;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.fire.api.AshHandler;

/**
 * Drives the custom-fire lifecycle: burns living entities each tick and routes fire deaths to the cremation effect.
 */
@EventBusSubscriber(modid = WFLib.MODID)
public final class FireHandler {

    /**
     * Ticks between fire-damage applications (1 second).
     */
    private static final int DAMAGE_INTERVAL = 20;

    private FireHandler() {
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide) return;

        WFFireData fire = WFFire.get(entity);
        if (fire == null || !fire.isBurning()) return;

        entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 2));

        if (!entity.fireImmune() && entity.tickCount % DAMAGE_INTERVAL == 0) {
            DamageSource source = WFDamageSources.create(entity.level(), DamageClass.FIRE, null);
            entity.hurt(source, fire.getType().damage);
        }

        fire.tick();
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        AshHandler.decideGore(event.getEntity(), event.getSource());
    }
}
