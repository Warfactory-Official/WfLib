package com.wf.wflib.soldier;

import com.wf.wflib.pda.api.Pda;
import com.wf.wflib.soldier.api.Offer;
import com.wf.wflib.soldier.api.RecruitSource;
import com.wf.wflib.soldier.api.SoldierRole;
import com.wf.wflib.soldier.api.Recruiting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.List;

/**
 * Getting soldiers: WFLib's own sources, hiring (a cost paid, then the soldier arrives beside the hirer or walks in after a
 * delay), and arrivals still on their way (saved with the {@link SquadBook}, so a restart does not lose a paid hire).
 */
public final class Recruits {
    /** Pending arrivals are checked this often (ticks). */
    static final int EVERY = 20;

    /** Seats an arrival: the editor's variants, unless a test stands in for a gun mod. */
    interface Hirer {
        Companion hire(ServerPlayer owner, String variant, boolean fromAfar);
    }

    static Hirer hirer = Squads::hire;

    private Recruits() {}

    /** Registers WFLib's own sources. */
    static void register() {
        Recruiting.source(gesture("rescue", "Rescue"));
        Recruiting.source(gesture("garrison", "Garrison"));
        Recruiting.source(gesture("barracks", "Barracks"));          // its menus come from the barracks types
        Recruiting.source(new RecruitSource() {
            @Override public String id() { return "pda"; }

            @Override public String title() { return "PDA"; }

            /** Needs a PDA on the hirer: everything friendly the map-maker saved, at the configured price and delay. */
            @Override public List<Offer> offers(ServerPlayer player) {
                if (!Pda.carries(player)) return List.of();
                return Recruiting.variants(player.getServer(), RecruitConfig.callinCost(), RecruitConfig.callinDelay());
            }
        });
        Recruiting.barracks(new MarkedBarracks());
    }

    private static RecruitSource gesture(String id, String title) {
        return new RecruitSource() {
            @Override public String id() { return id; }

            @Override public String title() { return title; }

            @Override public List<Offer> offers(ServerPlayer player) { return List.of(); }
        };
    }

    /** Saved variants that fight for a friendly faction: what a hirer can ask for. */
    public static List<SoldierTemplate> friendlyVariants(MinecraftServer server) {
        var roster = SoldierRoster.get(server);
        return roster.units().values().stream()
                .filter(u -> roster.factions().containsKey(u.faction()) && !roster.factions().get(u.faction()).hostile())
                .sorted(Comparator.comparing(SoldierTemplate::id)).toList();
    }

    /**
     * Pays for an offer and either seats the soldier beside the hirer or sets them on their way.
     *
     * @return what to tell the player
     */
    public static String hire(ServerPlayer player, Offer offer) {
        var server = player.getServer();
        if (friendlyVariants(server).stream().noneMatch(v -> v.id().equals(offer.variant()))) throw new IllegalArgumentException("That soldier is no longer on offer");
        SoldierEditor.loadout(server, offer.variant());                           // throws when the kit it needs is not installed: before anything is paid
        if (!offer.cost().affordable(player)) throw new IllegalArgumentException("You cannot afford " + offer.cost().describe());
        if (!offer.cost().charge(player)) throw new IllegalArgumentException("You cannot afford " + offer.cost().describe());
        if (offer.delaySeconds() <= 0) {
            Squads.hire(player, offer.variant(), false);
            return offer.title() + " joined you";
        }
        SquadBook.get(server).addPending(new SquadBook.Pending(player.getUUID(), offer.variant(), server.overworld().getGameTime() + offer.delaySeconds() * 20L));
        return offer.title() + " is on the way: " + offer.delaySeconds() + " s";
    }

    /** Whether wages are collected: the config says so, or the economy's pricing does. */
    static boolean wagesOn() {
        var pricing = Recruiting.pricing();
        return RecruitConfig.wages() || pricing != null && pricing.upkeepOn();
    }

    /** What a soldier of this role costs per day: the config's wage plus the economy's upkeep. */
    static com.wf.wflib.soldier.api.Cost upkeep(SoldierRole role) {
        var pricing = Recruiting.pricing();
        return com.wf.wflib.soldier.api.Cost.all(List.of(RecruitConfig.wage(),
                pricing == null ? com.wf.wflib.soldier.api.Cost.FREE : com.wf.wflib.soldier.api.Cost.currency(pricing.upkeep(role))));
    }

    /** A day's upkeep for soldiers of these roles as text: the economy's total, and the config's item wage each; empty when it is free. */
    static String upkeepText(List<SoldierRole> roles) {
        var pricing = Recruiting.pricing();
        var wallet = Recruiting.wallet();
        List<String> parts = new java.util.ArrayList<>();
        long money = pricing == null ? 0 : roles.stream().mapToLong(pricing::upkeep).sum();
        if (money > 0) parts.add(wallet == null ? money + " credits" : wallet.format(money));
        var item = RecruitConfig.wage();
        if (item != com.wf.wflib.soldier.api.Cost.FREE) parts.add(item.describe() + (roles.size() == 1 ? "" : " each"));
        return String.join(" + ", parts);
    }

    /** A saved variant's gun, magazines and range as a menu line: "M4A1 - 5 mags - 80 m". */
    static String kit(SoldierTemplate u) {
        return itemName(u.gun()) + " - " + (u.spares() + 1) + " mags - " + u.range() + " m";
    }

    /** Armour pieces and grenades: "3 armour - 2 grenades". */
    static String gear(SoldierTemplate u) {
        return u.armour().size() + (u.armour().size() == 1 ? " armour piece" : " armour pieces") + " - " + u.grenades().size() + (u.grenades().size() == 1 ? " grenade" : " grenades");
    }

    private static String itemName(String id) {
        var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl))
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl).getDescription().getString();
        return rl == null ? id : rl.getPath();
    }

    /** The PDA's summary line: the squad, hires on the way, what the wage costs a day and when it falls due, and the wallet's balance. */
    static String header(ServerPlayer p) {
        var server = p.getServer();
        int squad = Squads.deployed(p.getUUID()).size();
        List<String> parts = new java.util.ArrayList<>();
        parts.add(squad + (squad == 1 ? " SOLDIER" : " SOLDIERS"));
        int coming = arriving(server, p.getUUID());
        if (coming > 0) parts.add(coming + " ON THE WAY");
        if (wagesOn() && squad > 0) {
            long left = (Wages.DAY - server.overworld().getGameTime() % Wages.DAY) / 20;
            String each = upkeepText(Squads.deployed(p.getUUID()).stream().map(b -> SoldierRole.tactics(b.role)).toList());
            if (!each.isEmpty()) parts.add("UPKEEP " + each + " / DAY, DUE IN " + left / 60 + "M");
        }
        var wallet = Recruiting.wallet();
        if (wallet != null && wallet.balance(p) >= 0) parts.add("BALANCE " + wallet.format(wallet.balance(p)));
        return String.join("  -  ", parts);
    }

    /** How many of the player's hires have not arrived yet. */
    public static int arriving(MinecraftServer server, java.util.UUID owner) {
        return (int) SquadBook.get(server).pending().stream().filter(p -> p.owner().equals(owner)).count();
    }

    static void onServerTick(ServerTickEvent.Post e) {
        var server = e.getServer();
        if (server.getTickCount() % EVERY != 3) return;
        long now = server.overworld().getGameTime();
        arrivals(server, now);
        if (server.getTickCount() % (EVERY * 10) == 3) Wages.tick(server, now);
    }

    /** Seats every hire whose time has come; one whose hirer is away waits for them. */
    static void arrivals(MinecraftServer server, long now) {
        var book = SquadBook.get(server);
        for (var p : List.copyOf(book.pending())) {
            if (now < p.due()) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(p.owner());
            if (owner == null || !owner.isAlive()) continue;                        // it waits for them
            book.removePending(p);
            try {
                var c = hirer.hire(owner, p.variant(), true);
                owner.sendSystemMessage(Component.literal(c.person() + " has arrived and joined " + Companion.TEAMS[c.fireTeam]));
            } catch (IllegalArgumentException ex) {
                owner.sendSystemMessage(Component.literal("A hire could not arrive: " + ex.getMessage()));
            }
        }
    }
}
