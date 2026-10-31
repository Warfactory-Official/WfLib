package com.wf.wflib.pda;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.pda.alerts.PdaAlertPacket;
import com.wf.wflib.pda.api.client.PdaAlerts;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.pda.api.client.TerminalApp;
import com.wf.wflib.pda.client.AlertsApp;
import com.wf.wflib.pda.client.PdaFieldsDemo;
import com.wf.wflib.pda.client.PdaFullscreen;
import com.wf.wflib.pda.client.PdaItemClient;
import com.wf.wflib.pda.client.SettingsApp;
import com.wf.wflib.pda.client.theme.PdaUiSkin;
import com.wf.wflib.pda.music.BackgroundListPacket;
import com.wf.wflib.pda.music.MusicChunkPacket;
import com.wf.wflib.pda.music.MusicFetchPacket;
import com.wf.wflib.pda.music.MusicListPacket;
import com.wf.wflib.pda.music.MusicListRequestPacket;
import com.wf.wflib.pda.music.client.MusicApp;
import com.wf.wflib.pda.terminal.TerminalCommandPacket;
import com.wf.wflib.pda.terminal.TerminalReplyPacket;
import com.wf.wflib.pda.api.client.PdaClient;
import com.wf.wflib.ui.api.UiSkin;
import com.wf.wflib.ui.api.input.MouseSteer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.core.api.WFLib;

/** Holders named through {@link com.wf.wflib.pda.api.PdaContent}. */
public final class PdaSystem implements WFSystem {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PDA_CLICK = sound("pda.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> PDA_HOVER = sound("pda.hover");

    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name)));
    }

    @Override
    public String id() {
        return "pda";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("pda", 500, out -> out.accept(com.wf.wflib.pda.api.PdaContent.PDA.get()));
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, PdaClientConfig.SPEC, "wflib-pda-client.toml");
        active = true;
        ITEMS.register("pda", () -> new PdaItem(new Item.Properties().stacksTo(1)));
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, PdaSystem::registerPayloads);
        com.wf.wflib.pda.api.TerminalNoun.NOUNS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "nodes"),
                200, com.wf.wflib.pda.terminal.PdaTerminal.NODES);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        UiSkin.HOOK.set(WFLib.MODID, new PdaUiSkin());
        MouseSteer.STEERS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda"), 100, PdaClient::steer);
        modBus.addListener(RegisterClientExtensionsEvent.class,
                e -> e.registerItem(PdaItemClient.INSTANCE, com.wf.wflib.pda.api.PdaContent.PDA.get()));
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(PdaSystem::registerApps));
    }

    private static void registerApps() {
        PdaShell.register();
        PdaFullscreen.register();
        MusicApp.register();
        AlertsApp.register();
        TerminalApp.register();
        PdaFieldsDemo.register();
        SettingsApp.register();
        PdaAlerts.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("pda-1");
        r.playToClient(PdaAlertPacket.TYPE, PdaAlertPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> PdaAlerts.post(pkt.message(), pkt.tier())));
        r.playToServer(TerminalCommandPacket.TYPE, TerminalCommandPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(TerminalReplyPacket.TYPE, TerminalReplyPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> TerminalApp.accept(pkt.lines())));
        r.playToServer(MusicListRequestPacket.TYPE, MusicListRequestPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToServer(MusicFetchPacket.TYPE, MusicFetchPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(MusicListPacket.TYPE, MusicListPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> MusicApp.accept(pkt)));
        r.playToClient(BackgroundListPacket.TYPE, BackgroundListPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> com.wf.wflib.pda.api.client.PdaTheme.accept(pkt)));
        r.playToClient(MusicChunkPacket.TYPE, MusicChunkPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> MusicApp.accept(pkt)));
    }
}
