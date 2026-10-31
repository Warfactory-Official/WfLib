package com.wf.wflib.drone;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.drone.api.DroneAiScheduler;
import com.wf.wflib.drone.exchange.ExchangeManager;
import com.wf.wflib.cam.api.CameraSources;
import com.wf.wflib.cam.api.FeedHost;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.drone.ai.DroneStateHandlers;
import com.wf.wflib.drone.api.CoordinationModels;
import com.wf.wflib.drone.api.CrateEntity;
import com.wf.wflib.drone.api.DroneDispatchers;
import com.wf.wflib.drone.api.DroneEntity;
import com.wf.wflib.drone.api.DroneModels;
import com.wf.wflib.drone.api.DronePadBlockEntity;
import com.wf.wflib.drone.api.DroneNetBlock;
import com.wf.wflib.drone.api.DroneRecall;
import com.wf.wflib.drone.api.DroneTracker;
import com.wf.wflib.drone.api.Formations;
import com.wf.wflib.drone.api.PdaDrones;
import com.wf.wflib.drone.api.RemoteBody;
import com.wf.wflib.drone.api.SimDroneManager;
import com.wf.wflib.drone.client.DroneClientSetup;
import com.wf.wflib.drone.client.DronesApp;
import com.wf.wflib.drone.debug.DroneCommands;
import com.wf.wflib.drone.debug.ExchangeCommands;
import com.wf.wflib.drone.ai.coord.Consensus;
import com.wf.wflib.drone.ai.coord.Flocking;
import com.wf.wflib.drone.ai.coord.LeaderFollower;
import com.wf.wflib.drone.ai.coord.VirtualStructure;
import com.wf.wflib.recon.api.ReconTargets;
import com.wf.wflib.recon.api.Signature;
import com.wf.wflib.recon.api.SignatureProvider;
import com.wf.wflib.recon.api.SignatureProviders;
import com.wf.wflib.recon.api.ReconSignatures;
import com.wf.wflib.sim.api.SimKinds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;
import com.wf.wflib.core.api.WFLib;

/** Holders dependents name: {@link com.wf.wflib.drone.api.DroneContent}. */
public final class DroneSystem implements WFSystem {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, WFLib.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, WFLib.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DronePadBlockEntity>> DRONE_PAD_BE =
            BLOCK_ENTITIES.register("drone_pad", () -> BlockEntityType.Builder.of(
                    DronePadBlockEntity::new, com.wf.wflib.drone.api.DroneContent.DRONE_PAD.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<DronePadMenu>> DRONE_PAD_MENU =
            MENUS.register("drone_pad", () -> IMenuTypeExtension.create(DronePadMenu::new));

    public static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE =
            ENTITY_TYPES.register("drone", () -> EntityType.Builder.<DroneEntity>of(DroneEntity::new, MobCategory.MISC)
                    .sized(3.0F, 1.2F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<DroneDebrisEntity>> DRONE_DEBRIS =
            ENTITY_TYPES.register("drone_debris", () -> EntityType.Builder.<DroneDebrisEntity>of(
                            DroneDebrisEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.3F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("drone_debris"));

    public static final DeferredHolder<EntityType<?>, EntityType<CrateEntity>> CRATE =
            ENTITY_TYPES.register("crate", () -> EntityType.Builder.<CrateEntity>of(CrateEntity::new, MobCategory.MISC)
                    .sized(CrateEntity.SIZE, CrateEntity.SIZE)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("crate"));

    /** A quadcopter holding an unladen hover, looped. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_ROTOR_LOOP = SOUNDS.register("drone.rotor_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "drone.rotor_loop")));

    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "drone";
    }

    @Override
    public java.util.Set<String> requires() {
        return java.util.Set.of("work");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("drone", 800, out -> {
            out.accept(com.wf.wflib.drone.api.DroneContent.DRONE_PAD_ITEM.get());
            out.accept(com.wf.wflib.drone.api.DroneContent.DRONE_NET_ITEM.get());
        });
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "drone"), TickOrder.DRONE, level -> {
            DroneLaunchQueue.tick(level);
            DroneAiScheduler.tick(level);
        });
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "exchange"), TickOrder.EXCHANGE, ExchangeManager::tick);
        active = true;
        BLOCKS.register("drone_net", () -> new DroneNetBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOL).strength(0.8f).sound(SoundType.WOOL).noOcclusion()));
        ITEMS.register("drone_net", () -> new BlockItem(
                com.wf.wflib.drone.api.DroneContent.DRONE_NET.get(), new Item.Properties()));
        BLOCKS.register("drone_pad", () -> new DronePadBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f)));
        ITEMS.register("drone_pad", () -> new BlockItem(
                com.wf.wflib.drone.api.DroneContent.DRONE_PAD.get(), new Item.Properties()));
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, DroneSystem::registerPayloads);

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "drone");
        FeedHost.HOSTS.register(id, 0, new DroneFeedHost());
        CameraSources.PROVIDERS.register(id, 0, entity -> {
            if (entity instanceof DroneEntity drone && drone.isAlive() && drone.cameraSpec() != null) {
                return new DroneCameraSource(drone, drone.cameraSpec());
            }
            return null;
        });
        RemoteBody.PROVIDERS.register(id, 0, PdaDrones::bodyOf);
        com.wf.wflib.pda.api.TerminalNoun.NOUNS.register(id, 300, DronePdaLinks.TERMINAL);
        com.wf.wflib.radio.api.RadioTuning.LINKS.register(id, 0, DronePdaLinks.RADIO);
        DroneAiScheduler.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "tracker"), 100,
                DroneTracker::collect);
        DroneAiScheduler.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "sim"), 0,
                SimDroneManager::collect);
        SimKinds.register(SimDroneManager.KIND);
        com.wf.wflib.missile.api.SimGhosts.SOURCES.register(id, (level, sink) -> {
            for (var sd : SimDroneManager.tier(level).view()) {
                sink.accept(sd.id, sd.pos, sd.modelId, 0);
            }
        });

        defineFormations();
        DroneModels.define();
        DroneStateHandlers.define();

        ReconTargets.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "drone"), 0,
                new DroneTargetSource());
        ReconTargets.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "sim_drone"), 0,
                new SimDroneSource());
        SignatureProviders.PROVIDERS.register(id, 0, new SignatureProvider() {
            @Nullable
            @Override
            public Signature of(Entity entity) {
                return entity instanceof DroneEntity ? ReconSignatures.drone() : null;
            }
        });

        NeoForge.EVENT_BUS.register(DroneTracker.class);
        NeoForge.EVENT_BUS.register(DroneDispatchers.class);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, PdaDrones::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, DroneCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, ExchangeCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, DroneCommands::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> DroneAiScheduler.startup());
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> {
            DroneRecall.shutdown();
            DroneAiScheduler.shutdown();
        });
    }

    private static void defineFormations() {
        for (var formation : new com.wf.wflib.drone.api.Formation[] {Formations.VEE, Formations.COLUMN,
                Formations.LINE, Formations.GRID}) {
            Formations.REGISTRY.register(Formations.rl(formation.id()), formation);
        }
        for (var model : new com.wf.wflib.drone.api.CoordinationModel[] {VirtualStructure.INSTANCE,
                LeaderFollower.INSTANCE, Consensus.INSTANCE, Flocking.INSTANCE}) {
            CoordinationModels.REGISTRY.register(CoordinationModels.rl(model.id()), model);
        }
    }

    @Override
    public void registerClient(IEventBus modBus) {
        DroneClientSetup.register(modBus);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("drone-1");
        r.playToServer(DronePadConfigPacket.TYPE, DronePadConfigPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToServer(DroneStatusRequestPacket.TYPE, DroneStatusRequestPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(DroneStatusPacket.TYPE, DroneStatusPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> DronesApp.accept(pkt.status())));
        r.playToServer(DroneActionPacket.TYPE, DroneActionPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToServer(DroneDispatchPacket.TYPE, DroneDispatchPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(DroneDispatchReplyPacket.TYPE, DroneDispatchReplyPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> DronesApp.reply(pkt.line())));
    }
}
