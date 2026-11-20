package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.Site;
import com.wf.wflib.soldier.api.SiteSpec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Operation sites on top of territories: one territory per squad, all at the site's centre, each raising its squad as a
 * garrison that never reinforces (a fallen soldier lowers the garrison's size). Public only so {@code api.Sites} can reach it.
 */
public final class SiteImpl {
    /** Territory ids are {@code s<id>_<n>}; site ids are at most {@link #MAX_ID} characters so those fit the 32 a key allows. */
    public static final int MAX_ID = 26;
    private static final String PREFIX = "s";

    private SiteImpl() {
    }

    public static Site create(ServerLevel level, String id, Vec3 centre, SiteSpec spec) {
        SoldierTemplate.key(id);
        if (id.length() > MAX_ID) throw new IllegalArgumentException("Site ids are at most " + MAX_ID + " characters");
        var server = level.getServer();
        var book = TerritoryBook.get(server);
        if (!ids(server, id).isEmpty()) throw new IllegalArgumentException("Site " + id + " already exists");
        String dimension = level.dimension().location().toString();
        int n = 0;
        List<String> made = new ArrayList<>();
        try {
            for (SiteSpec.Squad squad : spec.squads()) {
                String tid = PREFIX + id + "_" + n++;
                book.put(new Territory(tid, "Site " + id, "", dimension, squad.at() != null ? squad.at() : centre,
                        Math.max(2, squad.radius() > 0 ? squad.radius() : spec.radius()),
                        level.getMinBuildHeight(), level.getMaxBuildHeight(), squad.route(),
                        new Garrison(squad.count(), squad.units(), squad.mode(), Garrison.MAX_RESPAWN_SECONDS)));
                made.add(tid);
            }
        } catch (RuntimeException e) {
            made.forEach(book::remove);                       // all or nothing
            throw e;
        }
        return new Site(server, id);
    }

    public static Optional<Site> get(MinecraftServer server, String id) {
        return ids(server, id).isEmpty() ? Optional.empty() : Optional.of(new Site(server, id));
    }

    public static List<Site> all(MinecraftServer server) {
        List<String> seen = new ArrayList<>();
        for (String tid : TerritoryBook.get(server).all().keySet()) {
            String site = siteOf(tid);
            if (site != null && !seen.contains(site)) seen.add(site);
        }
        return seen.stream().map(s -> new Site(server, s)).toList();
    }

    /** The site a territory id belongs to, or null for an ordinary territory. */
    static String siteOf(String territoryId) {
        if (!territoryId.startsWith(PREFIX)) return null;
        int cut = territoryId.lastIndexOf('_');
        if (cut < 2 || !territoryId.substring(cut + 1).matches("\\d+")) return null;
        return territoryId.substring(1, cut);
    }

    public static List<String> ids(MinecraftServer server, String site) {
        List<String> out = new ArrayList<>();
        for (String tid : TerritoryBook.get(server).all().keySet()) if (site.equals(siteOf(tid))) out.add(tid);
        return out;
    }

    public static List<ServerPlayer> members(MinecraftServer server, String site) {
        List<ServerPlayer> out = new ArrayList<>();
        for (String tid : ids(server, site)) out.addAll(Garrisons.alive(server, tid));
        return out;
    }

    /** Soldiers still to be raised or alive: the sum of the squads' garrison sizes. */
    public static int remaining(MinecraftServer server, String site) {
        int n = 0;
        var book = TerritoryBook.get(server);
        for (String tid : ids(server, site)) {
            Territory t = book.get(tid);
            if (t != null && t.garrison() != null) n += t.garrison().count();
        }
        return n;
    }

    /** Soldiers alive or still to be raised in one squad (0-based, in the order the spec listed them); 0 for a squad that does not exist. */
    public static int squadRemaining(MinecraftServer server, String site, int squad) {
        Territory t = TerritoryBook.get(server).get(PREFIX + site + "_" + squad);
        return t == null || t.garrison() == null ? 0 : t.garrison().count();
    }

    public static int squads(MinecraftServer server, String site) {
        return ids(server, site).size();
    }

    public static List<ServerPlayer> squadMembers(MinecraftServer server, String site, int squad) {
        return Garrisons.alive(server, PREFIX + site + "_" + squad);
    }

    public static Territory territory(MinecraftServer server, String site) {
        var ids = ids(server, site);
        return ids.isEmpty() ? null : TerritoryBook.get(server).get(ids.get(0));
    }

    /** Takes the site's soldiers out of the world and its territories out of the book. */
    public static void remove(MinecraftServer server, String site) {
        var book = TerritoryBook.get(server);
        for (String tid : ids(server, site)) {
            Garrisons.alive(server, tid).forEach(com.wf.wflib.soldier.api.Soldiers::remove);
            book.remove(tid);
        }
    }

    /** A soldier of this garrison fell: a site's garrison shrinks by one, so nobody comes to replace them. */
    static void fell(MinecraftServer server, String garrison) {
        if (garrison.isEmpty() || siteOf(garrison) == null) return;
        var book = TerritoryBook.get(server);
        Territory t = book.get(garrison);
        if (t == null || t.garrison() == null || t.garrison().count() == 0) return;
        book.put(t.withGarrison(t.garrison().withCount(t.garrison().count() - 1)));
    }
}
