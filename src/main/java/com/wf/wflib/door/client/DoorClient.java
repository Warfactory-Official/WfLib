package com.wf.wflib.door.client;

import com.wf.wflib.door.DoorContent;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** The door feature's whole client-side footprint: one visualizer and one rig-declaration call. */
public final class DoorClient {

    private DoorClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, DoorClient::onClientSetup);
        modBus.addListener(RegisterCapabilitiesEvent.class, DoorProbe::onRegisterCapabilities);
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> {
            for (var holder : DoorContent.DOOR_ITEMS.values()) {
                event.registerItem(new IClientItemExtensions() {
                    @Override
                    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return DoorItemRenderer.instance();
                    }
                }, holder.get());
            }
        });
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            DoorRigs.init();
            SimpleBlockEntityVisualizer.builder(DoorContent.DOOR.get())
                    .factory(DoorVisual::new)
                    // The visual draws the whole door, so nothing vanilla should draw it again.
                    .skipVanillaRender(door -> true)
                    .apply();
        });
    }
}
