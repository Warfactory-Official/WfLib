package com.wf.wflib.contract.blacksite;

import com.wf.wflib.contract.Money;
import com.wf.wflib.compat.api.WarforgeCompat;
import com.wf.wflib.soldier.api.Site;
import com.wf.wflib.soldier.api.SiteFinder;
import com.wf.wflib.soldier.api.SiteSpec;
import com.wf.wflib.soldier.api.Sites;
import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The Black Site: a contested uplink far from spawn that pays its holders daily and draws retaliation. */
public final class BlackSiteService {
    /** Whole units per day, split among the holders who stayed. */
    public static final int DAILY_POOL = 10_000;
    public static final double MIN_PRESENCE = 0.25;
    public static final int RANGE = 8, LIFETIME_DAYS = 6, DAY_TICKS = 24_000;
    private static final RandomSource RNG = RandomSource.create();

    private BlackSiteService() {
    }

    public static void tick(MinecraftServer server) {
        var book = BlackSiteBook.get(server);
        long now = server.overworld().getGameTime();
        if (!book.active) {
            if (book.autoStart && now >= book.nextStart && !server.getPlayerList().getPlayers().isEmpty()) start(server);
            return;
        }
        ServerLevel level = server.overworld();
        Set<UUID> in = new HashSet<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.position().distanceToSqr(book.pos.getX() + 0.5, p.getY(), book.pos.getZ() + 0.5) > (double) RANGE * RANGE) continue;
            in.add(p.getUUID());
        }
        if (book.capture.step(in, com.wf.wflib.contract.api.Parties::membersOf)) {
            UUID holder = book.capture.holder();
            ServerPlayer p = server.getPlayerList().getPlayer(holder);
            String who = p == null ? "Someone" : p.getGameProfile().getName();
            server.getPlayerList().broadcastSystemMessage(Component.literal(who + " has taken the Black Site uplink.").withStyle(ChatFormatting.GOLD), false);
            com.wf.wflib.contract.news.News.add(server, who + " took the Black Site uplink");
        }
        book.setDirty();
        if (now >= book.dayEnd) {
            book.dayEnd += DAY_TICKS;
            book.days++;
            for (Map.Entry<UUID, Integer> e : book.capture.closeDay(DAILY_POOL, MIN_PRESENCE).entrySet()) {
                if (Money.pay(server, e.getKey(), e.getValue(), "contract.blacksite")) {
                    ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                    if (p != null) p.sendSystemMessage(Component.literal("Black Site pays you " + Money.format(e.getValue()) + ".").withStyle(ChatFormatting.GREEN));
                }
            }
        }
        waves(server, book, now);
        if (now >= book.end) stop(server, "The Black Site goes dark.");
    }

    /** Opens a Black Site somewhere 750-2000 blocks from spawn. Returns false (nothing changed) when no ground is found. */
    public static boolean start(MinecraftServer server) {
        var book = BlackSiteBook.get(server);
        if (book.active) return false;
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        Vec3 origin = Vec3.atCenterOf(level.getSharedSpawnPos());
        Optional<Vec3> found = Optional.empty();
        for (int attempt = 0; attempt < 6 && found.isEmpty(); attempt++) {
            found = SiteFinder.find(level, new SiteFinder.Request(origin, 750, 2000, 8, 0, true, 12), RNG)
                    .filter(v -> !claimed(level, BlockPos.containing(v)));
        }
        if (found.isEmpty()) {
            book.nextStart = now + 6000;                           // try again in five minutes
            book.setDirty();
            return false;
        }
        BlockPos pos = BlockPos.containing(found.get());
        level.setBlock(pos, Blocks.LODESTONE.defaultBlockState(), 3);
        book.active = true;
        book.pos = pos;
        book.start = now;
        book.end = now + (long) LIFETIME_DAYS * DAY_TICKS;
        book.dayEnd = now + DAY_TICKS;
        book.days = 0;
        book.nextWave = now + 6000 + RNG.nextInt(6000);
        book.capture.reset();
        var units = com.wf.wflib.soldier.api.RandomForces.all(server, "enemy");
        if (!units.isEmpty()) {
            try {
                book.guardSite = "bs" + (now % 100000);
                Sites.create(level, book.guardSite, found.get(), new SiteSpec(14, List.of(new SiteSpec.Squad(units, 8, SoldierOrder.Kind.PATROL))));
            } catch (IllegalArgumentException e) {
                book.guardSite = null;
            }
        }
        book.setDirty();
        server.getPlayerList().broadcastSystemMessage(Component.literal("A Black Site uplink is live near " + pos.getX() + ", " + pos.getZ()
                + ". Hold it for " + Money.format(DAILY_POOL) + " a day.").withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    public static void stop(MinecraftServer server, String message) {
        var book = BlackSiteBook.get(server);
        if (!book.active) return;
        ServerLevel level = server.overworld();
        if (book.pos != null) level.setBlock(book.pos, Blocks.AIR.defaultBlockState(), 3);
        if (book.guardSite != null) Sites.get(server, book.guardSite).ifPresent(Site::remove);
        for (var w : book.waves) Sites.get(server, w.siteId()).ifPresent(Site::remove);
        book.waves.clear();
        book.guardSite = null;
        book.active = false;
        book.capture.reset();
        book.nextStart = level.getGameTime() + 72_000L + RNG.nextInt(72_000);
        book.setDirty();
        server.getPlayerList().broadcastSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD), false);
    }

    private static boolean claimed(ServerLevel level, BlockPos pos) {
        return WarforgeCompat.isActive() && WarforgeCompat.factionClaiming(level, pos) != null;
    }

    /** Retaliation: every 5-10 minutes a squad turns up 100-160 blocks from whoever holds the site; old ones are removed. */
    private static void waves(MinecraftServer server, BlackSiteBook book, long now) {
        for (var w : List.copyOf(book.waves)) {
            if (now >= w.expires()) {
                Sites.get(server, w.siteId()).ifPresent(Site::remove);
                book.waves.remove(w);
            }
        }
        UUID holder = book.capture.holder();
        if (holder == null || now < book.nextWave) return;
        book.nextWave = now + 6000 + RNG.nextInt(6000);
        ServerPlayer target = server.getPlayerList().getPlayer(holder);
        var units = com.wf.wflib.soldier.api.RandomForces.all(server, "enemy");
        if (target == null || units.isEmpty()) return;
        int size = Math.min(20, 4 + 2 * book.days);
        var spot = SiteFinder.find(target.serverLevel(), new SiteFinder.Request(target.position(), 100, 160, 10, 40, true, 12), RNG);
        if (spot.isEmpty()) return;
        String id = "bw" + (now % 100000);
        try {
            Sites.create(target.serverLevel(), id, spot.get(), new SiteSpec(12, List.of(new SiteSpec.Squad(units, size, SoldierOrder.Kind.PATROL))));
            book.waves.add(new BlackSiteBook.Wave(id, now + 12_000));
            target.sendSystemMessage(Component.literal("Retaliation is on its way.").withStyle(ChatFormatting.DARK_RED));
        } catch (IllegalArgumentException e) {
            com.wf.wflib.contract.ContractSystem.LOGGER.warn("Black Site wave not raised: {}", e.getMessage());
        }
    }
}
