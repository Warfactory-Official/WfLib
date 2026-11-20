package com.wf.wflib.contract.pda;

import com.wf.wflib.contract.Money;
import com.wf.wflib.contract.ContractDef;
import com.wf.wflib.contract.ContractService;
import com.wf.wflib.contract.Contracts;
import com.wf.wflib.contract.SessionBook;
import com.wf.wflib.contract.heat.HeatBook;
import com.wf.wflib.contract.heat.HuntSize;
import com.wf.wflib.contract.item.PmcItems;
import com.wf.wflib.contract.news.News;
import com.wf.wflib.contract.profile.ProfileBook;
import com.wf.wflib.contract.profile.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Reads the mod's state for one player into a {@link Snapshot}. */
public final class SnapshotBuilder {
    private SnapshotBuilder() {
    }

    public static Snapshot build(ServerPlayer p, String notice) {
        var server = p.getServer();
        var id = p.getUUID();
        long now = p.serverLevel().getGameTime();
        var prof = ProfileBook.get(server).of(id);
        Rank rank = prof.rank();
        Rank next = rank.ordinal() + 1 < Rank.values().length ? Rank.values()[rank.ordinal() + 1] : null;

        List<Snapshot.Offer> offers = new ArrayList<>();
        for (var lead : ContractService.leads(server, id)) offers.add(offer(lead.getKey(), lead.getValue().name().toLowerCase(Locale.ROOT)));
        for (ContractDef def : ContractService.offers(server, id)) offers.add(offer(def, ""));

        Snapshot.Active active = SessionBook.get(server).of(id).map(s -> {
            var def = Contracts.get(s.def());
            return new Snapshot.Active(def.map(ContractDef::title).orElse(s.def().toString()), (int) s.centre().x, (int) s.centre().z,
                    (int) Math.max(0, s.deadline() - now) / 20, def.map(d -> describe(d)).orElse(List.of()));
        }).orElse(null);

        List<Snapshot.Heat> heat = new ArrayList<>();
        HeatBook.get(server).of(id).forEach((faction, m) -> {
            double v = m.value(now);
            if (v < 0.5) return;
            String state = HuntSize.restricted(v) ? "CLOSED" : v >= HuntSize.HUNT_AT ? "HUNTED" : "WATCHED";
            int points = HuntSize.bribePoints(v);
            heat.add(new Snapshot.Heat(faction, (int) Math.round(v), state, points, Money.format(HuntSize.bribeCost(v, points))));
        });

        int drives = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(PmcItems.INTEL_DRIVE.get())) drives += s.getCount();

        return new Snapshot(rank.id(), prof.rep, next == null ? 0 : next.threshold, next == null ? "" : next.id(), prof.completed, prof.failed,
                prof.totalKills(), Money.format(prof.earnings), drives, offers, active, heat, News.get(server).latest(8), blackSite(p, now),
                com.wf.wflib.contract.api.MarketNews.headline(server), notice, com.wf.wflib.contract.difficulty.Tier.label(com.wf.wflib.contract.difficulty.Difficulty.get(server).tier(server, id)));
    }

    private static Snapshot.Offer offer(ContractDef d, String intel) {
        return new Snapshot.Offer(d.id().toString(), d.title(), d.description(), Money.format(d.money()), d.rep(), d.timeLimitMinutes(),
                d.minDistance(), d.maxDistance(), d.buyIn() > 0 ? Money.format(d.buyIn()) : "", d.gate().id(), d.employer(), intel, describe(d));
    }

    private static String blackSite(ServerPlayer p, long now) {
        var book = com.wf.wflib.contract.blacksite.BlackSiteBook.get(p.getServer());
        if (!book.active) return "";
        String holder = book.capture.holder() == null ? "NOBODY"
                : p.getServer().getProfileCache().get(book.capture.holder()).map(g -> g.getName()).orElse("SOMEONE");
        return "BLACK SITE " + book.pos.getX() + " " + book.pos.getZ() + "  HELD BY " + holder.toUpperCase(Locale.ROOT) + "  "
                + Math.max(0, (book.end - now) / 24000) + " DAYS LEFT";
    }

    /** One short line per objective. */
    public static List<String> describe(ContractDef def) {
        List<String> out = new ArrayList<>();
        for (ContractDef.Objective o : def.objectives()) {
            out.add(switch (o) {
                case ContractDef.KillTarget k -> k.squad() == 0 ? "Kill the target" : "Kill squad " + (k.squad() + 1);
                case ContractDef.RetrieveItem r -> "Bring back " + r.count() + " " + pretty(r.item().getPath());
                case ContractDef.DeliverItem d -> "Deliver " + d.count() + " " + pretty(d.item().getPath());
                case ContractDef.ClearArea c -> "Clear the site";
            });
        }
        return out;
    }

    static String pretty(String path) {
        return path.replace('_', ' ');
    }
}
