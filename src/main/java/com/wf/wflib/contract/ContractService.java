package com.wf.wflib.contract;

import com.wf.wflib.contract.api.Parties;
import com.wf.wflib.contract.profile.Profile;
import com.wf.wflib.contract.profile.ProfileBook;
import com.wf.wflib.contract.profile.Rank;
import com.wf.wflib.map.api.MarkerData;
import com.wf.wflib.soldier.api.SiteFinder;
import com.wf.wflib.soldier.api.SiteSpec;
import com.wf.wflib.soldier.api.Sites;
import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Taking, running and ending contracts. The rules are in {@link Engine}; this is the part that touches the world. */
public final class ContractService {
    /** The party earns what a soloist would, shared out. */
    public static final double TEAM_BONUS = 1.0;
    /** Real players must be at least this far from a new site. */
    public static final int PLAYER_CLEARANCE = 96;
    /** Minutes before the same job comes round again: short after a win, longer after a failure or abandonment. */
    static final long COOLDOWN_DONE = 10 * 1200L, COOLDOWN_FAILED = 20 * 1200L;
    private static final RandomSource RNG = RandomSource.create();
    /** Sessions whose site chunks were held since this run began. */
    private static final java.util.Set<UUID> HELD = new java.util.HashSet<>();

    private ContractService() {
    }

    /** Why a contract could not be taken. */
    public static final class Refused extends Exception {
        public Refused(String message) {
            super(message);
        }
    }

    /** Definitions this operator's rank allows. */
    public static List<ContractDef> offers(MinecraftServer server, UUID player) {
        Rank rank = ProfileBook.get(server).of(player).rank();
        return Contracts.all().stream().filter(d -> d.source().equals("board")).filter(d -> rank.atLeast(d.gate()))
                .filter(d -> !com.wf.wflib.contract.heat.HeatService.restricted(server, player, d.employer())).toList();
    }

    /** Contracts this operator holds intel for (oldest first), with the intel's quality. */
    public static List<Map.Entry<ContractDef, com.wf.wflib.contract.intel.Intel.Quality>> leads(MinecraftServer server, UUID player) {
        List<Map.Entry<ContractDef, com.wf.wflib.contract.intel.Intel.Quality>> out = new ArrayList<>();
        for (String[] lead : ProfileBook.get(server).of(player).leads) {
            ResourceLocation id = ResourceLocation.tryParse(lead[0]);
            if (id != null) Contracts.get(id).ifPresent(d -> out.add(Map.entry(d, com.wf.wflib.contract.intel.Intel.byName(lead[1]))));
        }
        return out;
    }

    /** Analyses one intel drive: rolls its quality and turns it into a lead on an intel contract. Returns the lead, or empty when none fits. */
    public static java.util.Optional<Map.Entry<ContractDef, com.wf.wflib.contract.intel.Intel.Quality>> analyze(ServerPlayer player) throws Refused {
        MinecraftServer server = player.getServer();
        Rank rank = ProfileBook.get(server).of(player.getUUID()).rank();
        List<ContractDef> pool = Contracts.all().stream().filter(d -> d.source().equals("intel") && rank.atLeast(d.gate()))
                .filter(d -> !com.wf.wflib.contract.heat.HeatService.restricted(server, player.getUUID(), d.employer())).toList();
        if (pool.isEmpty()) throw new Refused("There is nothing the data could point at");
        ItemStack drive = ItemStack.EMPTY;
        for (ItemStack s : player.getInventory().items) if (s.is(com.wf.wflib.contract.item.PmcItems.INTEL_DRIVE.get())) { drive = s; break; }
        if (drive.isEmpty()) throw new Refused("You carry no intel drive to analyse");
        drive.shrink(1);
        var quality = com.wf.wflib.contract.intel.Intel.roll(new java.util.Random());
        ContractDef def = pool.get(RNG.nextInt(pool.size()));
        var profiles = ProfileBook.get(server);
        profiles.of(player.getUUID()).leads.add(new String[]{def.id().toString(), quality.name()});
        profiles.setDirty();
        return java.util.Optional.of(Map.entry(def, quality));
    }

    public static Session accept(ServerPlayer player, ContractDef def) throws Refused {
        MinecraftServer server = player.getServer();
        com.wf.wflib.contract.intel.Intel.Quality intel = null;
        if (def.source().equals("intel")) {
            for (var lead : leads(server, player.getUUID())) if (lead.getKey().id().equals(def.id())) { intel = lead.getValue(); break; }
            if (intel == null) throw new Refused("You have no intel on that");
        }
        var sessions = SessionBook.get(server);
        Rank rank = ProfileBook.get(server).of(player.getUUID()).rank();
        if (!rank.atLeast(def.gate())) throw new Refused("Needs rank " + def.gate().id() + " (you are " + rank.id() + ")");
        if (sessions.of(player.getUUID()).isPresent()) throw new Refused("You already have a contract: finish it or abandon it first");
        long cool = ProfileBook.get(server).of(player.getUUID()).cooldowns.getOrDefault(def.id().toString(), 0L) - player.serverLevel().getGameTime();
        if (cool > 0) throw new Refused("That job is not on offer to you for another " + (cool / 1200 + 1) + " minutes");
        var party = Parties.membersOf(player.getUUID());
        for (UUID member : party) {
            if (sessions.of(member).isPresent()) throw new Refused("Someone in your group already has a contract");
        }
        ServerLevel level = player.serverLevel();
        var found = SiteFinder.find(level, new SiteFinder.Request(player.position(), def.minDistance(), def.maxDistance(), def.radius(),
                PLAYER_CLEARANCE, true, 24), RNG);
        if (found.isEmpty()) throw new Refused("No suitable ground found at that distance; try again");
        Vec3 centre = found.get();

        List<SiteSpec.Squad> squads = new ArrayList<>();
        for (ContractDef.Squad s : def.squads()) {
            boolean daily = def.id().getPath().startsWith("daily/");
            double tier = com.wf.wflib.contract.difficulty.Difficulty.get(server).tier(server, player.getUUID());
            List<String> units = !s.units().isEmpty() ? s.units() : com.wf.wflib.soldier.api.RandomForces.units(server, s.faction(), com.wf.wflib.contract.difficulty.Tier.grade(tier));
            if (units.isEmpty()) throw new Refused("No soldiers to raise for faction '" + s.faction() + "': save some in the soldier editor, or install a gun mod so a default squad can be made");
            squads.add(new SiteSpec.Squad(units, daily ? s.count() : com.wf.wflib.contract.difficulty.Tier.squadSize(s.count(), tier), s.mode().equals("patrol") ? SoldierOrder.Kind.PATROL : SoldierOrder.Kind.GUARD));
        }
        if (def.buyIn() > 0 && !Money.canAfford(player, def.buyIn())) throw new Refused("The buy-in is " + Money.format(def.buyIn()) + ", and you cannot cover it");
        UUID id = UUID.randomUUID();
        String siteId = "c" + id.toString().replace("-", "").substring(0, 12);
        try {
            Sites.create(level, siteId, centre, new SiteSpec(def.radius(), squads));
        } catch (IllegalArgumentException e) {
            throw new Refused("Could not set up the site: " + e.getMessage());
        }

        BlockPos cache = null;
        for (ContractDef.Objective o : def.objectives()) {
            if (o instanceof ContractDef.RetrieveItem r) {
                Item item = BuiltInRegistries.ITEM.get(r.item());
                if (item == net.minecraft.world.item.Items.AIR) {
                    Sites.get(server, siteId).ifPresent(com.wf.wflib.soldier.api.Site::remove);
                    throw new Refused("This contract names an unknown item: " + r.item());
                }
                cache = place(level, centre, def.radius(), new ItemStack(item, r.count()));
            }
        }

        if (def.buyIn() > 0 && !Money.charge(player, def.buyIn(), "contract.buyin")) {
            Sites.get(server, siteId).ifPresent(com.wf.wflib.soldier.api.Site::remove);
            throw new Refused("The buy-in could not be taken");
        }
        for (ContractDef.Cargo c : def.cargo()) {
            ItemStack goods = new ItemStack(BuiltInRegistries.ITEM.get(c.item()), c.count());
            if (!player.getInventory().add(goods)) player.drop(goods, false);
        }
        hold(level, centre, true);
        long deadline = level.getGameTime() + def.timeLimitMinutes() * 1200L;
        Session session = new Session(id, def.id(), player.getUUID(), party, siteId, level.dimension().location().toString(), centre, deadline, cache);
        sessions.put(session);
        BlockPos marked = BlockPos.containing(centre);
        if (intel != null) {
            int[] off = com.wf.wflib.contract.intel.Intel.offset(intel, new java.util.Random());
            marked = marked.offset(off[0], 0, off[1]);
            var profiles = ProfileBook.get(server);
            var mine = profiles.of(player.getUUID()).leads;
            for (int i = 0; i < mine.size(); i++) if (mine.get(i)[0].equals(def.id().toString())) { mine.remove(i); break; }
            profiles.setDirty();
        }
        MarkerData.get(server).addAndSync(server, MarkerData.side(player), level.dimension().location(), marked, label(def));
        if (!def.cargo().isEmpty()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal("Smuggling traffic reported near " + (int) centre.x + ", " + (int) centre.z + ".").withStyle(ChatFormatting.GOLD), false);
        }
        tell(server, session, Component.literal("Contract accepted: " + def.title() + ". Marked on your map; " + def.timeLimitMinutes() + " minutes.").withStyle(ChatFormatting.GREEN));
        return session;
    }

    /** Keeps the chunks round the site's middle loaded while the job lasts: soldiers are only raised in loaded chunks, so without this a far site stays empty until someone walks up. */
    static void hold(ServerLevel level, Vec3 centre, boolean on) {
        BlockPos at = BlockPos.containing(centre);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setChunkForced((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, on);
    }

    private static String label(ContractDef def) {
        return def.title().length() > 24 ? def.title().substring(0, 24) : def.title();
    }

    /** A barrel on the ground near the middle of the site holding the item. */
    private static BlockPos place(ServerLevel level, Vec3 centre, int radius, ItemStack stack) {
        double angle = RNG.nextDouble() * Math.PI * 2, dist = RNG.nextDouble() * radius * 0.5;
        int x = (int) Math.floor(centre.x + Math.cos(angle) * dist), z = (int) Math.floor(centre.z + Math.sin(angle) * dist);
        level.getChunk(x >> 4, z >> 4);                                  // loads it: the site search generated the middle only
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        level.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof Container c) c.setItem(0, stack);
        return pos;
    }

    /** One look at every contract in progress; called about once a second. */
    public static void tick(MinecraftServer server) {
        var sessions = SessionBook.get(server);
        for (Session s : sessions.all()) {
            ContractDef def = Contracts.get(s.def()).orElse(null);
            if (def == null) {                                      // its definition went away with a datapack
                finish(server, s, null, Engine.Status.TIMED_OUT);
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s.dimension())));
            if (level == null) continue;
            if (HELD.add(s.id())) hold(level, s.centre(), true);       // a job taken before a restart (or before this was added) holds its ground too
            var status = Engine.evaluate(def, s.deadline(), snapshot(server, level, def, s));
            if (status != Engine.Status.ACTIVE) finish(server, s, def, status);
        }
    }

    static Engine.Snapshot snapshot(MinecraftServer server, ServerLevel level, ContractDef def, Session s) {
        Map<Integer, Integer> remaining = new HashMap<>();
        Sites.get(server, s.siteId()).ifPresent(site -> {
            for (int i = 0; i < def.squads().size(); i++) remaining.put(i, site.squadRemaining(i));
        });
        Map<ResourceLocation, Integer> held = new HashMap<>(), atSite = new HashMap<>();
        for (ServerPlayer p : party(server, s)) {
            boolean there = p.level() == level && p.position().distanceToSqr(s.centre().x, p.getY(), s.centre().z) <= (double) def.radius() * def.radius();
            for (ContractDef.Objective o : def.objectives()) {
                if (o instanceof ContractDef.RetrieveItem r) {
                    held.merge(r.item(), p.getInventory().countItem(BuiltInRegistries.ITEM.get(r.item())), Integer::sum);
                } else if (o instanceof ContractDef.DeliverItem d && there) {
                    atSite.merge(d.item(), p.getInventory().countItem(BuiltInRegistries.ITEM.get(d.item())), Integer::sum);
                }
            }
        }
        return new Engine.Snapshot(level.getGameTime(), remaining, held, atSite);
    }

    static List<ServerPlayer> party(MinecraftServer server, Session s) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : s.party()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Ends a contract: pays or penalises, takes the site down. */
    public static void finish(MinecraftServer server, Session s, ContractDef def, Engine.Status status) {
        SessionBook.get(server).remove(s.id());
        HELD.remove(s.id());
        Sites.get(server, s.siteId()).ifPresent(com.wf.wflib.soldier.api.Site::remove);
        ServerLevel siteLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s.dimension())));
        if (siteLevel != null) hold(siteLevel, s.centre(), false);
        removeMarker(server, s);
        var profiles = ProfileBook.get(server);
        long nowTick = server.overworld().getGameTime();
        profiles.of(s.owner()).cooldowns.put(s.def().toString(), nowTick + (status == Engine.Status.COMPLETE ? COOLDOWN_DONE : COOLDOWN_FAILED));
        com.wf.wflib.contract.news.News.add(server, (def == null ? s.def().toString() : def.title()) + ": "
                + (status == Engine.Status.COMPLETE ? "completed" : status == Engine.Status.ABANDONED ? "abandoned" : "failed")
                + " by " + (server.getPlayerList().getPlayer(s.owner()) == null ? "an operator" : server.getPlayerList().getPlayer(s.owner()).getGameProfile().getName()));
        if (status == Engine.Status.COMPLETE && def != null) {
            handIn(server, def, s);
            List<UUID> members = new ArrayList<>(s.party());
            int[] shares = Payout.split(def.money(), members.size(), TEAM_BONUS);
            for (int i = 0; i < members.size(); i++) {
                UUID who = members.get(i);
                Profile p = profiles.of(who);
                Rank before = p.rank();
                p.rep += def.rep();
                p.completed++;
                boolean paid = shares[i] > 0 && Money.pay(server, who, shares[i], "contract.payout");
                if (paid) p.earnings += shares[i];
                ServerPlayer online = server.getPlayerList().getPlayer(who);
                // shortcut: an offline winner with no economy gets no item reward, hand it over on login if it ever matters
                boolean items = !paid && !Money.available() && online != null && giveItems(online, def);
                if (online != null) {
                    online.sendSystemMessage(Component.literal("Contract complete: " + def.title() + ". "
                            + (paid ? "Paid " + Money.format(shares[i]) : items ? "Paid in goods" : "No pay")
                            + ", +" + def.rep() + " reputation.").withStyle(ChatFormatting.GREEN));
                    if (p.rank() != before) online.sendSystemMessage(Component.literal("Promoted to " + p.rank().id() + ".").withStyle(ChatFormatting.GOLD));
                }
            }
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.wf.wflib.contract.api.ContractCompletedEvent(def.id(), def.employer(), members, def.money()));
        } else {
            int penalty = def == null ? 0 : def.penaltyRep();
            Profile p = profiles.of(s.owner());
            p.rep = Math.max(0, p.rep - penalty);
            p.failed++;
            tell(server, s, Component.literal("Contract failed" + (status == Engine.Status.TIMED_OUT ? ": out of time" : status == Engine.Status.ABANDONED ? ": abandoned" : "")
                    + (penalty > 0 ? ". -" + penalty + " reputation." : ".")).withStyle(ChatFormatting.RED));
        }
        profiles.setDirty();
    }

    /** The reward items of a contract, for a server with no economy. False when it lists none. */
    private static boolean giveItems(ServerPlayer player, ContractDef def) {
        for (ContractDef.Cargo c : def.items()) {
            ItemStack goods = new ItemStack(BuiltInRegistries.ITEM.get(c.item()), c.count());
            if (!player.getInventory().add(goods)) player.drop(goods, false);
        }
        return !def.items().isEmpty();
    }

    /** Takes the carried items from the party: the client keeps nothing of the job. */
    private static void handIn(MinecraftServer server, ContractDef def, Session s) {
        for (ContractDef.Objective o : def.objectives()) {
            ResourceLocation id = o instanceof ContractDef.RetrieveItem r ? r.item() : o instanceof ContractDef.DeliverItem d ? d.item() : null;
            if (id == null) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            int left = o instanceof ContractDef.RetrieveItem r2 ? r2.count() : ((ContractDef.DeliverItem) o).count();
            for (ServerPlayer p : party(server, s)) {
                if (left <= 0) break;
                int have = Math.min(left, p.getInventory().countItem(item));
                if (have > 0) {
                    p.getInventory().clearOrCountMatchingItems(st -> st.is(item), have, p.inventoryMenu.getCraftSlots());
                    left -= have;
                }
            }
        }
    }

    /** The owner steps away from the job. A party member may not abandon it for the others. */
    public static void abandon(ServerPlayer player) throws Refused {
        MinecraftServer server = player.getServer();
        Session s = SessionBook.get(server).of(player.getUUID()).orElseThrow(() -> new Refused("You have no contract"));
        if (!s.owner().equals(player.getUUID())) throw new Refused("Only whoever took the contract can abandon it");
        ContractDef def = Contracts.get(s.def()).orElse(null);
        finish(server, s, def, Engine.Status.ABANDONED);
    }

    private static void removeMarker(MinecraftServer server, Session s) {
        ServerPlayer owner = server.getPlayerList().getPlayer(s.owner());
        if (owner == null) return;
        UUID side = MarkerData.side(owner);
        var data = MarkerData.get(server);
        BlockPos at = BlockPos.containing(s.centre());
        String label = Contracts.get(s.def()).map(ContractService::label).orElse("");
        data.of(side).stream().filter(m -> m.label().equals(label) && Math.abs(m.pos().getX() - at.getX()) <= 220 && Math.abs(m.pos().getZ() - at.getZ()) <= 220)
                .map(m -> m.id()).toList().forEach(id -> data.removeAndSync(server, side, id));
    }

    static void tell(MinecraftServer server, Session s, Component message) {
        for (ServerPlayer p : party(server, s)) p.sendSystemMessage(message);
    }

    /** A hunt was sent after this operator (a hook for the war archive). */
    public static void recordHunt(MinecraftServer server, UUID player) {
        com.wf.wflib.contract.ContractSystem.LOGGER.debug("hunt launched against {}", player);
    }

    /** Counts a real player's soldier kill toward their record. */
    public static void recordKill(MinecraftServer server, UUID killer, String team) {
        var book = ProfileBook.get(server);
        book.of(killer).kills.merge(team, 1, Integer::sum);
        book.setDirty();
        com.wf.wflib.contract.ContractSystem.LOGGER.debug("{} killed a soldier of {}", killer, team);
    }
}
