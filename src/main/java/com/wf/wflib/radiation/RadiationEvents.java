package com.wf.wflib.radiation;

import com.wf.wflib.WFLib;
import com.wf.wflib.hazard.HazardAssignment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import com.wf.wflib.hazard.HazardEntityEffects;
import com.wf.wflib.hazard.HazardSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Radiation + hazard lifecycle; NTM's Services hooks and LivingEntity/ItemEntity mixins -> NeoForge events. */
public final class RadiationEvents {

    public static final TagKey<Block> RAD_RESISTANT =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "rad_resistant"));

    private RadiationEvents() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RadiationConfig::onLoad);
        modBus.addListener(RadiationEvents::newRegistries);
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(RadiationEvents::aboutToStart);
        game.addListener(RadiationEvents::serverTick);
        game.addListener(RadiationEvents::stopping);
        game.addListener(RadiationEvents::chunkData);
        game.addListener(RadiationEvents::chunkLoad);
        game.addListener(RadiationEvents::chunkUnload);
        game.addListener(RadiationEvents::entityTick);
        game.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) -> RadiationCommand.register(e.getDispatcher()));
    }

    private static void newRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(HazardAssignment.REGISTRY, HazardAssignment.CODEC, HazardAssignment.CODEC);
        event.dataPackRegistry(RadiationSettings.REGISTRY, RadiationSettings.CODEC);
        event.dataPackRegistry(RadiationDiffusivity.REGISTRY, RadiationDiffusivity.CODEC);
        event.dataPackRegistry(RadSourceRule.REGISTRY, RadSourceRule.CODEC);
    }

    /** Before loadLevel: spawn chunks palette-walk against the resolved source table. */
    private static void aboutToStart(ServerAboutToStartEvent event) {
        RadiationSystemNT.onLoadComplete();
        HazardSystem.applyDataPack(event.getServer().registryAccess());
        RadiationSystemNT.registerConstantSources(event.getServer().registryAccess());
        // Divergence: NTM marks resistance in code only; wflib ships no shield blocks.
        BuiltInRegistries.BLOCK.getTagOrEmpty(RAD_RESISTANT).forEach(h -> RadiationSystemNT.markRadResistant(h.value()));
    }

    private static void serverTick(ServerTickEvent.Post event) {
        // Before tickSim: incrementRad joins the in-flight sweep.
        for (ServerLevel level : event.getServer().getAllLevels()) Fallout.tick(level);
        RadiationSystemNT.tickSim();
        HazardSystem.onServerTick(event.getServer());
    }

    private static void stopping(ServerStoppingEvent event) {
        RadiationSystemNT.onServerStopping(event.getServer());
    }

    /** FULL tags arrive here with the real LevelChunk; proto loads drop the carry (NTM: protos drop). */
    private static void chunkData(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!event.getData().contains(RadiationCarrier.NBT_KEY)) return;
        ((RadiationCarrier) chunk).wflib$setRadiation(event.getData().getByteArray(RadiationCarrier.NBT_KEY));
    }

    private static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            RadiationSystemNT.onChunkLoad(level, chunk);
        }
    }

    private static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            RadiationSystemNT.onChunkUnload(level, chunk);
        }
    }

    private static void entityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof LivingEntity living) {
            HazardSystem.tickMobEquipment(living);
            HazardEntityEffects.onUpdate(living);
        } else if (entity instanceof ItemEntity item) {
            HazardSystem.tickDroppedItem(item);
        }
    }
}
