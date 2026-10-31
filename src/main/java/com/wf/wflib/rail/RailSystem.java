package com.wf.wflib.rail;

import com.mojang.logging.LogUtils;
import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.rail.client.RailClient;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

import java.util.Set;

/**
 * Track alignments, right of way, tunnelling and track laying. Surveying and excavation are independent of
 * Immersive Railroading; laying track needs it.
 */
public final class RailSystem implements WFSystem {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String id() {
        return "rail";
    }

    @Override
    public Set<String> requires() {
        return Set.of();
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        // Registered whatever else is installed: a config that is only present sometimes throws on read.
        container.registerConfig(ModConfig.Type.COMMON, RailConfig.SPEC, "wflib-rail.toml");
        if (!RailCompat.isActive()) {
            LOGGER.info("[wflib] Immersive Railroading is absent; surveying and tunnelling still work,"
                    + " laying track does not");
        } else if (RailCompat.isForced()) {
            LOGGER.info("[wflib] rail features forced on without Immersive Railroading;"
                    + " only the IR-independent parts (excavation) will do anything");
        } else {
            LOGGER.info("[wflib] Immersive Railroading found; rail features enabled");
            if (!RailCompat.trackGraphAvailable()) {
                LOGGER.warn("[wflib] TrackAPI is missing, so nothing can read IR's track graph;"
                        + " excavation still works but track laying will not");
            }
        }
        modBus.addListener(RegisterPayloadHandlersEvent.class, RailNetwork::register);
        NeoForge.EVENT_BUS.register(RailNetwork.class);
        NeoForge.EVENT_BUS.register(RailCommands.class);
        NeoForge.EVENT_BUS.register(TrackReporter.class);
        NeoForge.EVENT_BUS.register(RailFeatures.class);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "rail"), TickOrder.RAIL,
                RailFeatures::tick);
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "rail"), TickOrder.RAIL_STOP,
                server -> RailFeatures.shutdown());
    }

    @Override
    public void registerClient(IEventBus modBus) {
        RailClient.register(modBus);
    }
}
