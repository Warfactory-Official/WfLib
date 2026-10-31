package com.wf.wflib.missile;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.missile.api.AscentStage;
import com.wf.wflib.missile.api.FlightStages;
import com.wf.wflib.missile.api.MissileConfig;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.missile.api.MissileEntity.Phase;
import com.wf.wflib.missile.api.MissileItem;
import com.wf.wflib.missile.api.MissileListenerData;
import com.wf.wflib.missile.api.MissileModels;
import com.wf.wflib.missile.api.MissilePreset;
import com.wf.wflib.missile.api.MissilePresets;
import com.wf.wflib.missile.attitude.CopterAttitude;
import com.wf.wflib.missile.attitude.LevelDroneAttitude;
import com.wf.wflib.missile.api.MissileAttitudes;
import com.wf.wflib.missile.attitude.NoseToVelocityAttitude;
import com.wf.wflib.missile.api.DetonationChunkGuard;
import com.wf.wflib.missile.client.MissileClient;
import com.wf.wflib.missile.client.MissileClientConfig;
import com.wf.wflib.missile.debug.MissileCommands;
import com.wf.wflib.missile.flight.AirLaunchStage;
import com.wf.wflib.missile.flight.ApproachStage;
import com.wf.wflib.missile.flight.AttackStage;
import com.wf.wflib.missile.flight.CruiseStage;
import com.wf.wflib.missile.flight.DirectStage;
import com.wf.wflib.missile.flight.InterceptStage;
import com.wf.wflib.missile.flight.LoiterStage;
import com.wf.wflib.missile.flight.SubmergedRunStage;
import com.wf.wflib.missile.flight.TorpedoEntryStage;
import com.wf.wflib.missile.flight.TorpedoTerminalStage;
import com.wf.wflib.missile.flight.VerticalDiveStage;
import com.wf.wflib.missile.tv.TvConnectPacket;
import com.wf.wflib.missile.api.TvGuidance;
import com.wf.wflib.missile.tv.TvLinkPacket;
import com.wf.wflib.sim.api.SimKinds;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.missile.api.SimMissileManager;

public final class MissileSystem implements WFSystem {
    // Shahed pusher prop spin, deg/tick; visual only.
    private static final float SHAHED_ROTOR_SPEED = 45.0f;

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, WFLib.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, WFLib.MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);

    /** Named through {@link com.wf.wflib.missile.api.MissileContent#STEALTH_MISSILE}. */
    private static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE =
            ENTITY_TYPES.register("missile", () -> EntityType.Builder.of(MissileEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .build("missile"));

    /** Named through {@link com.wf.wflib.missile.api.MissileContent#MISSILE_FLIGHT}. */
    private static final DeferredHolder<SoundEvent, SoundEvent> FLIGHT_SOUND =
            SOUND_EVENTS.register("weapon.missile_flight", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "weapon.missile_flight")));

    public static final DeferredHolder<Block, Block> MISSILE_LISTENER_DEBUG =
            BLOCKS.register("missile_listener_debug", () -> new MissileListenerDebugBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f)));

    public static final DeferredHolder<Item, Item> MISSILE_LISTENER_DEBUG_ITEM =
            ITEMS.register("missile_listener_debug", () ->
                    new BlockItem(MISSILE_LISTENER_DEBUG.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> MISSILE_DISPENSER =
            BLOCKS.register("missile_dispenser", () -> new MissileDispenserBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f)));

    public static final DeferredHolder<Item, Item> MISSILE_DISPENSER_ITEM =
            ITEMS.register("missile_dispenser", () ->
                    new BlockItem(MISSILE_DISPENSER.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> TURRET_INTERCEPTOR =
            BLOCKS.register("turret_interceptor", () -> new TurretInterceptorBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f).requiresCorrectToolForDrops(),
                    false));

    public static final DeferredHolder<Item, Item> TURRET_INTERCEPTOR_ITEM =
            ITEMS.register("turret_interceptor", () ->
                    new BlockItem(TURRET_INTERCEPTOR.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> TURRET_INTERCEPTOR_SUPERSONIC =
            BLOCKS.register("turret_interceptor_supersonic", () -> new TurretInterceptorBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f).requiresCorrectToolForDrops(),
                    true));

    public static final DeferredHolder<Item, Item> TURRET_INTERCEPTOR_SUPERSONIC_ITEM =
            ITEMS.register("turret_interceptor_supersonic", () ->
                    new BlockItem(TURRET_INTERCEPTOR_SUPERSONIC.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileListenerDebugBlockEntity>> MISSILE_LISTENER_DEBUG_BE =
            BLOCK_ENTITIES.register("missile_listener_debug", () -> BlockEntityType.Builder.of(
                    MissileListenerDebugBlockEntity::new, MISSILE_LISTENER_DEBUG.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileDispenserBlockEntity>> MISSILE_DISPENSER_BE =
            BLOCK_ENTITIES.register("missile_dispenser", () -> BlockEntityType.Builder.of(
                    MissileDispenserBlockEntity::new, MISSILE_DISPENSER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurretInterceptorNormalBlockEntity>> TURRET_INTERCEPTOR_BE =
            BLOCK_ENTITIES.register("turret_interceptor", () -> BlockEntityType.Builder.of(
                    TurretInterceptorNormalBlockEntity::new, TURRET_INTERCEPTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurretInterceptorSupersonicBlockEntity>> TURRET_INTERCEPTOR_SUPERSONIC_BE =
            BLOCK_ENTITIES.register("turret_interceptor_supersonic", () -> BlockEntityType.Builder.of(
                    TurretInterceptorSupersonicBlockEntity::new, TURRET_INTERCEPTOR_SUPERSONIC.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<MissileDispenserMenu>> MISSILE_DISPENSER_MENU =
            MENUS.register("missile_dispenser", () -> IMenuTypeExtension.create(MissileDispenserMenu::new));

    @Override
    public String id() {
        return "missile";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("missile", 900, out -> {
            out.accept(MISSILE_DISPENSER_ITEM.get());
            out.accept(MISSILE_LISTENER_DEBUG_ITEM.get());
            out.accept(TURRET_INTERCEPTOR_ITEM.get());
            out.accept(TURRET_INTERCEPTOR_SUPERSONIC_ITEM.get());
            com.wf.wflib.missile.api.MissileContent.missileItems().stream()
                    .map(net.neoforged.neoforge.registries.DeferredHolder::get)
                    .filter(i -> i.preset().id().getNamespace().equals(com.wf.wflib.core.api.WFLib.MODID))
                    .forEach(out::accept);
        });
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "missile_ghosts"), TickOrder.MISSILE_GHOSTS, SimGhostTracking::tick);
        com.wf.wflib.missile.api.SimGhosts.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "missile"),
                SimGhostTracking::missiles);
        active = true;
        ENTITY_TYPES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        SOUND_EVENTS.register(modBus);

        modBus.addListener(MissileConfig::onLoad);
        container.registerConfig(ModConfig.Type.COMMON, MissileConfig.SPEC, "wflib-missile.toml");
        modBus.addListener(MissileClientConfig::onLoad);
        container.registerConfig(ModConfig.Type.CLIENT, MissileClientConfig.SPEC, "wflib-missile-client.toml");

        FlightStages.register(Phase.ASCEND, AscentStage.INSTANCE);
        FlightStages.register(Phase.ASCEND, AirLaunchStage.INSTANCE);
        FlightStages.register(Phase.CRUISE, CruiseStage.INSTANCE);
        FlightStages.register(Phase.CRUISE, ApproachStage.INSTANCE);
        FlightStages.register(Phase.CRUISE, LoiterStage.of("loiter", 24.0, 200));
        FlightStages.register(Phase.CRUISE, LoiterStage.of("loiter_close", 12.0, 200));
        FlightStages.register(Phase.CRUISE, LoiterStage.of("loiter_wide", 48.0, 300));
        FlightStages.register(Phase.CRUISE, LoiterStage.of("loiter_recon", 64.0, 600));
        FlightStages.register(Phase.ATTACK, AttackStage.INSTANCE);
        FlightStages.register(Phase.ATTACK, VerticalDiveStage.INSTANCE);
        FlightStages.register(Phase.ATTACK, DirectStage.INSTANCE);
        FlightStages.register(Phase.ASCEND, TorpedoEntryStage.INSTANCE);
        FlightStages.register(Phase.CRUISE, SubmergedRunStage.INSTANCE);
        FlightStages.register(Phase.ATTACK, TorpedoTerminalStage.INSTANCE);
        FlightStages.register(Phase.ASCEND, InterceptStage.INSTANCE);
        FlightStages.register(Phase.CRUISE, InterceptStage.INSTANCE);
        FlightStages.register(Phase.ATTACK, InterceptStage.INSTANCE);

        MissileAttitudes.register(MissileAttitudes.rl(MissileAttitudes.DEFAULT_ID), NoseToVelocityAttitude.INSTANCE);
        MissileAttitudes.register(MissileAttitudes.rl("drone"), LevelDroneAttitude.INSTANCE);
        MissileAttitudes.register(MissileAttitudes.rl("copter"), CopterAttitude.INSTANCE);

        defineModels();
        MissileCatalogue.define();
        SimKinds.register(SimMissileManager.KIND);
        // Interceptor turrets fly unregistered catalogue presets => warhead live.
        WFLibContent.warheads(WFLib.MODID, "interceptor");

        modBus.addListener(EventPriority.NORMAL, RegisterEvent.class, MissileSystem::registerItems);
        modBus.addListener(RegisterTicketControllersEvent.class, event -> {
            event.register(DetonationChunkGuard.CONTROLLER);
            event.register(MissileListenerData.CHUNK_TICKET);
        });
        modBus.addListener(RegisterPayloadHandlersEvent.class, MissileSystem::registerPayloads);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, MissileCommands::onRegisterCommands);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        com.wf.wflib.ui.api.input.MouseSteer.STEERS.register(
                ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "tv"), 0, com.wf.wflib.missile.client.TvClient::steer);
        MissileClient.register(modBus);
    }

    private static void defineModels() {
        // Base airframes (each has its own hand-authored model json = OBJ + default skin).
        MissileModels.register("abm", "missile_abm");
        MissileModels.register("atlas", "missile_atlas");
        MissileModels.register("booster", "missilebooster");
        MissileModels.register("carrier", "missilecarrier");
        MissileModels.register("cluster", "missile_cluster");
        MissileModels.register("cluster_part", "missile_cluster_part");
        MissileModels.register("huge", "missile_huge");
        MissileModels.register("micro", "missile_micro");
        MissileModels.register("neon", "missileneon");
        MissileModels.register("shuttle", "missile_shuttle");
        MissileModels.register("stealth", "missile_stealth");
        MissileModels.register("strong", "missile_strong");
        MissileModels.register("taint", "missiletaint");
        MissileModels.register("thermo", "missilethermo");
        MissileModels.register("v2", "missile_v2");

        MissileModels.register("v2_bunker", "missile_v2_bu");
        MissileModels.register("v2_cluster", "missile_v2_cl");
        MissileModels.register("v2_decoy", "missile_v2_decoy");
        MissileModels.register("v2_incendiary", "missile_v2_inc");

        MissileModels.register("strong_bunker", "missile_strong_bu");
        MissileModels.register("strong_cluster", "missile_strong_cl");
        MissileModels.register("strong_emp", "missile_strong_emp");
        MissileModels.register("strong_incendiary", "missile_strong_inc");

        MissileModels.register("micro_bhole", "missile_micro_bhole");
        MissileModels.register("micro_emp", "missile_micro_emp");
        MissileModels.register("micro_schrab", "missile_micro_schrab");
        MissileModels.register("micro_taint", "missile_micro_taint");

        MissileModels.register("huge_bunker", "missile_huge_bu");
        MissileModels.register("huge_cluster", "missile_huge_cl");
        MissileModels.register("huge_incendiary", "missile_huge_inc");

        // Air-launched, pylon-sized: tools/small_airframes.py.
        MissileModels.register("atgm", "missile_atgm");
        MissileModels.register("guided_rocket", "missile_guided_rocket");

        MissileModels.register("atlas_doomsday", "missile_atlas_doomsday");
        MissileModels.register("atlas_doomsday_weathered", "missile_atlas_doomsday_weathered");
        MissileModels.register("atlas_tectonic", "missile_atlas_tectonic");
        MissileModels.register("atlas_thermo", "missile_atlas_thermo");

        MissileModels.register("shahed", "shahed_body");
        MissileModels.rotor("shahed", "shahed_prop", Axis.Y, SHAHED_ROTOR_SPEED);
        MissileModels.attitude("shahed", "drone");
        MissileModels.register("shahedjarty", "shahedjarty_body");
        MissileModels.rotor("shahedjarty", "shahedjarty_prop", Axis.Y, SHAHED_ROTOR_SPEED);
        MissileModels.attitude("shahedjarty", "drone");
    }

    private static void registerItems(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        event.register(Registries.ITEM, helper -> {
            for (MissilePreset preset : MissilePresets.withItems()) {
                helper.register(WFLibContent.itemId(preset.id(), preset.itemPath()),
                        new MissileItem(preset, new Item.Properties().stacksTo(16)));
            }
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("missile-2");
        r.playToClient(MissileFlightAudioPacket.TYPE, MissileFlightAudioPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> MissileClient.handleMissileAudio(pkt)));
        r.playToClient(com.wf.wflib.missile.api.SimGhostSyncPacket.TYPE,
                com.wf.wflib.missile.api.SimGhostSyncPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> MissileClient.handleSimGhosts(pkt)));
        r.playToServer(SpawnMissilePacket.TYPE, SpawnMissilePacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToServer(TvConnectPacket.TYPE, TvConnectPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        PacketDistributor.sendToPlayer(player, new TvLinkPacket(TvGuidance.connect(player), false));
                    }
                }));
        r.playToClient(TvLinkPacket.TYPE, TvLinkPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> MissileClient.handleTvLink(pkt)));
    }
}
