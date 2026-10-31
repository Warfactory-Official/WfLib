package com.wf.wflib.mine.client;

import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.mine.api.MineItem;
import com.wf.wflib.mine.api.MineContent;
import com.wf.wflib.mine.api.MineEntity;
import com.wf.wflib.mine.api.client.MineRigs;
import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client wiring of {@code MineSystem}; the entry class stays loadable on a bare server. */
public final class MineClient {
    private MineClient() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(MineDebugRenderer.class);
        if (WFSystems.enabled("probe")) {
            modBus.addListener(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent.class,
                    MineProbe::onRegisterCapabilities);
        }
        // Mines draw through flywheel (see MineVisual); vanilla still needs a renderer or the dispatcher NPEs.
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event ->
                event.registerEntityRenderer(MineContent.MINE.get(), NoopRenderer::new));
        modBus.addListener(RegisterClientReloadListenersEvent.class, event ->
                event.registerReloadListener((barrier, manager, prepProfiler, reloadProfiler, background, game) ->
                        barrier.wait(net.minecraft.Util.NIL_UUID).thenRunAsync(MineRigs::init, game)));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            MineRigs.init();
            MineItemRenderers.init();
            VisualizerRegistry.setVisualizer(MineContent.MINE.get(), new EntityVisualizer<MineEntity>() {
                @Override
                public EntityVisual<? super MineEntity> createVisual(VisualizationContext ctx, MineEntity entity,
                                                                     float partialTick) {
                    return new MineVisual(ctx, entity);
                }

                @Override
                public boolean skipVanillaRender(MineEntity entity) {
                    return true;
                }
            });
        }));
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> {
            for (var holder : MineContent.items()) {
                Item item = holder.get();
                ResourceLocation model = ((MineItem) item).preset().modelId();
                event.registerItem(new IClientItemExtensions() {
                    @Override
                    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        BlockEntityWithoutLevelRenderer renderer = MineItemRenderers.get(model);
                        return renderer != null ? renderer : IClientItemExtensions.super.getCustomRenderer();
                    }
                }, item);
            }
        });
    }
}
