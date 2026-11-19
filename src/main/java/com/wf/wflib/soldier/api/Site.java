package com.wf.wflib.soldier.api;

import com.wf.wflib.soldier.SiteImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A live operation site: squads raised inside a disc and kept there. Cheap to hold and to re-create from its id after a
 * restart ({@link Sites#get}): the state is the world's territories, not this object. Server thread only.
 */
public final class Site {
    private final MinecraftServer server;
    private final String id;

    public Site(MinecraftServer server, String id) {
        this.server = server;
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Vec3 centre() {
        var t = SiteImpl.territory(server, id);
        return t == null ? Vec3.ZERO : t.centre();
    }

    public int radius() {
        var t = SiteImpl.territory(server, id);
        return t == null ? 0 : t.radius();
    }

    /** The disc's bounding box, full height. */
    public AABB bounds() {
        Vec3 c = centre();
        int r = radius();
        return new AABB(c.x - r, -2048, c.z - r, c.x + r, 2048, c.z + r);
    }

    /** The site's soldiers that are in the world now (those still waiting to be raised are not here yet). */
    public List<ServerPlayer> members() {
        return SiteImpl.members(server, id);
    }

    /** Soldiers alive or still to be raised; falls as the site's soldiers die. */
    public int remaining() {
        return SiteImpl.remaining(server, id);
    }

    /** True when every soldier of the site is dead. */
    public boolean cleared() {
        return remaining() == 0 && members().isEmpty();
    }

    /** Takes the site's soldiers out of the world and forgets the site. */
    public void remove() {
        SiteImpl.remove(server, id);
    }
}
