package com.wf.wflib.cam.client;

import com.wf.wflib.cam.SecurityCameraBlockEntity;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The security camera's client footprint, kept out of the system class so a bare server never loads Flywheel. */
public final class SecurityCameraClient {
    private SecurityCameraClient() {
    }

    public static void visualize(BlockEntityType<SecurityCameraBlockEntity> type) {
        SimpleBlockEntityVisualizer.builder(type).factory(SecurityCameraVisual::new).skipVanillaRender(be -> true).apply();
    }

    public static void registerItem(IEventBus modBus, DeferredHolder<Item, Item> item) {
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> event.registerItem(SecurityCameraItem.extensions(), item.get()));
    }
}
