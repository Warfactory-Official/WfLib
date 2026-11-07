package com.wf.wflib.soldier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

/** The same external packs work in a local modpack without enabling each one by hand. */
final class VoiceResources {
    private VoiceResources() {}

    static void add(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(out -> {
            if (!Files.isRegularFile(SoldierVoices.bundle())) return;
            var title = Component.literal("Warforge NPC voices");
            out.accept(new Pack(new PackLocationInfo("wflib/soldier_voices", title, PackSource.BUILT_IN, Optional.empty()),
                    new FilePackResources.FileResourcesSupplier(SoldierVoices.bundle()),
                    new Pack.Metadata(title, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), false),
                    new PackSelectionConfig(true, Pack.Position.TOP, false)));
        });
    }
}
