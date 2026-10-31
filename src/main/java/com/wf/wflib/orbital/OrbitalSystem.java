package com.wf.wflib.orbital;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.orbital.api.OrbitalNet;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.wf.wflib.orbital.debug.OrbitalCommands;
import com.wf.wflib.cam.api.VirtualFeeds;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.orbital.api.CargoTable;
import com.wf.wflib.orbital.api.OrbitalContent;
import com.wf.wflib.orbital.api.SatPayloads;
import com.wf.wflib.orbital.api.SpotFeeds;
import com.wf.wflib.orbital.client.OrbitalClient;
import com.wf.wflib.orbital.payload.HunterPayload;
import com.wf.wflib.orbital.payload.ImageryPayload;
import com.wf.wflib.orbital.payload.InertPayload;
import com.wf.wflib.orbital.payload.LaserPayload;
import com.wf.wflib.orbital.payload.MinerPayload;
import com.wf.wflib.orbital.payload.ReconPayload;
import com.wf.wflib.orbital.payload.RelayPayload;
import com.wf.wflib.orbital.payload.RodsPayload;
import com.wf.wflib.orbital.api.SurveyTilePacket;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** Satellites: orbits, payloads, ground stations, pads and shuttles, the PDA console and the survey screen. */
public final class OrbitalSystem implements WFSystem {
    @Override
    public String id() {
        return "orbital";
    }

    @Override
    public Set<String> requires() {
        return Set.of();
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("orbital", 350, out -> {
            out.accept(OrbitalContent.LANDING_PAD_ITEM.get());
            out.accept(OrbitalContent.CARGO_SHUTTLE_ITEM.get());
        });
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "orbital"), TickOrder.ORBITAL, OrbitalNet::tick);
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "orbital"), TickOrder.ORBITAL_STOP, server -> OrbitalNet.shutdown());
        container.registerConfig(ModConfig.Type.COMMON, OrbitalConfig.SPEC, "wflib-orbital.toml");
        modBus.addListener(OrbitalConfig::onLoad);

        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, WFLib.MODID);
        blocks.register("cargo_shuttle", () -> new CargoShuttleBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f)));
        blocks.register("landing_pad", () -> new LandingPadBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f)));
        blocks.register(modBus);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
        items.register("cargo_shuttle",
                () -> new BlockItem(OrbitalContent.CARGO_SHUTTLE.get(), new Item.Properties()));
        items.register("landing_pad",
                () -> new BlockItem(OrbitalContent.LANDING_PAD.get(), new Item.Properties()));
        items.register(modBus);
        DeferredRegister<BlockEntityType<?>> blockEntities =
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WFLib.MODID);
        blockEntities.register("cargo_shuttle", () -> BlockEntityType.Builder.of(CargoShuttleBlockEntity::new,
                OrbitalContent.CARGO_SHUTTLE.get()).build(null));
        blockEntities.register("landing_pad", () -> BlockEntityType.Builder.of(LandingPadBlockEntity::new,
                OrbitalContent.LANDING_PAD.get()).build(null));
        blockEntities.register(modBus);
        modBus.addListener(RegisterCapabilitiesEvent.class, event -> event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, OrbitalContent.CARGO_SHUTTLE_BE.get(),
                (shuttle, side) -> shuttle.items()));

        SatPayloads.register(SatPayloads.INERT, InertPayload::new);
        SatPayloads.register(SatPayloads.RELAY, RelayPayload::new);
        SatPayloads.register(SatPayloads.RECON_RADAR, ReconPayload::new);
        SatPayloads.register(SatPayloads.MINER, MinerPayload::new);
        SatPayloads.register(SatPayloads.RODS, RodsPayload::new);
        SatPayloads.register(SatPayloads.LASER, LaserPayload::new);
        SatPayloads.register(SatPayloads.HUNTER, HunterPayload::new);
        SatPayloads.register(SatPayloads.IMAGERY, ImageryPayload::new);

        // What a rock in space is actually made of, in vanilla terms.
        CargoTable.register(new ItemStack(Items.RAW_IRON), 30);
        CargoTable.register(new ItemStack(Items.COBBLESTONE, 4), 24);
        CargoTable.register(new ItemStack(Items.RAW_COPPER), 18);
        CargoTable.register(new ItemStack(Items.COAL), 12);
        CargoTable.register(new ItemStack(Items.RAW_GOLD), 6);
        CargoTable.register(new ItemStack(Items.REDSTONE, 3), 6);
        CargoTable.register(new ItemStack(Items.LAPIS_LAZULI, 2), 3);
        CargoTable.register(new ItemStack(Items.DIAMOND), 1);

        Warheads.register(OrbitalWarheads.KINETIC_ROD, OrbitalWarheads.detonation(OrbitalWarheads.KINETIC_ROD));
        Warheads.register(OrbitalWarheads.CARGO_LANDING, OrbitalWarheads.detonation(OrbitalWarheads.CARGO_LANDING));
        Warheads.register(OrbitalWarheads.CARGO_LANDING_SOFT,
                OrbitalWarheads.detonation(OrbitalWarheads.CARGO_LANDING_SOFT));
        com.wf.wflib.pda.api.Uplink.RELAYS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "satellites"), 0,
                (level, netId, player) -> {
                    for (var sat : OrbitalNet.overhead(level, netId, player.getX(), player.getZ())) {
                        if (sat.inContact() && sat.payload().equals(SatPayloads.RELAY)) {
                            return true;
                        }
                    }
                    return false;
                });
        com.wf.wflib.pda.api.TerminalNoun.NOUNS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "sats"),
                100, SatTerminal.INSTANCE);
        VirtualFeeds.FEEDS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "orbital_spots"), 0,
                SpotFeeds.FEED);

        modBus.addListener(RegisterPayloadHandlersEvent.class, OrbitalSystem::registerPayloads);
        NeoForge.EVENT_BUS.register(PdaSatellites.class);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, OrbitalCommands::onRegisterCommands);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        OrbitalClient.register(modBus);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("orbital-1");
        r.playToClient(SurveyTilePacket.TYPE, SurveyTilePacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> OrbitalClient.surveyTile(pkt)));
        r.playToClient(SurveyOpenPacket.TYPE, SurveyOpenPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> OrbitalClient.surveyOpen(pkt)));
        r.playToServer(SurveyRequestPacket.TYPE, SurveyRequestPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(SatStatusPacket.TYPE, SatStatusPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> OrbitalClient.satStatus(pkt)));
        r.playToClient(SatReplyPacket.TYPE, SatReplyPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> OrbitalClient.satReply(pkt)));
        r.playToServer(SpotRequestPacket.TYPE, SpotRequestPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(SpotOpenPacket.TYPE, SpotOpenPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> OrbitalClient.spot(pkt)));
        r.playToServer(SatCommandPacket.TYPE, SatCommandPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
    }
}
