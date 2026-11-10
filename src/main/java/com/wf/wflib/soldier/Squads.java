package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierRole;
import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Companions and fire teams: a player's soldiers come back with them, can be told apart, and take orders as a squad, a
 * fire team or one by one. Server thread only.
 */
public final class Squads {
    /** An order's target: a fire team ({@code team} 0-3, -1 for everyone), or one soldier by its profile name. */
    public record Target(int team, String soldier) {
        public static final Target ALL = new Target(-1, "");

        public static Target of(String word) {
            String w = word.toLowerCase(Locale.ROOT);
            if (w.equals("all")) return ALL;
            for (int i = 0; i < Companion.TEAMS.length; i++) if (Companion.TEAMS[i].toLowerCase(Locale.ROOT).equals(w)) return new Target(i, "");
            return new Target(-1, word);
        }
    }

    /** A soldier too far from its owner to follow is brought to them: this far (blocks), and no sooner than this often (ticks). */
    static final double CATCH_UP = 96;
    static final int CATCH_UP_EVERY = 100;
    /** Apart this long (ticks) and a soldier greets its owner on their return. */
    static final int GREET_AFTER = 1200;
    private static final String[] SLOTS = {"head", "chest", "legs", "feet"};

    private Squads() {}

    /** The owner's soldiers that are in the world now. */
    static List<SoldierAi.Bot> deployed(UUID owner) {
        List<SoldierAi.Bot> out = new ArrayList<>();
        for (SoldierAi.Bot b : SoldierAi.soldierBots()) if (owner.equals(b.ownerId) && b.companion != null) out.add(b);
        return out;
    }

    static ServerPlayer player(MinecraftServer server, SoldierAi.Bot b) {
        return server.getPlayerList().getPlayerByName(b.name);
    }

    /** Brings one companion into the world beside its owner. */
    public static ServerPlayer deploy(ServerPlayer owner, Companion c) {
        var level = owner.serverLevel();
        Vec3 at = clearSpot(owner, c.callsign.hashCode() + c.id.hashCode());
        var p = Soldiers.spawn(level, at, owner.getYRot(), c.spec(), owner);
        if (p == null) return null;
        for (var e : c.armour.entrySet()) p.setItemSlot(e.getKey(), e.getValue().copy());
        if (c.role == SoldierRole.HEAVY) {
            p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
            p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(.6);
            p.setHealth(40);
        }
        p.setCustomName(Component.literal(c.callsign));
        p.setCustomNameVisible(true);
        SoldierAi.Bot b = SoldierAi.botOf(p.getGameProfile().getName());
        b.companion = c.id;
        b.fireTeam = c.fireTeam;
        b.range = c.range;
        b.spreadDeg = c.spread;
        b.voice = c.voice;
        b.squad = squadId(owner, c.fireTeam);
        Orders.apply(b, p.position(), validOrder(owner.getServer(), c.order));
        return p;
    }

    private static String squadId(ServerPlayer owner, int team) {
        return owner.getUUID().toString().substring(0, 8) + "/" + Companion.TEAMS[team];
    }

    /** A saved patrol or guard of a territory that is gone becomes "follow". */
    private static SoldierOrder validOrder(MinecraftServer server, SoldierOrder o) {
        if (o.kind().needsRegion && TerritoryBook.get(server).get(o.region()) == null) return SoldierOrder.FOLLOW;
        return o;
    }

    /** A free spot near the owner, a different one for each soldier. */
    private static Vec3 clearSpot(ServerPlayer owner, int seed) {
        for (int i = 0; i < 8; i++) {
            Vec3 at = SoldierBrain.slot(owner.position(), seed + i * 53);
            AABB box = new AABB(at.x - .3, owner.getY(), at.z - .3, at.x + .3, owner.getY() + 1.8, at.z + .3);
            if (owner.serverLevel().noCollision(box)) return new Vec3(at.x, owner.getY(), at.z);
        }
        return owner.position();
    }

    /** Brings the owner's whole roster into the world (login). Soldiers already out are left alone. */
    public static void deployAll(ServerPlayer owner) {
        var out = deployed(owner.getUUID());
        for (Companion c : SquadBook.get(owner.getServer()).of(owner.getUUID())) {
            if (out.stream().anyMatch(b -> c.id.equals(b.companion))) continue;
            if (deploy(owner, c) == null) break;                 // the server's soldier cap
        }
    }

    /** Records what each soldier was last told and takes them out of the world (logout, shutdown). */
    public static void stow(MinecraftServer server, UUID owner) {
        var book = SquadBook.get(server);
        for (SoldierAi.Bot b : deployed(owner)) {
            Companion c = book.find(owner, b.companion);
            if (c != null) {
                c.order = b.order;
                book.setDirty();
            }
            ServerPlayer p = player(server, b);
            if (p != null) Soldiers.remove(p);
        }
    }

    public static void stowAll(MinecraftServer server) {
        for (UUID owner : SoldierAi.soldierBots().stream().filter(b -> b.companion != null).map(b -> b.ownerId).distinct().toList()) stow(server, owner);
    }

    /** An unowned friendly soldier joins the owner's squad. */
    public static Companion recruit(ServerPlayer owner, ServerPlayer soldier) {
        SoldierAi.Bot b = SoldierAi.botOf(soldier.getGameProfile().getName());
        if (b == null || !b.soldier || b.huntsPlayers || b.companion != null
                || b.ownerId != null && !b.ownerId.equals(owner.getUUID())) throw new IllegalArgumentException("Not a soldier you can recruit");
        var book = SquadBook.get(owner.getServer());
        String name = soldier.getGameProfile().getName();
        Companion c = new Companion(UUID.randomUUID(), name.contains("_") ? name.substring(0, name.lastIndexOf('_')) : name, b.team,
                SoldierRole.tactics(b.role), b.gun);
        c.grenades = b.grenades;
        c.spares = b.mags;
        c.range = (int) b.range;
        c.spread = b.spreadDeg;
        c.voice = b.voice;
        for (String slot : SLOTS) {
            var piece = soldier.getItemBySlot(SoldierCatalogue.slot(slot)).copy();
            if (!piece.isEmpty()) c.armour.put(SoldierCatalogue.slot(slot), piece);
        }
        c.fireTeam = book.emptiestTeam(owner.getUUID());
        book.add(owner.getUUID(), c);
        b.ownerId = owner.getUUID();
        b.companion = c.id;
        b.fireTeam = c.fireTeam;
        b.squad = squadId(owner, c.fireTeam);
        Orders.apply(b, soldier.position(), SoldierOrder.FOLLOW);
        return c;
    }

    /** Takes a soldier off the roster; it stays where it stands, holding, and belongs to no one. */
    public static void dismiss(ServerPlayer owner, String soldierName) {
        SoldierAi.Bot b = SoldierAi.botOf(soldierName);
        if (b == null || !owner.getUUID().equals(b.ownerId) || b.companion == null) throw new IllegalArgumentException("Not one of your soldiers");
        SquadBook.get(owner.getServer()).remove(owner.getUUID(), b.companion);
        b.companion = null;
        b.ownerId = null;
        ServerPlayer p = player(owner.getServer(), b);
        Orders.apply(b, p == null ? Vec3.ZERO : p.position(), SoldierOrder.of(SoldierOrder.Kind.HOLD));
    }

    public static void setFireTeam(ServerPlayer owner, String soldierName, int team) {
        SoldierAi.Bot b = SoldierAi.botOf(soldierName);
        if (b == null || !owner.getUUID().equals(b.ownerId) || b.companion == null) throw new IllegalArgumentException("Not one of your soldiers");
        var book = SquadBook.get(owner.getServer());
        Companion c = book.find(owner.getUUID(), b.companion);
        c.fireTeam = Math.floorMod(team, Companion.TEAMS.length);
        b.fireTeam = c.fireTeam;
        b.squad = squadId(owner, c.fireTeam);
        book.setDirty();
    }

    /** The owner's deployed soldiers a target names. */
    static List<SoldierAi.Bot> select(UUID owner, Target t) {
        List<SoldierAi.Bot> out = new ArrayList<>();
        for (SoldierAi.Bot b : deployed(owner)) {
            if (!t.soldier().isEmpty() ? b.name.equalsIgnoreCase(t.soldier()) : (t.team() < 0 || b.fireTeam == t.team())) out.add(b);
        }
        return out;
    }

    /**
     * Gives the target the order, one soldier answers by voice, and the roster remembers it.
     * A point order spreads the soldiers around the point (the same formation the squad AI uses).
     *
     * @return the soldiers that took it
     */
    public static List<SoldierAi.Bot> order(ServerPlayer owner, Target target, SoldierOrder order) {
        var server = owner.getServer();
        var members = select(owner.getUUID(), target);
        if (members.isEmpty()) throw new IllegalArgumentException("No soldiers answer to that");
        if (order.kind().needsRegion && TerritoryBook.get(server).get(order.region()) == null)
            throw new IllegalArgumentException("Unknown territory: " + order.region());
        var book = SquadBook.get(server);
        Vec3 forward = new Vec3(0, 0, 1);
        if (order.pos() != null) {
            Vec3 centre = Vec3.ZERO;
            int n = 0;
            for (SoldierAi.Bot b : members) {
                ServerPlayer p = player(server, b);
                if (p != null) {
                    centre = centre.add(p.position());
                    n++;
                }
            }
            if (n > 0) {
                Vec3 flat = order.pos().subtract(centre.scale(1.0 / n)).multiply(1, 0, 1);
                if (flat.lengthSqr() > 1e-6) forward = flat.normalize();
            }
        }
        List<SoldierAi.Bot> taken = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            SoldierAi.Bot b = members.get(i);
            ServerPlayer p = player(server, b);
            if (p == null) continue;
            SoldierOrder mine = order;
            if (order.pos() != null && members.size() > 1) mine = SoldierOrder.at(order.kind(), BotSquad.formation(order.pos(), forward, b.role, i));
            Orders.apply(b, p.position(), mine);
            Companion c = book.find(owner.getUUID(), b.companion);
            if (c != null) c.order = b.order;
            taken.add(b);
        }
        book.setDirty();
        taken.stream().sorted(Comparator.comparingDouble(b -> {
            ServerPlayer p = player(server, b);
            return p == null ? Double.MAX_VALUE : p.distanceToSqr(owner);
        })).anyMatch(b -> {
            ServerPlayer p = player(server, b);
            return p != null && SoldierVoices.say(p, b, order.kind().ack);   // the nearest one that has the line answers
        });
        return taken;
    }

    /** A companion died: it comes off the roster, and its owner is told. */
    static void fell(ServerPlayer soldier) {
        SoldierAi.Bot b = SoldierAi.botOf(soldier.getGameProfile().getName());
        if (b == null || b.companion == null || b.ownerId == null) return;
        var server = soldier.getServer();
        SquadBook.get(server).remove(b.ownerId, b.companion);
        ServerPlayer owner = server.getPlayerList().getPlayer(b.ownerId);
        if (owner != null) owner.sendSystemMessage(Component.literal(soldier.getGameProfile().getName() + " (" + Companion.TEAMS[b.fireTeam] + ") was killed."));
        b.companion = null;
    }

    /** A companion that has been left far behind, or in another dimension, is brought to its owner. */
    static void catchUp(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b, long now) {
        if (b.companion == null || b.ownerId == null || now < b.nextCatchUp) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(b.ownerId);
        if (owner == null || !owner.isAlive() || !b.threats.isEmpty()) return;
        boolean far = owner.level() != p.level() || owner.distanceToSqr(p) > CATCH_UP * CATCH_UP;
        if (!far) return;
        b.nextCatchUp = now + CATCH_UP_EVERY;
        Vec3 at = clearSpot(owner, b.name.hashCode());
        p.teleportTo(owner.serverLevel(), at.x, at.y, at.z, owner.getYRot(), 0);
        p.connection.resetPosition();
    }

    /** Spawns a saved variant beside the owner and puts it on their roster (editor permission, like the eggs). */
    public static Companion enlist(ServerPlayer owner, String variant) {
        SoldierEditor.require(owner);
        var loadout = SoldierEditor.loadout(owner.getServer(), variant);
        if (loadout.faction().hostile()) throw new IllegalArgumentException("That variant belongs to a hostile faction");
        var p = SoldierEditor.spawn(owner, variant, clearSpot(owner, variant.hashCode()), owner.getYRot());
        return recruit(owner, p);
    }

    /** Re-equips the target from a saved variant: its gun, grenades, armour and role. The soldier is re-armed on its next tick. */
    public static int rekit(ServerPlayer owner, Target target, String variant) {
        var server = owner.getServer();
        SoldierEditor.require(owner);
        var loadout = SoldierEditor.loadout(server, variant);
        var book = SquadBook.get(server);
        int n = 0;
        for (SoldierAi.Bot b : select(owner.getUUID(), target)) {
            ServerPlayer p = player(server, b);
            Companion c = book.find(owner.getUUID(), b.companion);
            if (p == null || c == null) continue;
            p.getInventory().clearContent();
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                p.setItemSlot(slot, loadout.armour().getOrDefault(slot, net.minecraft.world.item.ItemStack.EMPTY).copy());
            }
            var spec = loadout.spec();
            b.gun = spec.gun();
            b.mags = spec.spares();
            b.grenades = spec.grenades();
            b.role = spec.role().id;
            b.burst = spec.role().burst;
            b.pause = spec.role().pause;
            b.spreadDeg = loadout.unit().spread();
            b.range = loadout.unit().range();
            b.arm = true;                                    // the AI arms it on its next tick and captures the new kit
            b.kit = null;
            c.role = spec.role();
            c.gun = spec.gun();
            c.grenades = spec.grenades();
            c.spares = spec.spares();
            c.range = loadout.unit().range();
            c.spread = loadout.unit().spread();
            c.armour.clear();
            c.armour.putAll(loadout.armour());
            n++;
        }
        book.setDirty();
        return n;
    }

    /** What the owner is looking at, as a point to send soldiers to. */
    public static Vec3 aim(ServerPlayer owner, double range) {
        var hit = owner.pick(range, 1f, false);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) throw new IllegalArgumentException("Look at a spot within " + (int) range + " blocks");
        return hit.getLocation();
    }

    /** A soldier says hello when its owner turns up again after a minute or more apart (and on being deployed beside them). */
    static void greet(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b, long now) {
        if (b.ownerId == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(b.ownerId);
        if (owner == null || owner.level() != p.level() || owner.distanceToSqr(p) > 36) return;
        if (now - b.ownerNear > GREET_AFTER && b.threats.isEmpty()) SoldierVoices.say(p, b, "greet");
        b.ownerNear = now;
    }
}
