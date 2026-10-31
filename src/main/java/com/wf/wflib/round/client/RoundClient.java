package com.wf.wflib.round.client;

import com.wf.wflib.missile.api.client.MissileItemRenderer;
import com.wf.wflib.round.api.KineticShellItem;
import com.wf.wflib.round.api.RoundContent;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class RoundClient {
    private RoundClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(RoundContent.ROCKET.get(), RocketClient::new));
        modBus.addListener(RegisterClientExtensionsEvent.class, RoundClient::registerItemExtensions);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            RoundLights.register();
            RoundDebug.register();
        }));
    }

    private static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions renderer = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return MissileItemRenderer.instance();
            }
        };
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof KineticShellItem && !event.isItemRegistered(item)) {
                event.registerItem(renderer, item);
            }
        }
    }
}
