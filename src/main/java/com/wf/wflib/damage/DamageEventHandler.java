package com.wf.wflib.damage;

import com.wf.wflib.armor.ArmorSystem;
import com.wf.wflib.armor.api.ArmorResolution;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import com.wf.wflib.core.api.WFLib;

/**
 * The one reduction step, and the only place in this mod that changes how much a hit does.
 *
 * <p><b>Priority is load-bearing.</b> {@code NORMAL} is where later handlers read the reduced amount
 * as wound energy (internal bleeding, fracture chance and severity), so at {@code NORMAL} the same shot
 * could produce different injuries depending on mod load order. Armour resolves first, by name, at
 * {@code HIGH}.
 *
 * <p>The arithmetic itself moved to {@code com.wf.wflib.armor}: DT and DR per protection type,
 * per equipment slot, with the durability the hit cost charged to the layer that actually stopped it.
 * {@link DamageResistanceHandler} keeps the piercing context and entities' innate resistance, both of
 * which the resolver reads.
 */
@EventBusSubscriber(modid = WFLib.MODID)
public final class DamageEventHandler {

    private DamageEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (ArmorSystem.active()) {
            ArmorResolution.onIncomingDamage(event);
        }
    }
}
