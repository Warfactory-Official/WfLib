package com.wf.wflib.missile.client;

import com.wf.wflib.missile.MissileFlightAudioPacket;
import com.wf.wflib.missile.MissileSystem;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.missile.api.MissileContent;
import com.wf.wflib.missile.api.MissileItem;
import com.wf.wflib.missile.api.MissilePreset;
import com.wf.wflib.missile.api.MissilePresets;
import com.wf.wflib.missile.api.SimGhostSyncPacket;
import com.wf.wflib.missile.api.client.ModModels;
import com.wf.wflib.missile.api.client.MissileItemRenderer;
import com.wf.wflib.missile.api.client.SimGhostEntities;
import com.wf.wflib.missile.tv.TvLinkPacket;
import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.Map;
import com.wf.wflib.missile.api.client.MissileRigs;

public final class MissileClient {
    private MissileClient() {
    }

    public static void register(IEventBus modBus) {
        com.wf.wflib.missile.api.client.SimGhostEntities.KINDS.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "missile"),
                (level, model, exhaust) -> {
                    var m = new com.wf.wflib.missile.api.MissileEntity(MissileContent.STEALTH_MISSILE.get(), level);
                    m.setModelId(model);
                    m.setExhaustColor(exhaust);
                    return m;
                });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(MissileContent.STEALTH_MISSILE.get(), MissileRenderer::new);
            ModModels.bake();
        });
        modBus.addListener(ModelEvent.ModifyBakingResult.class, MissileClient::onModifyBakingResult);
        modBus.addListener(RegisterMenuScreensEvent.class, event ->
                event.register(MissileSystem.MISSILE_DISPENSER_MENU.get(), MissileDispenserScreen::new));
        modBus.addListener(RegisterClientExtensionsEvent.class, MissileClient::registerItemExtensions);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            MissileRigs.load();
            TvHud.register();
            VisualizerRegistry.setVisualizer(MissileContent.STEALTH_MISSILE.get(), new EntityVisualizer<MissileEntity>() {
                @Override
                public EntityVisual<? super MissileEntity> createVisual(VisualizationContext ctx, MissileEntity entity,
                                                                        float partialTick) {
                    return new MissileVisual(ctx, entity);
                }

                @Override
                public boolean skipVanillaRender(MissileEntity entity) {
                    return false;
                }
            });
        }));
    }

    public static void handleMissileAudio(MissileFlightAudioPacket pkt) {
        if (Minecraft.getInstance().level != null) {
            MissileAudioClient.upsert(pkt);
        }
    }

    public static void handleSimGhosts(SimGhostSyncPacket pkt) {
        SimGhostEntities.accept(pkt);
    }

    public static void handleTvLink(TvLinkPacket pkt) {
        TvClient.accept(pkt);
    }

    private static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions renderer = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return MissileItemRenderer.instance();
            }
        };
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof MissileItem && !event.isItemRegistered(item)) {
                event.registerItem(renderer, item);
            }
        }
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        BakedModel template = itemTemplate(models);
        if (template == null) {
            return;
        }
        for (MissilePreset preset : MissilePresets.all()) {
            models.put(itemModel(preset.id()), template);
        }
    }

    private static BakedModel itemTemplate(Map<ModelResourceLocation, BakedModel> models) {
        for (MissilePreset preset : MissilePresets.all()) {
            BakedModel model = models.get(itemModel(preset.id()));
            if (model != null && model.isCustomRenderer()) {
                return model;
            }
        }
        return null;
    }

    private static ModelResourceLocation itemModel(ResourceLocation preset) {
        return ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(preset.getNamespace(),
                "missile_" + preset.getPath()));
    }
}
