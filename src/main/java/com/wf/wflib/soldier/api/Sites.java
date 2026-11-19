package com.wf.wflib.soldier.api;

import com.wf.wflib.soldier.SiteImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Operation sites: "stand this force here". Soldiers are raised a few at a time by the garrison machinery (so a large
 * site does not stall the server) and a fallen soldier is not replaced. Per-world limit: the territory book's.
 */
public final class Sites {
    private Sites() {
    }

    /**
     * @param id lowercase letters, digits, {@code _} or {@code -}, at most {@link SiteImpl#MAX_ID} characters; unique
     * @throws IllegalArgumentException for a bad id, a taken id, a bad squad, or a full territory book (nothing is created then)
     */
    public static Site create(ServerLevel level, String id, Vec3 centre, SiteSpec spec) {
        return SiteImpl.create(level, id, centre, spec);
    }

    public static Optional<Site> get(MinecraftServer server, String id) {
        return SiteImpl.get(server, id);
    }

    public static List<Site> all(MinecraftServer server) {
        return SiteImpl.all(server);
    }
}
