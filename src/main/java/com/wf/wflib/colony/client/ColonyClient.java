package com.wf.wflib.colony.client;

import com.wf.wflib.colony.api.ColonyContent;
import com.wf.wflib.colony.api.EntityGlyphid;
import com.wf.wflib.colony.api.GlyphidCaste;
import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client wiring of {@code ColonySystem}; the entry class stays loadable on a bare server. */
public final class ColonyClient {
    private ColonyClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            for (GlyphidCaste caste : GlyphidCaste.VALUES) {
                event.registerEntityRenderer(caste.type(), NoopRenderer::new);
            }
            event.registerEntityRenderer(ColonyContent.GLYPHID_WAYPOINT.get(), NoopRenderer::new);
            event.registerEntityRenderer(ColonyContent.GLYPHID_BOMB.get(), NoopRenderer::new);
            // Benchmark targets are server-side furniture; nothing to draw.
            event.registerEntityRenderer(ColonyContent.DEBUG_DUMMY.get(), NoopRenderer::new);
        });
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            GlyphidModel.init();
            for (GlyphidCaste caste : GlyphidCaste.VALUES) {
                bindGlyphid(caste.type(), caste);
            }
        }));
    }

    /** Generic so the wildcard on {@link GlyphidCaste#type()} has somewhere to be captured. */
    private static <T extends EntityGlyphid> void bindGlyphid(EntityType<T> type, GlyphidCaste caste) {
        VisualizerRegistry.setVisualizer(type, new EntityVisualizer<T>() {
            @Override
            public EntityVisual<? super T> createVisual(VisualizationContext ctx, T entity, float partialTick) {
                return new GlyphidVisual(ctx, entity, caste);
            }

            @Override
            public boolean skipVanillaRender(T entity) {
                return true;
            }
        });
    }
}
