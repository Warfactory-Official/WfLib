package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The gear screen's wire format. Optional channel. The client asks (open, save or delete a preset, give soldiers gear);
 * the server checks every key against the installed catalogue and every soldier against the owner's roster, and answers
 * with the player's presets and how much of each item the force has in storage.
 */
public final class GearPackets {
    private static final int MAX_JSON = 60000, MAX_TARGETS = 256;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private GearPackets() {}

    static void register(PayloadRegistrar r) {
        r.playToServer(Request.TYPE, Request.CODEC, Request::handle);
        r.playToClient(State.TYPE, State.CODEC, State::handle);
    }

    static void forget() {
        LAST.clear();
    }

    /** Opens the screen on the player's client. */
    public static void open(ServerPlayer p) {
        send(p, "", true, List.of());
    }

    public static void request(String action, JsonObject data) {
        var j = new JsonObject();
        j.addProperty("action", action);
        j.add("data", data);
        PacketDistributor.sendToServer(new Request(j.toString()));
    }

    static void send(ServerPlayer p, String message, boolean open, List<UUID> targets) {
        if (!p.connection.hasChannel(State.TYPE)) {
            p.sendSystemMessage(Component.literal("Install the matching WFLib client to use the gear screen"));
            return;
        }
        var server = p.getServer();
        var j = new JsonObject();
        j.addProperty("message", message);
        j.addProperty("open", open);
        var presets = new JsonArray();
        GearBook.get(server).of(p.getUUID()).values().forEach(x -> presets.add(x.json()));
        j.add("presets", presets);
        j.addProperty("claims", com.wf.wflib.compat.api.WarforgeCompat.factionOfPlayer(p.getUUID()) != null);
        // what the force holds, per catalogue choice; only the ones it has any of
        Stockpile stock = Stockpile.of(server, p.getUUID(), friendlyTeam(server, p));
        var counts = new JsonObject();
        for (var c : SoldierEditor.catalogue(server).all()) {
            int n = switch (c.slot()) {
                case "gun", "grenade" -> stock.countItem(c.stack().getItem());
                default -> stock.count(c.stack());
            };
            if (n > 0 && counts.size() < 3000) counts.addProperty(c.key() + "|" + c.slot(), n);
        }
        j.add("stock", counts);
        var who = new JsonArray();
        for (UUID body : targets) {
            SoldierAi.Bot b = Gear.botOf(p, body);
            if (b == null) continue;
            var t = new JsonObject();
            t.addProperty("id", body.toString());
            t.addProperty("name", b.name);
            var needs = new JsonArray();
            Requisition.missing(server, p.getServer().getPlayerList().getPlayer(body), b).forEach(x -> needs.add(x.proto().getHoverName().getString()));
            t.add("needs", needs);
            who.add(t);
        }
        j.add("targets", who);
        PacketDistributor.sendToPlayer(p, new State(j.toString()));
    }

    /** The side of the player's soldiers, for depot stock: the first of their roster, else "friendly". */
    private static String friendlyTeam(net.minecraft.server.MinecraftServer server, ServerPlayer p) {
        for (SoldierAi.Bot b : Squads.deployed(p.getUUID())) return b.team;
        return "friendly";
    }

    private static List<UUID> ids(JsonObject data) {
        List<UUID> out = new ArrayList<>();
        if (!data.has("ids")) return out;
        for (var e : data.getAsJsonArray("ids")) {
            if (out.size() >= MAX_TARGETS) break;
            try {
                out.add(UUID.fromString(e.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // a bad id is no soldier
            }
        }
        return out;
    }

    public record Request(String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.parse("wflib:soldier_gear"));
        static final StreamCodec<FriendlyByteBuf, Request> CODEC = CustomPacketPayload.codec((p, b) -> b.writeUtf(p.json, 16384), b -> new Request(b.readUtf(16384)));

        @Override
        public Type<Request> type() {
            return TYPE;
        }

        static void handle(Request packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p)) return;
                long now = p.getServer().getTickCount();
                Long last = LAST.put(p.getUUID(), now);
                if (last != null && now - last < 4) return;
                try {
                    var j = JsonParser.parseString(packet.json).getAsJsonObject();
                    String action = j.get("action").getAsString();
                    var data = j.getAsJsonObject("data");
                    var catalogue = SoldierEditor.catalogue(p.getServer());
                    String message = "";
                    boolean open = false;
                    switch (action) {
                        case "open" -> open = true;
                        case "refresh" -> { }
                        case "save" -> {
                            var preset = GearBook.Preset.read(data);
                            Gear.validate(catalogue, preset.want(), preset.role());
                            GearBook.get(p.getServer()).put(p.getUUID(), preset);
                            message = "Saved preset " + preset.name();
                        }
                        case "delete" -> {
                            String name = data.get("name").getAsString();
                            message = GearBook.get(p.getServer()).remove(p.getUUID(), name) ? "Deleted preset " + name : "No such preset";
                        }
                        case "assign" -> {
                            var want = Want.read(data.getAsJsonObject("want"));
                            String role = data.has("role") ? data.get("role").getAsString() : "";
                            int spares = data.has("spares") ? data.get("spares").getAsInt() : -1;
                            int n = Gear.assign(p, ids(data), want, role, spares);
                            message = n == 0 ? "No soldier of yours to give it to"
                                    : n + (n == 1 ? " soldier" : " soldiers") + (want.isEmpty() ? " updated" : " will collect it from storage");
                        }
                        default -> throw new IllegalArgumentException("Unknown gear action");
                    }
                    send(p, message, open, ids(data));
                } catch (RuntimeException e) {
                    send(p, e.getMessage() == null ? "Invalid gear request" : e.getMessage(), false, List.of());
                }
            });
        }
    }

    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.parse("wflib:soldier_gear_state"));
        static final StreamCodec<FriendlyByteBuf, State> CODEC = CustomPacketPayload.codec((p, b) -> b.writeUtf(p.json, MAX_JSON * 4), b -> new State(b.readUtf(MAX_JSON * 4)));

        @Override
        public Type<State> type() {
            return TYPE;
        }

        static void handle(State packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.wf.wflib.soldier.client.GearScreen.receive(packet.json));
        }
    }
}
