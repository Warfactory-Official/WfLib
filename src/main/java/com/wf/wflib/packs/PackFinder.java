package com.wf.wflib.packs;

import com.wf.wflib.packs.api.Pack;
import com.wf.wflib.packs.api.PackHost;
import com.wf.wflib.packs.api.PackHosts;
import com.wf.wflib.packs.api.PackSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.List;

/** Every pack of a host that asks = required, always-on vanilla pack of the event's type, on top. */
final class PackFinder {
    private PackFinder() {
    }

    static void onAddPackFinders(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        event.addRepositorySource(out -> {
            for (PackHost host : PackHosts.all()) {
                PackSpec spec = host.spec();
                if (type == PackType.CLIENT_RESOURCES ? spec.clientResources() : spec.serverData()) {
                    for (Pack p : host.packs()) {
                        out.accept(vanilla(spec, p));
                    }
                }
            }
        });
    }

    private static net.minecraft.server.packs.repository.Pack vanilla(PackSpec spec, Pack p) {
        net.minecraft.server.packs.repository.Pack.Metadata metadata = new net.minecraft.server.packs.repository.Pack.Metadata(
                Component.literal(p.description()), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), false);
        return new net.minecraft.server.packs.repository.Pack(PackHosts.info(spec, p), PackHosts.resources(p), metadata,
                new PackSelectionConfig(true, net.minecraft.server.packs.repository.Pack.Position.TOP, false));
    }
}
