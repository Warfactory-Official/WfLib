package com.wf.wflib.stream;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.stream.api.ChunkStreams;
import com.wf.wflib.stream.client.StreamClientCommands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class StreamSystem implements WFSystem {
    @Override
    public String id() {
        return "stream";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, StreamConfig.SPEC, "wflib-stream.toml");
        modBus.addListener(StreamConfig::onLoad);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, StreamCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ChunkStreams.drop(player);
            }
        });
        // Same-dimension respawn keeps the client level: chunks the old player held need forgetting.
        NeoForge.EVENT_BUS.addListener(PlayerEvent.Clone.class, event -> {
            if (event.getOriginal() instanceof ServerPlayer original) {
                ChunkStreams.forgetAll(original);
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> ChunkStreams.shutdown());
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, StreamClientCommands::onRegisterClientCommands);
    }
}
