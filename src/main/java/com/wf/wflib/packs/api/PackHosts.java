package com.wf.wflib.packs.api;

import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.packs.PackHandshake;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Hosts by mod id, keyed {@code <modId>:packs}. Register in the mod constructor: jar roots are fixed until restart. */
public final class PackHosts {
    private static final WFRegistry<PackHost> HOSTS = WFRegistry.create("packs", "pack hosts");

    private PackHosts() {
    }

    /** Finds the mod files' pack dirs; scans nothing (see {@link PackHost#scan}). Duplicate mod id, or after freeze => throws. */
    public static PackHost register(PackSpec spec) {
        PackHost host = new PackHost(spec, providers(spec.jarDir()),
                FMLPaths.GAMEDIR.get().resolve(spec.gameDirName()).resolve("packs"));
        HOSTS.register(key(spec.modId()), host);
        return host;
    }

    public static PackHost get(String modId) {
        return HOSTS.require(key(modId));
    }

    /** Mod id order. */
    public static List<PackHost> all() {
        return HOSTS.values().stream().sorted(Comparator.comparing(h -> h.spec().modId())).toList();
    }

    /** Server, after a content reload: every host's list to every player; a client mismatch disconnects it. */
    public static void broadcast() {
        PacketDistributor.sendToAllPlayers(PackHandshake.serverPacks());
    }

    /** Pack list entry of {@code pack}: {@code <modId>/<id>}. */
    public static PackLocationInfo info(PackSpec spec, Pack pack) {
        return new PackLocationInfo(spec.modId() + "/" + pack.id(), Component.literal(pack.description()),
                PackSource.BUILT_IN, Optional.empty());
    }

    /** Zip packs reopen the file themselves: independent of the scan's file systems. */
    public static net.minecraft.server.packs.repository.Pack.ResourcesSupplier resources(Pack pack) {
        return pack.origin() == Pack.Origin.ARCHIVE ? new FilePackResources.FileResourcesSupplier(pack.source())
                : new PathPackResources.PathResourcesSupplier(pack.root());
    }

    private static ResourceLocation key(String modId) {
        return ResourceLocation.fromNamespaceAndPath(modId, "packs");
    }

    private static List<PackHost.Provider> providers(String jarDir) {
        List<PackHost.Provider> out = new ArrayList<>();
        for (IModFileInfo f : ModList.get().getModFiles()) {
            Path root = f.getFile().findResource(jarDir);
            if (!f.getMods().isEmpty() && Files.isDirectory(root)) {
                out.add(new PackHost.Provider(f.getMods().getFirst().getModId(), root));
            }
        }
        out.sort(Comparator.comparing(PackHost.Provider::mod));
        return out;
    }
}
