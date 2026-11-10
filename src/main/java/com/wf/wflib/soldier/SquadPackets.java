package com.wf.wflib.soldier;

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
        registrar.playToServer(Command.TYPE, Command.CODEC, Command::handle);
    }

    public static boolean canReceive(ServerPlayer p) {
        return p.connection.hasChannel(Roster.TYPE);
    }

    /** One of the player's soldiers, as the HUD shows it. {@code health} and {@code ammo} are percent (ammo -1: unarmed). */
    public record Member(java.util.UUID uuid, String name, int team, String role, int health, int ammo, int mags, String order, int flags) {
        public static final int DOWNED = 1, FIGHTING = 2, RESUPPLYING = 4;

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
                    | (b.order.kind() == SoldierOrder.Kind.RESUPPLY ? Member.RESUPPLYING : 0);
            out.add(new Member(p.getUUID(), b.name, b.fireTeam, b.role, Math.max(0, Math.min(100, Math.round(p.getHealth() / p.getMaxHealth() * 100))),
                    ammo, mags, b.order.kind().id, flags));
        }
        return new Roster(out);
    }
}
