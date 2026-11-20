package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.Soldiers;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierServices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * The squad's wire format. Optional channel: a client without WFLib's squad UI still joins; it commands with {@code /squad}.
 * The client only ever asks; the server decides (ownership, range, whether the territory exists).
 */
public final class SquadPackets {
    /** How far from the player a commanded point may be (blocks): a bound on what a client can name. */
    static final double MAX_RANGE = 256;

    private SquadPackets() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Roster.TYPE, Roster.CODEC, Roster::handle);
        registrar.playToClient(Echo.TYPE, Echo.CODEC, Echo::handle);
        registrar.playToClient(Territories.TYPE, Territories.CODEC, Territories::handle);
        registrar.playToServer(Command.TYPE, Command.CODEC, Command::handle);
        registrar.playToServer(Manage.TYPE, Manage.CODEC, Manage::handle);
    }

    public static boolean canReceive(ServerPlayer p) {
        return p.connection.hasChannel(Roster.TYPE);
    }

    /** One of the player's soldiers, as the HUD shows it. {@code health} and {@code ammo} are percent (ammo -1: unarmed). */
    public record Member(java.util.UUID uuid, String name, int team, String role, int health, int ammo, int mags, String order, int flags) {
        public static final int DOWNED = 1, FIGHTING = 2, RESUPPLYING = 4, LAMP = 8;

        static Member read(FriendlyByteBuf b) {
            return new Member(b.readUUID(), b.readUtf(32), b.readByte(), b.readUtf(16), b.readByte() & 0xFF, b.readByte(), b.readByte() & 0xFF,
                    b.readUtf(64), b.readByte());
        }

        void write(FriendlyByteBuf b) {
            b.writeUUID(uuid);
            b.writeUtf(name, 32);
            b.writeByte(team);
            b.writeUtf(role, 16);
            b.writeByte(health);
            b.writeByte(ammo);
            b.writeByte(Math.min(mags, 255));
            b.writeUtf(order, 64);
            b.writeByte(flags);
        }
    }

    /** The player's roster and what each soldier is doing; sent about twice a second while they have soldiers. */
    public record Roster(List<Member> members) implements CustomPacketPayload {
        public static final Type<Roster> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "squad_roster"));
        static final StreamCodec<FriendlyByteBuf, Roster> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeCollection(p.members, (b, m) -> m.write(b)),
                buf -> new Roster(buf.readList(Member::read)));

        @Override public Type<Roster> type() { return TYPE; }

        static void handle(Roster packet, IPayloadContext context) {
            context.enqueueWork(() -> com.wf.wflib.soldier.client.SquadClient.roster(packet.members));
        }
    }

    /** Who took an order and where it points: the client draws the marker and the labels over the soldiers. */
    public record Echo(String kind, String label, List<Integer> entities, boolean hasPos, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Echo> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "squad_echo"));
        static final StreamCodec<FriendlyByteBuf, Echo> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeUtf(p.kind, 16);
                    b.writeUtf(p.label, 64);
                    b.writeCollection(p.entities, FriendlyByteBuf::writeVarInt);
                    b.writeBoolean(p.hasPos);
                    b.writeDouble(p.x);
                    b.writeDouble(p.y);
                    b.writeDouble(p.z);
                },
                b -> new Echo(b.readUtf(16), b.readUtf(64), b.readList(FriendlyByteBuf::readVarInt), b.readBoolean(), b.readDouble(), b.readDouble(), b.readDouble()));

        @Override public Type<Echo> type() { return TYPE; }

        static void handle(Echo packet, IPayloadContext context) {
            context.enqueueWork(() -> com.wf.wflib.soldier.client.SquadClient.echo(packet));
        }
    }

    /** An order from the wheel or the quick key. {@code target} is "all", a fire team, or one soldier's name. */
    public record Command(String target, String kind, boolean hasPos, double x, double y, double z, String region) implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "squad_command"));
        static final StreamCodec<FriendlyByteBuf, Command> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeUtf(p.target, 32);
                    b.writeUtf(p.kind, 16);
                    b.writeBoolean(p.hasPos);
                    b.writeDouble(p.x);
                    b.writeDouble(p.y);
                    b.writeDouble(p.z);
                    b.writeUtf(p.region, 32);
                },
                b -> new Command(b.readUtf(32), b.readUtf(16), b.readBoolean(), b.readDouble(), b.readDouble(), b.readDouble(), b.readUtf(32)));

        @Override public Type<Command> type() { return TYPE; }

        static void handle(Command c, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer p && !SoldierAi.scripted(p)) apply(p, c);
            });
        }
    }

    /** Validates what a client asked for and carries it out. Everything the client said is a request. */
    static void apply(ServerPlayer p, Command c) {
        try {
            var kind = SoldierOrder.Kind.of(c.kind());
            if (kind == null) throw new IllegalArgumentException("Unknown order");
            Vec3 pos = c.hasPos() ? new Vec3(c.x(), c.y(), c.z()) : null;
            if (pos != null && (!Double.isFinite(pos.lengthSqr()) || pos.distanceToSqr(p.position()) > MAX_RANGE * MAX_RANGE))
                throw new IllegalArgumentException("That is too far away to order");
            if (pos != null && !p.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(pos))) throw new IllegalArgumentException("That is not loaded");
            SoldierOrder order;
            if (kind.needsRegion) {
                String region = c.region();
                if (region.isBlank()) {                       // "patrol here": the territory at the aim point, else at the player
                    var here = TerritoryBook.get(p.getServer()).at(p.level().dimension().location().toString(), pos != null ? pos : p.position());
                    if (here.isEmpty()) throw new IllegalArgumentException("You are not inside a territory");
                    region = here.get(0).id();
                }
                order = SoldierOrder.in(kind, region);
            } else if (kind.needsPos) {
                if (pos == null) throw new IllegalArgumentException(kind.title + " needs a point: look at one");
                order = SoldierOrder.at(kind, pos);
            } else {
                order = SoldierOrder.of(kind);
            }
            var taken = Squads.order(p, Squads.Target.of(c.target()), order);
            List<Integer> ids = new ArrayList<>();
            for (var b : taken) {
                var sp = Squads.player(p.getServer(), b);
                if (sp != null) ids.add(sp.getId());
            }
            PacketDistributor.sendToPlayer(p, new Echo(kind.id, order.label(), ids, order.pos() != null, order.pos() == null ? 0 : order.pos().x,
                    order.pos() == null ? 0 : order.pos().y, order.pos() == null ? 0 : order.pos().z));
        } catch (IllegalArgumentException e) {
            p.displayClientMessage(Component.literal(e.getMessage()), true);
        }
    }

    /** One territory as the management screen lists it. {@code garrison} is the target size (-1: raises none); {@code mine} how many of the player's soldiers are assigned to it. */
    public record TerritoryInfo(String id, String name, String dimension, int x, int y, int z, int radius, int garrison, String mode, int respawn,
                                String units, int alive, int mine) {
        static TerritoryInfo read(FriendlyByteBuf b) {
            return new TerritoryInfo(b.readUtf(32), b.readUtf(48), b.readUtf(128), b.readInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readVarInt() - 1,
                    b.readUtf(16), b.readVarInt(), b.readUtf(256), b.readVarInt(), b.readVarInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 32);
            b.writeUtf(name, 48);
            b.writeUtf(dimension, 128);
            b.writeInt(x);
            b.writeInt(y);
            b.writeInt(z);
            b.writeVarInt(radius);
            b.writeVarInt(garrison + 1);
            b.writeUtf(mode, 16);
            b.writeVarInt(respawn);
            b.writeUtf(units, 256);
            b.writeVarInt(alive);
            b.writeVarInt(mine);
        }
    }

    /** The world's territories and the saved variants a garrison can be raised from; {@code canEdit} says whether this player may change them. */
    public record Territories(boolean canEdit, List<String> variants, List<TerritoryInfo> list) implements CustomPacketPayload {
        public static final Type<Territories> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "squad_territories"));
        static final StreamCodec<FriendlyByteBuf, Territories> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeBoolean(p.canEdit);
                    b.writeCollection(p.variants, (buf, v) -> buf.writeUtf(v, 64));
                    b.writeCollection(p.list, (buf, t) -> t.write(buf));
                },
                b -> new Territories(b.readBoolean(), b.readList(buf -> buf.readUtf(64)), b.readList(TerritoryInfo::read)));

        @Override public Type<Territories> type() { return TYPE; }

        static void handle(Territories packet, IPayloadContext context) {
            context.enqueueWork(() -> com.wf.wflib.soldier.client.SquadClient.territories(packet));
        }
    }

    /**
     * A request from the management screen. {@code action}: list, order, lamp (headlamp on when {@code n} is 1), move (into fire team {@code n} before its {@code m}-th member), team, dismiss (acting on {@code soldiers}); garrison,
     * territory_new, territory_delete (map-maker only, naming {@code region}). {@code kind}/{@code n}/{@code m}/{@code units} are the action's arguments.
     */
    public record Manage(String action, List<java.util.UUID> soldiers, String kind, String region, String units, int n, int m) implements CustomPacketPayload {
        public static final Type<Manage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "squad_manage"));
        static final StreamCodec<FriendlyByteBuf, Manage> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeUtf(p.action, 16);
                    b.writeCollection(p.soldiers, (buf, u) -> buf.writeUUID(u));
                    b.writeUtf(p.kind, 16);
                    b.writeUtf(p.region, 32);
                    b.writeUtf(p.units, 256);
                    b.writeVarInt(p.n);
                    b.writeVarInt(p.m);
                },
                b -> {
                    String action = b.readUtf(16);
                    int count = b.readVarInt();
                    if (count < 0 || count > b.readableBytes() / 16) throw new io.netty.handler.codec.DecoderException("More soldiers than the packet holds");
                    List<java.util.UUID> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) ids.add(b.readUUID());
                    return new Manage(action, ids, b.readUtf(16), b.readUtf(32), b.readUtf(256), b.readVarInt(), b.readVarInt());
                });

        @Override public Type<Manage> type() { return TYPE; }

        static void handle(Manage m, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer p && !SoldierAi.scripted(p)) manage(p, m);
            });
        }
    }

    /** The player's soldiers a request names; anything that is not one of theirs is ignored. */
    private static List<SoldierAi.Bot> mine(ServerPlayer p, List<java.util.UUID> ids) {
        java.util.Set<java.util.UUID> want = new java.util.HashSet<>(ids);
        List<SoldierAi.Bot> out = new ArrayList<>();
        for (var b : Squads.deployed(p.getUUID())) {
            var sp = Squads.player(p.getServer(), b);
            if (sp != null && want.contains(sp.getUUID())) out.add(b);
        }
        return out;
    }

    /** Carries out a management request. The client only asks: ownership and permission are checked here. */
    static void manage(ServerPlayer p, Manage m) {
        try {
            var server = p.getServer();
            switch (m.action()) {
                case "list" -> { }
                case "order" -> {
                    var kind = SoldierOrder.Kind.of(m.kind());
                    if (kind == null || kind.needsPos) throw new IllegalArgumentException("Point at the target with the wheel for that order");
                    var order = kind.needsRegion ? SoldierOrder.in(kind, m.region()) : SoldierOrder.of(kind);
                    var taken = Squads.order(p, mine(p, m.soldiers()), order);
                    p.displayClientMessage(Component.literal(order.label() + ": " + taken.size()), true);
                }
                case "lamp" -> Squads.lamp(p, mine(p, m.soldiers()), m.n() != 0);
                case "move" -> Squads.move(p, mine(p, m.soldiers()), m.n(), m.m());
                case "team" -> {
                    for (var b : mine(p, m.soldiers())) Squads.setFireTeam(p, b.name, m.n());
                }
                case "dismiss" -> {
                    for (var b : mine(p, m.soldiers())) Squads.dismiss(p, b.name);
                }
                case "garrison", "territory_new", "territory_delete" -> {
                    if (!p.hasPermissions(2)) throw new IllegalArgumentException("Only operators edit territories");
                    edit(p, m);
                }
                default -> throw new IllegalArgumentException("Unknown request");
            }
        } catch (IllegalArgumentException e) {
            p.displayClientMessage(Component.literal(e.getMessage()), true);
        }
        PacketDistributor.sendToPlayer(p, territories(p));
    }

    private static void edit(ServerPlayer p, Manage m) {
        var book = TerritoryBook.get(p.getServer());
        switch (m.action()) {
            case "territory_delete" -> book.remove(m.region());
            case "territory_new" -> {
                String id = m.region();
                for (int i = 1; id.isEmpty() || book.get(id) != null; i++) id = "territory_" + i;
                var level = p.serverLevel();
                book.put(new Territory(id, id, "", level.dimension().location().toString(), p.position(), Math.max(2, Math.min(Territory.MAX_RADIUS, m.n())),
                        level.getMinBuildHeight(), level.getMaxBuildHeight(), List.of()));
            }
            default -> {
                Territory t = book.get(m.region());
                if (t == null) throw new IllegalArgumentException("Unknown territory: " + m.region());
                List<String> units = m.units().isEmpty() ? List.of() : java.util.Arrays.asList(m.units().split(","));
                var known = SoldierRoster.get(p.getServer()).units().keySet();
                for (String u : units) if (!known.contains(u)) throw new IllegalArgumentException("Unknown saved variant: " + u);
                book.put(t.withGarrison(m.n() <= 0 && m.kind().isEmpty() ? null : new Garrison(m.n(), units, SoldierOrder.Kind.of(m.kind()), m.m())));
            }
        }
    }

    static Territories territories(ServerPlayer p) {
        var server = p.getServer();
        List<TerritoryInfo> out = new ArrayList<>();
        var squad = Squads.deployed(p.getUUID());
        for (Territory t : TerritoryBook.get(server).all().values()) {
            var g = t.garrison();
            int assigned = (int) squad.stream().filter(b -> b.order.region().equals(t.id())).count();
            out.add(new TerritoryInfo(t.id(), t.name(), t.dimension(), (int) t.centre().x, (int) t.centre().y, (int) t.centre().z, t.radius(),
                    g == null ? -1 : g.count(), g == null ? "" : g.mode().id, g == null ? 0 : g.respawnSeconds(), g == null ? "" : String.join(",", g.units()),
                    Garrisons.alive(server, t.id()).size(), assigned));
        }
        return new Territories(p.hasPermissions(2), new ArrayList<>(SoldierRoster.get(server).units().keySet()), out);
    }

    /** What the HUD shows for the player's soldiers. */
    static Roster snapshot(ServerPlayer owner) {
        List<Member> out = new ArrayList<>();
        for (var b : Squads.deployed(owner.getUUID())) {
            var p = Squads.player(owner.getServer(), b);
            if (p == null) continue;
            int ammo = -1, mags = 0;
            if (SoldierServices.hasWeapon()) {
                var a = SoldierServices.weapon().ammo(p);
                if (a != null) {
                    ammo = a.capacity() <= 0 ? 0 : Math.min(100, a.left() * 100 / a.capacity());
                    mags = a.spare();
                }
            }
            int flags = (b.health.downed() ? Member.DOWNED : 0) | (!b.threats.isEmpty() ? Member.FIGHTING : 0)
                    | (b.order.kind() == SoldierOrder.Kind.RESUPPLY ? Member.RESUPPLYING : 0) | (b.lamp ? Member.LAMP : 0);
            out.add(new Member(p.getUUID(), Soldiers.displayName(p), b.fireTeam, b.role, Math.max(0, Math.min(100, Math.round(p.getHealth() / p.getMaxHealth() * 100))),
                    ammo, mags, b.order.label(), flags));
        }
        return new Roster(out);
    }
}
