package com.wf.wflib.damage;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.damage.api.MissileDamageResponses;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class DamageSystem implements WFSystem {
    @Override
    public String id() {
        return "damage";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        MissileDamageResponses.register(MissileDamageResponses.defaultId(), MissileDamageResponses.STANDARD);
        MissileDamageResponses.register(MissileDamageResponses.rl("explosion_only"), MissileDamageResponses.EXPLOSION_ONLY);
        MissileDamageResponses.register(MissileDamageResponses.rl("invulnerable"), MissileDamageResponses.INVULNERABLE);
    }
}
