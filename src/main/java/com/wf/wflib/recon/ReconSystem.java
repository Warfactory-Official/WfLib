package com.wf.wflib.recon;

import com.wf.wflib.recon.api.ReconNet;
import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import com.wf.wflib.recon.api.HubIndex;
import com.wf.wflib.recon.api.Decoys;
import com.wf.wflib.recon.api.Interference;
import com.wf.wflib.recon.api.ReconMapService;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.recon.api.CountermeasureProviders;
import com.wf.wflib.recon.api.EntityTargetSource;
import com.wf.wflib.recon.api.ReconBound;
import com.wf.wflib.recon.api.ReconOwners;
import com.wf.wflib.recon.api.ReconTargets;
import com.wf.wflib.recon.api.SeismicEvents;
import com.wf.wflib.recon.api.SignatureProviders;
import com.wf.wflib.recon.api.TelemetryLogPacket;
import com.wf.wflib.recon.client.ReconClientSetup;
import com.wf.wflib.recon.debug.ReconCommands;
import com.wf.wflib.recon.example.ExampleCountermeasureProvider;
import com.wf.wflib.recon.example.ExampleSignatureProvider;
import com.wf.wflib.recon.example.decoy.DecoyTargetSource;
import com.wf.wflib.recon.map.ReconMapCommand;
import com.wf.wflib.recon.source.SensorTargetSource;
import com.wf.wflib.recon.source.SimMissileSource;
import com.wf.wflib.recon.telemetry.TelemetryLogRequestPacket;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import com.wf.wflib.core.api.WFLib;

/**
 * Sensors, radar, telemetry. Holders dependents name: {@link com.wf.wflib.recon.api.ReconContent}.
 * {@link ProbeKind} (the five sensor blocks) != system {@code probe} (look-at panel).
 * Sources of other systems register themselves (drone) or sit here until their owner is a system (TODO(A9): glyphid).
 */
public final class ReconSystem implements WFSystem {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, WFLib.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);

    public static final DeferredHolder<Block, Block> ORBITAL_JAMMER =
            BLOCKS.register("orbital_jammer", () -> new OrbitalJammerBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0f)));
    public static final DeferredHolder<Item, Item> ORBITAL_JAMMER_ITEM =
            ITEMS.register("orbital_jammer", () -> new BlockItem(ORBITAL_JAMMER.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> RADAR_SURVEILLANCE =
            BLOCKS.register("radar_surveillance", () -> new RadarSurveillanceBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f)));
    public static final DeferredHolder<Item, Item> RADAR_SURVEILLANCE_ITEM =
            ITEMS.register("radar_surveillance", () -> new BlockItem(RADAR_SURVEILLANCE.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> RADAR_SCOPE =
            BLOCKS.register("radar_scope", () -> new RadarScopeBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f)));
    public static final DeferredHolder<Item, Item> RADAR_SCOPE_ITEM =
            ITEMS.register("radar_scope", () -> new BlockItem(RADAR_SCOPE.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> GRID_POWER_CELL =
            BLOCKS.register("grid_power_cell", () -> new GridPowerCellBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0f)));
    public static final DeferredHolder<Item, Item> GRID_POWER_CELL_ITEM =
            ITEMS.register("grid_power_cell", () -> new BlockItem(GRID_POWER_CELL.get(), new Item.Properties()));

    /** One holder per {@link ProbeKind}, because a block is a registry object and an enum constant is not. */
    public static final Map<ProbeKind, DeferredHolder<Block, Block>> PROBES = new EnumMap<>(ProbeKind.class);
    public static final Map<ProbeKind, DeferredHolder<Item, Item>> PROBE_ITEMS = new EnumMap<>(ProbeKind.class);

    static {
        for (ProbeKind kind : ProbeKind.values()) {
            DeferredHolder<Block, Block> block = BLOCKS.register(kind.blockName(), () -> {
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .mapColor(kind.underwater() ? MapColor.WATER : MapColor.METAL).strength(2.5f);
                return kind.underwater() ? new SonarProbeBlock(props, kind) : new ReconProbeBlock(props, kind);
            });
            PROBES.put(kind, block);
            PROBE_ITEMS.put(kind, ITEMS.register(kind.blockName(),
                    () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrbitalJammerBlockEntity>> ORBITAL_JAMMER_BE =
            BLOCK_ENTITIES.register("orbital_jammer", () -> BlockEntityType.Builder.of(
                    OrbitalJammerBlockEntity::new, ORBITAL_JAMMER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarSurveillanceBlockEntity>> RADAR_SURVEILLANCE_BE =
            BLOCK_ENTITIES.register("radar_surveillance", () -> BlockEntityType.Builder.of(
                    RadarSurveillanceBlockEntity::new, RADAR_SURVEILLANCE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarScopeBlockEntity>> RADAR_SCOPE_BE =
            BLOCK_ENTITIES.register("radar_scope", () -> BlockEntityType.Builder.of(
                    RadarScopeBlockEntity::new, RADAR_SCOPE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.wf.wflib.recon.api.ReconHubBlockEntity>> RECON_HUB_BE =
            BLOCK_ENTITIES.register("recon_hub", () -> BlockEntityType.Builder.of(
                    com.wf.wflib.recon.api.ReconHubBlockEntity::new, com.wf.wflib.recon.api.ReconContent.RECON_HUB.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReconProbeBlockEntity>> RECON_PROBE_BE =
            BLOCK_ENTITIES.register("recon_probe", () -> BlockEntityType.Builder.of(
                    ReconProbeBlockEntity::new, PROBES.values().stream().map(DeferredHolder::get)
                            .toArray(Block[]::new)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GridPowerCellBlockEntity>> GRID_POWER_CELL_BE =
            BLOCK_ENTITIES.register("grid_power_cell", () -> BlockEntityType.Builder.of(
                    GridPowerCellBlockEntity::new, GRID_POWER_CELL.get()).build(null));

    public static final DeferredHolder<Item, Item> GRID_KEY =
            ITEMS.register("grid_key", () -> new GridKeyItem(new Item.Properties().stacksTo(1)));
    /** The network a {@link GridKeyItem} is holding. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> GRID_ID =
            COMPONENTS.register("grid_id", () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    @Override
    public String id() {
        return "recon";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("recon", 600, out -> {
            out.accept(RADAR_SURVEILLANCE_ITEM.get());
            out.accept(RADAR_SCOPE_ITEM.get());
            out.accept(com.wf.wflib.recon.api.ReconContent.PORTABLE_JAMMER.get());
            out.accept(com.wf.wflib.recon.api.ReconContent.RECON_HUB_ITEM.get());
            PROBE_ITEMS.values().forEach(item -> out.accept(item.get()));
            out.accept(GRID_POWER_CELL_ITEM.get());
            out.accept(ORBITAL_JAMMER_ITEM.get());
            out.accept(GRID_KEY.get());
        });
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "recon"), TickOrder.RECON, ReconNet::tick);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "recon_map"), TickOrder.RECON_MAP, ReconMapService::tick);
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "recon"), TickOrder.RECON_STOP, server -> ReconNet.shutdown());
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "recon_map"), TickOrder.RECON_MAP_STOP, server -> ReconMapService.shutdown());
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "recon_state"), TickOrder.RECON_STATE_STOP, server -> {
            HubIndex.shutdown();
            Decoys.shutdown();
            Interference.shutdown();
        });
        BLOCKS.register("recon_hub", () -> new ReconHubBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(4.0f)));
        ITEMS.register("recon_hub", () -> new BlockItem(
                com.wf.wflib.recon.api.ReconContent.RECON_HUB.get(), new Item.Properties()));
        ITEMS.register("portable_jammer", () -> new com.wf.wflib.recon.api.PortableJammerItem(
                new Item.Properties().stacksTo(1).durability(
                        com.wf.wflib.recon.api.PortableJammerItem.CHARGE_SECONDS)));
        COMPONENTS.register("active", () -> DataComponentType.<Boolean>builder()
                .persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL)
                .build());
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ReconSystem::registerPayloads);
        modBus.addListener(RegisterCapabilitiesEvent.class, event -> event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK, RECON_PROBE_BE.get(), (probe, side) -> probe.energy()));

        registerSources();

        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> {
            ReconCommands.onRegisterCommands(event);
            ReconMapCommand.register(event.getDispatcher());
        });
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
            if (event.getItemStack().getItem() instanceof GridKeyItem
                    && event.getLevel().getBlockEntity(event.getPos()) instanceof ReconBound) {
                event.setUseBlock(TriState.FALSE);
            }
        });
        NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                net.minecraft.world.level.Explosion blast = event.getExplosion();
                Vec3 at = blast.center();
                SeismicEvents.report(level, at.x, at.y, at.z, blast.radius());
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class,
                event -> ReconMapService.forget(event.getEntity().getUUID()));
    }

    private static void registerSources() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "entity");
        ReconTargets.SOURCES.register(id, 0, new EntityTargetSource());
        ReconTargets.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "sim_missile"), 0,
                new SimMissileSource());
        ReconTargets.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "sensor"), 0,
                new SensorTargetSource());
        ReconTargets.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "interference"), 0,
                new Interference());
        ReconTargets.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "decoys"), 0,
                new DecoyTargetSource());
        SignatureProviders.PROVIDERS.register(id, 0, new EntityTargetSource.BuiltIn());
        ReconOwners.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "warforge"), 0,
                new ReconOwners.WarforgeOwners());
        if (!FMLEnvironment.production) {
            SignatureProviders.PROVIDERS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
                    "example_signature"), 100, new ExampleSignatureProvider());
            CountermeasureProviders.PROVIDERS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
                    "example_countermeasure"), 100, new ExampleCountermeasureProvider());
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("recon-1");
        r.playToClient(ScopeFramePacket.TYPE, ScopeFramePacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> ReconClientSetup.scopeFrame(pkt)));
        r.playToClient(ReconMapPacket.TYPE, ReconMapPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> ReconClientSetup.reconMap(pkt)));
        r.playToClient(TelemetryPacket.TYPE, TelemetryPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> ReconClientSetup.telemetry(pkt)));
        r.playToClient(TelemetryLogPacket.TYPE, TelemetryLogPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> ReconClientSetup.telemetryLog(pkt)));
        r.playToServer(TelemetryLogRequestPacket.TYPE, TelemetryLogRequestPacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(PdaRadarPacket.TYPE, PdaRadarPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> ReconClientSetup.radar(pkt)));
        r.playToServer(PdaRadarSubscribePacket.TYPE, PdaRadarSubscribePacket.STREAM_CODEC,
                (pkt, ctx) -> pkt.handle(ctx));
    }

    @Override
    public void registerClient(net.neoforged.bus.api.IEventBus modBus) {
        ReconClientSetup.register(modBus);
    }
}
