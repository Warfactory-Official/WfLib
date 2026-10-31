package com.wf.wflib.radiation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import com.wf.wflib.core.api.WFLib;

/** Radiation lifecycle handlers; NTM's Services hooks -> NeoForge events. */
public final class RadiationEvents {

    public static final TagKey<Block> RAD_RESISTANT =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "rad_resistant"));

    private RadiationEvents() {
    }

    static void newRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(RadiationSettings.REGISTRY, RadiationSettings.CODEC);
        event.dataPackRegistry(RadiationDiffusivity.REGISTRY, RadiationDiffusivity.CODEC);
        event.dataPackRegistry(RadSourceRule.REGISTRY, RadSourceRule.CODEC);
    }

    /** Before loadLevel: spawn chunks palette-walk against the resolved source table. */
    static void aboutToStart(ServerAboutToStartEvent event) {
        RadiationSystemNT.onLoadComplete();
        RadiationSystemNT.registerConstantSources(event.getServer().registryAccess());
        // Divergence: NTM marks resistance in code only; wflib ships no shield blocks.
        BuiltInRegistries.BLOCK.getTagOrEmpty(RAD_RESISTANT).forEach(h -> RadiationSystemNT.markRadResistant(h.value()));
    }

    static void serverTick(ServerTickEvent.Post event) {
        // Before tickSim: incrementRad joins the in-flight sweep.
        for (ServerLevel level : event.getServer().getAllLevels()) FalloutData.tick(level);
        RadiationSystemNT.tickSim();
    }

    static void stopping(ServerStoppingEvent event) {
        RadiationSystemNT.onServerStopping(event.getServer());
    }

    /** FULL tags arrive here with the real LevelChunk; proto loads drop the carry (NTM: protos drop). */
    static void chunkData(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!event.getData().contains(RadiationCarrier.NBT_KEY)) return;
        ((RadiationCarrier) chunk).wflib$setRadiation(event.getData().getByteArray(RadiationCarrier.NBT_KEY));
    }

    static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            RadiationSystemNT.onChunkLoad(level, chunk);
        }
    }

    static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            RadiationSystemNT.onChunkUnload(level, chunk);
        }
    }
}
