package com.wf.wflib.radiation;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.explosion.api.RadiationSink;
import com.wf.wflib.radiation.api.RadiationConfig;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** Chunk radiation field (NTM NEXT sim): sources, diffusion, fallout, sidecar persistence, {@code /wfrad}. */
public final class RadiationSystem implements WFSystem {
    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "radiation";
    }

    @Override
    public Set<String> requires() {
        return Set.of();
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        active = true;
        container.registerConfig(ModConfig.Type.COMMON, RadiationConfig.SPEC, "wflib-radiation.toml");
        modBus.addListener(RadiationConfig::onLoad);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, RadiationEvents::newRegistries);
        RadiationSink.SINK.set(WFLib.MODID, new ExplosionRadiation());

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(ServerAboutToStartEvent.class, RadiationEvents::aboutToStart);
        // Before hazard's server tick: fallout -> sim -> hazards.
        game.addListener(EventPriority.HIGH, ServerTickEvent.Post.class, RadiationEvents::serverTick);
        game.addListener(ServerStoppingEvent.class, RadiationEvents::stopping);
        game.addListener(ChunkDataEvent.Load.class, RadiationEvents::chunkData);
        game.addListener(ChunkEvent.Load.class, RadiationEvents::chunkLoad);
        game.addListener(ChunkEvent.Unload.class, RadiationEvents::chunkUnload);
        game.addListener(RegisterCommandsEvent.class, e -> RadiationCommand.register(e.getDispatcher()));
    }
}
