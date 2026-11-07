package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.soldier.api.Soldiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Set;

/**
 * Soldiers: server players with no client, driven by a squad AI (vision with reaction time, hearing, cover, peeking,
 * bounding overwatch, suppression, grenades, first aid). The guns, grenades and medicine come from the mods that
 * register a {@code SoldierWeapon}, {@code SoldierThrowables} and {@code SoldierMedical}.
 */
public final class SoldierSystem implements WFSystem {
    @Override
    public String id() {
        return "soldier";
    }

    @Override
    public Set<String> requires() {
        return Set.of("grenade");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener(SoldierAi::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SoldierAi::onGrenadeSound);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> Soldiers.removeAll(e.getServer()));
    }
}
