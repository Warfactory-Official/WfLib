package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.api.Offer;
import com.wf.wflib.soldier.api.Recruiting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * The hire menu's wire format. {@code where} names the source: {@code pda}, or {@code barracks@x,y,z}. The client only asks to see
 * a menu and to hire an offer from it; the server works the offers out again (a PDA carried, a barracks within reach, the source on)
 * and charges.
 */
public final class RecruitPackets {
    private static final double REACH = 8;

    private RecruitPackets() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Offers.TYPE, Offers.CODEC, Offers::handle);
        registrar.playToServer(Request.TYPE, Request.CODEC, Request::handle);
        registrar.playToServer(Hire.TYPE, Hire.CODEC, Hire::handle);
    }

    /** One offer as a menu shows it. */
    public record View(String id, String title, String role, String kit, String gear, String cost, String upkeep, int delay, boolean affordable) {
        static View read(FriendlyByteBuf b) {
            return new View(b.readUtf(64), b.readUtf(48), b.readUtf(24), b.readUtf(96), b.readUtf(96), b.readUtf(64), b.readUtf(64), b.readVarInt(), b.readBoolean());
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 64);
            b.writeUtf(title, 48);
            b.writeUtf(role, 24);
            b.writeUtf(kit, 96);
            b.writeUtf(gear, 96);
            b.writeUtf(cost, 64);
            b.writeUtf(upkeep, 64);
            b.writeVarInt(delay);
            b.writeBoolean(affordable);
        }
    }

    /** What the player can hire from {@code where}; {@code open} asks the client to show the barracks screen. {@code on}: the source is switched on. */
    public record Offers(String where, boolean open, boolean on, int arriving, String header, String note, List<View> list) implements CustomPacketPayload {
        public static final Type<Offers> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "recruit_offers"));
        static final StreamCodec<FriendlyByteBuf, Offers> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeUtf(p.where, 64);
                    b.writeBoolean(p.open);
                    b.writeBoolean(p.on);
                    b.writeVarInt(p.arriving);
                    b.writeUtf(p.header, 120);
                    b.writeUtf(p.note, 120);
                    b.writeCollection(p.list, (buf, v) -> v.write(buf));
                },
                b -> new Offers(b.readUtf(64), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readUtf(120), b.readUtf(120), b.readList(View::read)));

        @Override public Type<Offers> type() { return TYPE; }

        static void handle(Offers packet, IPayloadContext context) {
            context.enqueueWork(() -> com.wf.wflib.soldier.client.RecruitClient.offers(packet));
        }
    }

    public record Request(String where) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "recruit_request"));
        static final StreamCodec<FriendlyByteBuf, Request> CODEC = CustomPacketPayload.codec((p, b) -> b.writeUtf(p.where, 64), b -> new Request(b.readUtf(64)));

        @Override public Type<Request> type() { return TYPE; }

        static void handle(Request r, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer p && !SoldierAi.scripted(p)) send(p, r.where, "", false);
            });
        }
    }

    public record Hire(String where, String offer) implements CustomPacketPayload {
        public static final Type<Hire> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "recruit_hire"));
        static final StreamCodec<FriendlyByteBuf, Hire> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeUtf(p.where, 64);
                    b.writeUtf(p.offer, 64);
                },
                b -> new Hire(b.readUtf(64), b.readUtf(64)));

        @Override public Type<Hire> type() { return TYPE; }

        static void handle(Hire h, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer p) || SoldierAi.scripted(p)) return;
                String note;
                try {
                    Offer o = resolve(p, h.where).stream().filter(x -> x.id().equals(h.offer)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("That is not on offer"));
                    note = Recruits.hire(p, o);
                } catch (IllegalArgumentException e) {
                    note = e.getMessage();
                }
                send(p, h.where, note, false);
            });
        }
    }

    /** The offers {@code where} really has for this player: nothing when the source is off, the PDA is not carried or the barracks is out of reach. */
    static List<Offer> resolve(ServerPlayer p, String where) {
        if (where.equals("pda")) {
            if (!Recruiting.enabled("pda")) return List.of();
            var source = Recruiting.source("pda");
            return source == null ? List.of() : source.offers(p);
        }
        if (!where.startsWith("barracks@") || !Recruiting.enabled("barracks")) return List.of();
        try {
            String[] c = where.substring(9).split(",");
            BlockPos pos = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            var level = p.serverLevel();
            if (p.distanceToSqr(pos.getCenter()) > REACH * REACH || !level.hasChunkAt(pos)) return List.of();
            for (var type : Recruiting.barracks()) if (type.matches(level, pos)) return type.offers(p, level, pos);
        } catch (RuntimeException e) {
            // a malformed place from a client: nothing on offer
        }
        return List.of();
    }

    static void send(ServerPlayer p, String where, String note, boolean open) {
        boolean on = where.equals("pda") ? Recruiting.enabled("pda") : Recruiting.enabled("barracks");
        var server = p.getServer();
        var roster = SoldierRoster.get(server);
        boolean wages = Recruits.wagesOn();
        List<View> views = new ArrayList<>();
        for (Offer o : resolve(p, where)) {
            var unit = roster.units().get(o.variant());
            String cost = o.cost() == com.wf.wflib.soldier.api.Cost.FREE ? "" : o.cost().describe();
            String upkeep = !wages || unit == null ? "" : Recruits.upkeepText(List.of(unit.role()));
            views.add(new View(o.id(), o.title(), unit == null ? "" : unit.role().title, unit == null ? "" : Recruits.kit(unit), unit == null ? "" : Recruits.gear(unit),
                    cost, upkeep.isEmpty() ? "" : upkeep + " / day", o.delaySeconds(), o.cost().affordable(p)));
        }
        PacketDistributor.sendToPlayer(p, new Offers(where, open, on, Recruits.arriving(server, p.getUUID()), Recruits.header(p), note, views));
    }

    /** Using a barracks block opens its hire menu (not while sneaking, so building against one still works). */
    static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || SoldierAi.scripted(p) || p.isShiftKeyDown()) return;
        if (!Recruiting.enabled("barracks") || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        BlockPos pos = e.getPos();
        for (var type : Recruiting.barracks()) {
            if (!type.matches(level, pos)) continue;
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            send(p, "barracks@" + pos.getX() + "," + pos.getY() + "," + pos.getZ(), "", true);
            return;
        }
    }
}
