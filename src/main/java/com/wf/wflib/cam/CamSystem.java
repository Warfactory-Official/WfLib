package com.wf.wflib.cam;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.cam.api.CameraNet;
import com.wf.wflib.cam.api.CameraChannels;
import com.wf.wflib.cam.api.CameraContent;
import com.wf.wflib.cam.api.CameraMonitorBlock;
import com.wf.wflib.cam.api.CameraMonitorBlockEntity;
import com.wf.wflib.cam.api.Datalink;
import com.wf.wflib.cam.api.FeedTarget;
import com.wf.wflib.cam.api.client.DroneCamHud;
import com.wf.wflib.cam.client.CamClientCommands;
import com.wf.wflib.cam.client.CamClientConfig;
import com.wf.wflib.cam.client.CameraMonitorRenderer;
import com.wf.wflib.cam.client.CameraOsd;
import com.wf.wflib.cam.client.CamGemRender;
import com.wf.wflib.cam.client.CamerasApp;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.stream.api.StreamAudience;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.cam.api.CameraChunkStream;

/** Holders dependents name: {@link CameraContent}. */
public final class CamSystem implements WFSystem {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, WFLib.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);

    public static final DeferredHolder<Block, Block> CAMERA_MONITOR =
            BLOCKS.register("camera_monitor", () -> new CameraMonitorBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f)));

    public static final DeferredHolder<Item, Item> CAMERA_MONITOR_ITEM =
            ITEMS.register("camera_monitor", () -> new BlockItem(CAMERA_MONITOR.get(), new Item.Properties()));

    public static final DeferredHolder<Block, Block> SECURITY_CAMERA =
            BLOCKS.register("security_camera", () -> new SecurityCameraBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f).noOcclusion()));

    public static final DeferredHolder<Item, Item> SECURITY_CAMERA_ITEM =
            ITEMS.register("security_camera", () -> new BlockItem(SECURITY_CAMERA.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CameraMonitorBlockEntity>> CAMERA_MONITOR_BE =
            BLOCK_ENTITIES.register("camera_monitor", () -> BlockEntityType.Builder.of(
                    CameraMonitorBlockEntity::new, CAMERA_MONITOR.get()).build(null));

    /** Picks a camera up and puts it down on a monitor. */
    public static final DeferredHolder<Item, Item> CAMERA_LINKER =
            ITEMS.register("camera_linker", () -> new CameraLinkerItem(new Item.Properties().stacksTo(1)));

    /** What a linker is currently holding, between picking a camera up and putting it down on a panel. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FeedTarget>> CAMERA_LINK_TARGET =
            COMPONENTS.register("camera_link_target", () -> DataComponentType.<FeedTarget>builder()
                    .persistent(FeedTarget.CODEC)
                    .networkSynchronized(FeedTarget.STREAM_CODEC)
                    .build());

    static {
        COMPONENTS.register("camera_channels", () -> DataComponentType.<CameraChannels>builder()
                .persistent(CameraChannels.CODEC)
                .networkSynchronized(CameraChannels.STREAM_CODEC)
                .build());
    }

    @Override
    public String id() {
        return "cam";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("cam", 550, out -> {
            out.accept(CAMERA_MONITOR_ITEM.get());
            out.accept(SECURITY_CAMERA_ITEM.get());
            out.accept(CAMERA_LINKER.get());
        });
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "cam"), TickOrder.CAM, CameraNet::tick);
        container.registerConfig(ModConfig.Type.COMMON, CamConfig.SPEC, "wflib-cam.toml");
        container.registerConfig(ModConfig.Type.CLIENT, CamClientConfig.SPEC, "wflib-cam-client.toml");
        modBus.addListener(CamConfig::onLoad);
        modBus.addListener(CamClientConfig::onLoad);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, CamSystem::registerPayloads);
        StreamAudience.AUDIENCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "camera_feed"), 0,
                (ServerPlayer player, Entity entity) -> CameraNet.watches(player, entity));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CameraChunkStream.forget(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.Clone.class, event -> {
            if (event.getOriginal() instanceof ServerPlayer original) {
                CameraChunkStream.forget(original);
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> {
            CameraNet.shutdown();
            Datalink.shutdown();
        });
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event ->
                event.registerBlockEntityRenderer(CAMERA_MONITOR_BE.get(), CameraMonitorRenderer::new));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, CamClientCommands::onRegisterClientCommands);
        // HIGH: PdaFullscreen's layout reads the drone_cam signals this registers.
        modBus.addListener(EventPriority.HIGH, FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            CamGemRender.hideInFeeds();
            CameraOsd.register();
            DroneCamHud.registerLayout();
            CamerasApp.register();
        }));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("cam-1");
        r.playToClient(CameraFeedPacket.TYPE, CameraFeedPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> com.wf.wflib.cam.api.client.CameraFeedCache.accept(pkt.feed())));
        r.playToServer(CameraControlPacket.TYPE, CameraControlPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToServer(CameraCapabilityPacket.TYPE, CameraCapabilityPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(CameraPanelPacket.TYPE, CameraPanelPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> CamerasApp.accept(pkt.feedIds(), pkt.selected())));
        r.playToServer(PdaCamerasRequestPacket.TYPE, PdaCamerasRequestPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
    }
}
