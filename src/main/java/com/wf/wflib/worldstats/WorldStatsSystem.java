package com.wf.wflib.worldstats;

import com.wf.wflib.core.api.WFSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Set;

/** A configurable on-screen readout of the world (day, time, biome climate, entities, chunk, light, moon, weather forecast). */
public final class WorldStatsSystem implements WFSystem {
    @Override
    public String id() {
        return "worldstats";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, StatsConfig.SPEC, "wflib-worldstats-client.toml");
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("worldstats-1").optional().playToClient(WeatherPacket.TYPE,
                WeatherPacket.CODEC, (pkt, ctx) -> ctx.enqueueWork(() -> com.wf.wflib.worldstats.client.StatsHud.weather(pkt))));
        // every second, each player's own level: the forecast is per dimension
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            if (e.getServer().getTickCount() % 20 != 0) {
                return;
            }
            for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
                if (p.connection != null && p.serverLevel().getLevelData() instanceof ServerLevelData d) {
                    PacketDistributor.sendToPlayer(p, new WeatherPacket(d.getClearWeatherTime(), d.getRainTime(), d.getThunderTime(),
                            d.isRaining(), d.isThundering()));
                }
            }
        });
    }

    @Override
    public void registerClient(IEventBus modBus) {
        com.wf.wflib.worldstats.client.StatsHud.register(modBus);
    }

    @Override
    public Set<String> requires() {
        return Set.of();
    }
}
