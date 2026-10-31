package com.wf.wflib.drone.client;

import com.wf.wflib.drone.DroneDebrisEntity;
import com.wf.wflib.drone.DroneSystem;
import com.wf.wflib.drone.api.DroneEntity;
import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client wiring of {@link DroneSystem}; Flywheel types stay out of the entry class. */
public final class DroneClientSetup {
    private DroneClientSetup() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            // Drawn by Flywheel visuals; vanilla still needs a renderer or the dispatcher NPEs.
            event.registerEntityRenderer(DroneSystem.DRONE.get(), NoopRenderer::new);
            event.registerEntityRenderer(DroneSystem.DRONE_DEBRIS.get(), NoopRenderer::new);
            event.registerEntityRenderer(DroneSystem.CRATE.get(), CrateRenderer::new);
        });
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(DroneSystem.DRONE_PAD_MENU.get(), DronePadScreen::new));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            com.wf.wflib.drone.api.client.PartRigs.init();
            VisualizerRegistry.setVisualizer(DroneSystem.DRONE.get(), new EntityVisualizer<DroneEntity>() {
                            public EntityVisual<? super DroneEntity> createVisual(VisualizationContext ctx, DroneEntity entity,
                                                                      float partialTick) {
                    return new DroneVisual(ctx, entity);
                }

                            public boolean skipVanillaRender(DroneEntity entity) {
                    return true;
                }
            });
            VisualizerRegistry.setVisualizer(DroneSystem.DRONE_DEBRIS.get(), new EntityVisualizer<DroneDebrisEntity>() {
                            public EntityVisual<? super DroneDebrisEntity> createVisual(VisualizationContext ctx,
                                                                            DroneDebrisEntity entity, float partialTick) {
                    return new DroneDebrisVisual(ctx, entity);
                }

                            public boolean skipVanillaRender(DroneDebrisEntity entity) {
                    return true;
                }
            });
            DronesApp.register();
            RadioApp.register();
        }));
        com.wf.wflib.missile.api.client.SimGhostEntities.KINDS.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "drone"),
                (level, model, exhaust) -> {
                    DroneEntity d = new DroneEntity(DroneSystem.DRONE.get(), level);
                    d.setModelId(model);
                    return d;
                });
        com.wf.wflib.pda.api.client.FullscreenControl.HOOK.set(com.wf.wflib.core.api.WFLib.MODID,
                new com.wf.wflib.pda.api.client.FullscreenControl() {
                    @Override
                    public boolean controllable(int feedId) {
                        return DronesApp.controllable(feedId);
                    }

                    @Override
                    public void control(int feedId) {
                        DronesApp.control(feedId);
                    }

                    @Override
                    public String name(int feedId) {
                        return DronesApp.name(feedId);
                    }
                });
        NeoForge.EVENT_BUS.register(DroneAudioClient.class);
        NeoForge.EVENT_BUS.register(DronePilotView.class);
    }
}
