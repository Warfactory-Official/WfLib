package com.wf.wflib.contract.heat;

import com.wf.wflib.contract.Money;
import com.wf.wflib.contract.ContractService;
import com.wf.wflib.soldier.api.SiteFinder;
import com.wf.wflib.soldier.api.SiteSpec;
import com.wf.wflib.soldier.api.Sites;
import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.UUID;

/** Heat from kills, hunter teams sent after hot operators, and the ways to cool down. */
public final class HeatService {
    private static final RandomSource RNG = RandomSource.create();
    /** A hunt starts 3-6 minutes after the heat crosses the line, and lasts at most 15. */
    private static final int MIN_DELAY = 3 * 1200, MAX_DELAY = 6 * 1200, LEASH = 15 * 1200, COOLDOWN = 30 * 1200;
    /** Surviving a hunt takes this much heat off. */
    private static final int SURVIVAL_RELIEF = 10;

    private HeatService() {
    }

    public static double heat(MinecraftServer server, UUID player, String faction) {
        return HeatBook.get(server).meter(player, faction).value(server.overworld().getGameTime());
    }

    public static boolean restricted(MinecraftServer server, UUID player, String faction) {
        return !faction.isEmpty() && HuntSize.restricted(heat(server, player, faction));
    }

    /** A real player killed a soldier of {@code faction}. */
    public static void onKill(MinecraftServer server, UUID killer, String faction) {
        if (faction.isEmpty()) return;
        var book = HeatBook.get(server);
        book.meter(killer, faction).add(HuntSize.PER_KILL, server.overworld().getGameTime());
        book.setDirty();
    }

    /** Buys {@code points} of heat off with a contact; false (nothing charged) when it cannot be afforded. */
    public static boolean bribe(ServerPlayer player, String faction, int points) {
        MinecraftServer server = player.getServer();
        double heat = heat(server, player.getUUID(), faction);
        points = Math.min(points, HuntSize.bribePoints(heat));
        if (heat <= 0 || points < 1) return false;
        int cost = HuntSize.bribeCost(heat, points);
        if (!Money.charge(player, cost, "contract.bribe")) return false;
        var book = HeatBook.get(server);
        book.meter(player.getUUID(), faction).add(-points, server.overworld().getGameTime());
        book.setDirty();
        return true;
    }

    /** About once a second: schedule hunts for the hot, launch the due, end the done. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        var book = HeatBook.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (book.hunts().containsKey(id) || now < book.cooldownUntil(id)) continue;
            String hottest = null;
            double best = HuntSize.HUNT_AT - 0.001;
            for (var e : book.of(id).entrySet()) {
                double v = e.getValue().value(now);
                if (v > best) {
                    best = v;
                    hottest = e.getKey();
                }
            }
            if (hottest != null) {
                book.put(id, new HeatBook.Hunt(hottest, now + MIN_DELAY + RNG.nextInt(MAX_DELAY - MIN_DELAY), null, 0));
                p.sendSystemMessage(Component.literal("You have drawn attention. Expect company.").withStyle(ChatFormatting.RED));
            }
        }
        for (var e : List.copyOf(book.hunts().entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            var hunt = e.getValue();
            if (hunt.siteId() == null) {
                if (p != null && now >= hunt.due()) launch(server, p, hunt, now, book);
                continue;
            }
            var site = Sites.get(server, hunt.siteId());
            boolean over = site.isEmpty() || site.get().cleared() || now >= hunt.expires();
            if (!over) continue;
            boolean survived = site.isPresent() && site.get().cleared();
            site.ifPresent(com.wf.wflib.soldier.api.Site::remove);
            book.endHunt(e.getKey());
            book.setCooldown(e.getKey(), now + COOLDOWN);
            if (survived) {
                book.meter(e.getKey(), hunt.faction()).add(-SURVIVAL_RELIEF, now);
                if (p != null) p.sendSystemMessage(Component.literal("The hunters are down. Word gets around: heat drops.").withStyle(ChatFormatting.GREEN));
            }
            book.setDirty();
        }
    }

    private static void launch(MinecraftServer server, ServerPlayer p, HeatBook.Hunt hunt, long now, HeatBook book) {
        int size = HuntSize.hunters(heat(server, p.getUUID(), hunt.faction()));
        var units = com.wf.wflib.soldier.api.RandomForces.all(server, hunt.faction());
        var spot = size == 0 || units.isEmpty() ? java.util.Optional.<net.minecraft.world.phys.Vec3>empty()
                : SiteFinder.find(p.serverLevel(), new SiteFinder.Request(p.position(), 60, 100, 10, 40, true, 16), RNG);
        if (spot.isEmpty()) {                                              // nothing to send, or nowhere to put them: try again later
            book.endHunt(p.getUUID());
            book.setCooldown(p.getUUID(), now + COOLDOWN / 3);
            return;
        }
        String siteId = "h" + p.getUUID().toString().replace("-", "").substring(0, 12) + (now % 1000);
        try {
            Sites.create(p.serverLevel(), siteId, spot.get(), new SiteSpec(12, List.of(new SiteSpec.Squad(units, size, SoldierOrder.Kind.PATROL))));
        } catch (IllegalArgumentException e) {
            book.endHunt(p.getUUID());
            book.setCooldown(p.getUUID(), now + COOLDOWN / 3);
            return;
        }
        book.put(p.getUUID(), new HeatBook.Hunt(hunt.faction(), hunt.due(), siteId, now + LEASH));
        p.sendSystemMessage(Component.literal("Hunters are out looking for you.").withStyle(ChatFormatting.DARK_RED));
        ContractService.recordHunt(server, p.getUUID());
    }
}
