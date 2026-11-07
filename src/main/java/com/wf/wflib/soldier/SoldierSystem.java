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
        return Set.of("grenade", "audio");
    }

    @Override
    public void registerClient(IEventBus modBus) {
        com.wf.wflib.soldier.client.SoldierLightClient.register(modBus);
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        Headlamp.register(modBus);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, VoiceDelivery.SPEC, "wflib-voices.toml");
        try { SoldierVoices.reload(); }
        catch (java.io.IOException e) { com.mojang.logging.LogUtils.getLogger().error("NPC voice packs failed to load", e); }
        modBus.addListener(VoiceResources::add);
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class,
                e -> VoicePackets.register(e.registrar("soldier-voices-1").optional()));
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class,
                e -> SoldierLights.register(e.registrar("soldier-lights-1").optional()));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class,
                e -> VoiceDelivery.start(e.getServer()));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                e -> { if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) VoiceDelivery.offer(p); });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, e -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p && SoldierAi.scripted(p)) {
                var bot = SoldierAi.botOf(p.getGameProfile().getName());
                if (bot != null) SoldierVoices.say(p, bot, "death");
            }
        });
        NeoForge.EVENT_BUS.addListener(SoldierAi::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SoldierAi::onGrenadeSound);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> {
            Soldiers.removeAll(e.getServer());
            SoldierJobs.shutdown();
            VoiceDelivery.stop();
        });
        NeoForge.EVENT_BUS.addListener(SoldierCommands::onRegisterCommands);
    }
}
