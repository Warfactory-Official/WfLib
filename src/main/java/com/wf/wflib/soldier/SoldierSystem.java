package com.wf.wflib.soldier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.pda.api.client.PdaClient;
import com.wf.wflib.soldier.api.SoldierPlayer;
import com.wf.wflib.soldier.api.Soldiers;
import com.wf.wflib.soldier.client.HireApp;
import com.wf.wflib.soldier.client.SoldierEditorScreen;
import com.wf.wflib.soldier.client.SoldierLightClient;
import com.wf.wflib.soldier.client.SquadClient;
import java.io.IOException;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

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
        return Set.of("grenade", "audio", "armor", "pda");
    }

    @Override
    public void registerClient(IEventBus modBus) {
        SoldierLightClient.register(modBus);
        SquadClient.register(modBus);
        // /wfpda open <app id> [tab]: raises the carried PDA straight onto an app (testing, and key-less setups)
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                Commands.literal("wfpda").then(Commands.literal("open")
                        .then(Commands.argument("app", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String[] words = StringArgumentType.getString(c, "app").split(" ");
                                    HireApp.tab(words.length > 1 && words[1].equals("forces"));
                                    return PdaClient.openCarried(ResourceLocation.parse(words[0])) ? 1 : 0;
                                })))));
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(HireApp::register));
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(com.wf.wflib.soldier.client.GearScreen::register));
        SoldierEditorScreen.registerNameTags();
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        Headlamp.register(modBus);
        SoldierEditorEgg.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class,
                e -> SoldierEditorPackets.register(e.registrar("soldier-editor-1").optional()));
        container.registerConfig(ModConfig.Type.COMMON, VoiceDelivery.SPEC, "wflib-voices.toml");
        try { SoldierVoices.reload(); }
        catch (IOException e) { LogUtils.getLogger().error("NPC voice packs failed to load", e); }
        modBus.addListener(VoiceResources::add);
        modBus.addListener(RegisterPayloadHandlersEvent.class,
                e -> VoicePackets.register(e.registrar("soldier-voices-1").optional()));
        modBus.addListener(RegisterPayloadHandlersEvent.class,
                e -> SoldierLights.register(e.registrar("soldier-lights-1").optional()));
        modBus.addListener(RegisterPayloadHandlersEvent.class,
                e -> SquadPackets.register(e.registrar("soldier-squad-1").optional()));
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, RecruitConfig.SPEC, "wflib-recruiting.toml");
        Recruits.register();
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class,
                e -> RecruitPackets.register(e.registrar("soldier-recruit-1").optional()));
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class,
                e -> GearPackets.register(e.registrar("soldier-gear-1").optional()));
        NeoForge.EVENT_BUS.addListener(Recruits::onServerTick);
        NeoForge.EVENT_BUS.addListener(RecruitPackets::onUseBlock);
        NeoForge.EVENT_BUS.addListener(SquadSync::onServerTick);
        NeoForge.EVENT_BUS.addListener(Posts::onServerTick);
        NeoForge.EVENT_BUS.addListener(Garrisons::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> Posts.start(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class,
                e -> VoiceDelivery.start(e.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class,
                e -> {
                    if (e.getEntity() instanceof ServerPlayer p) {
                        VoiceDelivery.offer(p);
                        if (!(p instanceof SoldierPlayer)) Squads.deployAll(p);   // their soldiers come back with them
                    }
                });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && !(p instanceof SoldierPlayer)) {
                if (p.getServer().isSingleplayerOwner(p.getGameProfile())) Posts.stop(p.getServer());   // the host leaving ends the game: the shutdown takes every soldier out before it announces itself
                Squads.stow(p.getServer(), p.getUUID());
                SquadSync.forget(p.getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, SquadInteraction::onInteract);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && SoldierAi.scripted(p)) {
                var bot = SoldierAi.botOf(p.getGameProfile().getName());
                if (bot != null) {
                    SoldierVoices.say(p, bot, "death");
                    Garrisons.lost(bot, p.level().getGameTime());
                    SiteImpl.fell(p.getServer(), bot.garrison);
                    Kills.post(p, bot.team, e.getSource());
                }
                Squads.fell(p);
            }
        });
        NeoForge.EVENT_BUS.addListener(SoldierAi::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SoldierAi::onGrenadeSound);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> {
            Posts.stop(e.getServer());                       // write the unowned soldiers down before they are taken out
            Squads.stowAll(e.getServer());
            Soldiers.removeAll(e.getServer());
            SoldierJobs.shutdown();
            SoldierEditor.forget(e.getServer());
            SoldierEditorPackets.forget();
            GearPackets.forget();
            Stockpile.forget();
            VoiceDelivery.stop();
        });
        NeoForge.EVENT_BUS.addListener(SoldierCommands::onRegisterCommands);
    }
}
