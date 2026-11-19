package com.wf.wflib.photo;

import com.wf.wflib.core.api.CreativeTabs;
import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.core.api.WFSystem;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Cameras and photographs: a camera item whose shot is rendered by the client through an offscreen view at a fixed
 * pose and field of view, uploaded in fragments, validated and stored by the server, and browsed in the PDA gallery
 * or on a printed photograph item. See {@code docs/photos.md}.
 */
public final class PhotoSystem implements WFSystem {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);

    public static final DeferredHolder<Item, Item> CAMERA_ITEM =
            ITEMS.register("camera", () -> new CameraItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> PHOTO_ITEM =
            ITEMS.register("photo", () -> new PhotoItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PhotoRef>> PHOTO_REF =
            COMPONENTS.register("photo", () -> DataComponentType.<PhotoRef>builder().persistent(PhotoRef.CODEC)
                    .networkSynchronized(PhotoRef.STREAM_CODEC).build());

    @Override
    public String id() {
        return "photo";
    }

    /** The PDA hosts the gallery; the offscreen view renders the shot. */
    @Override
    public Set<String> requires() {
        return Set.of("pda", "cam");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        CreativeTabs.contribute("photo", 450, out -> out.accept(CAMERA_ITEM.get()));
        container.registerConfig(ModConfig.Type.COMMON, PhotoConfig.SPEC, "wflib-photo.toml");
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, PhotoSystem::registerPayloads);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo"), TickOrder.PHOTO,
                level -> {
                    if (level.dimension() == Level.OVERWORLD) {
                        PhotoServer.tick(level.getServer());
                    }
                });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PhotoServer.forget(player.getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> PhotoServer.clear());
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class,
                event -> event.enqueueWork(com.wf.wflib.photo.client.PhotoClient::setup));
        modBus.addListener(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent.class,
                com.wf.wflib.photo.client.PhotoClient::clientExtensions);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("photo-1");
        r.playToClient(PhotoShutterPacket.TYPE, PhotoShutterPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> com.wf.wflib.photo.client.PhotoClient.shutter(pkt)));
        r.playToServer(PhotoUploadPacket.TYPE, PhotoUploadPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToServer(PhotoListRequestPacket.TYPE, PhotoListRequestPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(PhotoListPacket.TYPE, PhotoListPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> com.wf.wflib.photo.client.PhotoClient.list(pkt)));
        r.playToServer(PhotoFetchPacket.TYPE, PhotoFetchPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
        r.playToClient(PhotoChunkPacket.TYPE, PhotoChunkPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> com.wf.wflib.photo.client.PhotoClient.chunk(pkt)));
        r.playToServer(PhotoActionPacket.TYPE, PhotoActionPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx));
    }
}
