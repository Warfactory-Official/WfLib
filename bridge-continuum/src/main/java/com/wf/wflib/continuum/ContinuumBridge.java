package com.wf.wflib.continuum;

import dev.golem.core.platform.ModEntries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Runs NTM: Continuum on WFLib's systems. A Golem-based mod: its start-up goes through {@link ModEntries}, it replaces nothing by mixin, and
 * everything it changes in Continuum it changes through Continuum's and Golem Core's own seams (data-map patches, substitutions, hooks).
 * See {@code docs/ntm-continuum-compat.md} in the WFLib repository.
 */
@Mod(ContinuumBridge.ID)
public final class ContinuumBridge {
    public static final String ID = "wflib_continuum";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    public ContinuumBridge(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.STARTUP, BridgeConfig.SPEC, "wflib_continuum.toml");
        ModEntries.start(new BridgeEntry());
        com.wf.wflib.core.api.Features.register(new BridgeFeatures());
        modBus.addListener(AddPackFindersEvent.class, ContinuumBridge::addPacks);
        modBus.addListener(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent.class, event -> {
            if (BridgeConfig.doors()) DoorRecipes.hideContinuumDoors(event);
        });
        modBus.addListener(net.neoforged.neoforge.event.RegisterGameTestsEvent.class, e -> e.register(BridgeGameTests.class));
    }

    /** The generated data pack (patches that stand Continuum's own armour reduction down, and WFLib armour profiles seeded from its numbers). */
    private static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        Path root;
        try {
            root = GeneratedPack.build();
        } catch (IOException | RuntimeException e) {
            LOG.error("The generated pack could not be built; Continuum's armour stays as it is", e);
            return;
        }
        PackLocationInfo info = new PackLocationInfo(ID + "_generated", Component.literal("WFLib: Continuum bridge (generated)"), PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(root), PackType.SERVER_DATA,
                new net.minecraft.server.packs.PackSelectionConfig(true, Pack.Position.TOP, false));
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
