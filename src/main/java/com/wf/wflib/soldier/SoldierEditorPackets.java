package com.wf.wflib.soldier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.*;

public final class SoldierEditorPackets {
    private static final Map<UUID,Long> LAST=new HashMap<>();
    private SoldierEditorPackets() {}
    static void register(PayloadRegistrar r) { r.playToServer(Request.TYPE,Request.CODEC,Request::handle); r.playToClient(State.TYPE,State.CODEC,State::handle); }
    static void forget() { LAST.clear(); }
    public static void request(String action,JsonObject data,int revision) {
        var j=new JsonObject(); j.addProperty("action",action); j.addProperty("revision",revision); j.add("data",data);
        PacketDistributor.sendToServer(new Request(j.toString()));
    }
    static void send(ServerPlayer p,String message,boolean open) {
        if (!p.connection.hasChannel(State.TYPE)) { p.sendSystemMessage(Component.literal("Install the matching WFLib client to use the soldier editor")); return; }
        var j=SoldierRoster.get(p.getServer()).json(); j.addProperty("message",message); j.addProperty("open",open); j.addProperty("medical",com.wf.wflib.soldier.api.SoldierServices.medical().present());
        if (open) j.add("catalogue",SoldierEditor.catalogue(p.getServer()).keys());
        PacketDistributor.sendToPlayer(p,new State(j.toString()));
    }
    public record Request(String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.parse("wflib:soldier_edit"));
        static final StreamCodec<FriendlyByteBuf,Request> CODEC=CustomPacketPayload.codec((p,b)->b.writeUtf(p.json,24576),b->new Request(b.readUtf(24576)));
        @Override public Type<Request> type() { return TYPE; }
        static void handle(Request packet,IPayloadContext ctx) { ctx.enqueueWork(()->{
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            try {
                SoldierEditor.require(p); long now=p.getServer().getTickCount();
                Long last=LAST.put(p.getUUID(),now); if (last!=null && now-last<4) { send(p,"Please wait a moment between actions",false); return; }
                var j=JsonParser.parseString(packet.json).getAsJsonObject(); String action=j.get("action").getAsString();
                var data=j.getAsJsonObject("data"); var r=SoldierRoster.get(p.getServer());
                if (action.equals("open")) { send(p,"",true); return; }
                if (j.get("revision").getAsInt()!=r.revision()) { send(p,"Roster changed elsewhere; review it and retry",false); return; }
                String message;
                switch(action) {
                    case "save", "egg", "spawn" -> {
                        var u=SoldierTemplate.read(data.getAsJsonObject("unit")); SoldierEditor.save(p.getServer(),u);
                        message="Saved "+u.name();
                        if (action.equals("egg")) { var stack=SoldierEditorEgg.egg(u); if (!p.getInventory().add(stack)) p.drop(stack,false); message="Spawn egg added to inventory"; }
                        if (action.equals("spawn")) message="Deployed "+SoldierEditor.spawnNear(p,u.id(),data.get("count").getAsInt())+" soldiers";
                    }
                    case "delete_unit" -> { r.removeUnit(data.get("id").getAsString()); message="Variant removed; its eggs are disabled"; }
                    case "save_faction" -> { SoldierEditor.faction(p.getServer(),new SoldierRoster.Faction(data.get("id").getAsString(),data.get("name").getAsString(),data.get("hostile").getAsBoolean())); message="Faction saved"; }
                    case "delete_faction" -> { String id=data.get("id").getAsString(); r.removeFaction(id); SoldierEditor.clear(p.getServer(),id); message="Faction, variants and deployed soldiers removed"; }
                    case "spawn_faction" -> message="Deployed "+SoldierEditor.spawnFaction(p,data.get("id").getAsString(),data.get("count").getAsInt())+" faction soldiers";
                    case "clear_faction" -> message="Removed "+SoldierEditor.clear(p.getServer(),data.get("id").getAsString())+" deployed soldiers";
                    default -> throw new IllegalArgumentException("Unknown editor action");
                }
                send(p,message,false);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().debug("Soldier editor action rejected for {}",p.getUUID(),e);
                if (p.isCreative()) send(p,e.getMessage()==null ? "Invalid editor request" : e.getMessage(),false);
                else p.sendSystemMessage(Component.literal("The soldier editor requires Creative mode"));
            }
        }); }
    }
    private static byte[] compress(String value) {
        byte[] raw=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(raw.length>2097152)throw new IllegalArgumentException("Soldier roster exceeds 2 MiB");
        try {
            var bytes=new java.io.ByteArrayOutputStream();
            try(var zip=new java.util.zip.GZIPOutputStream(bytes)){zip.write(raw);}
            byte[] compressed=bytes.toByteArray();
            if(compressed.length>524288)throw new IllegalArgumentException("Soldier roster exceeds packet budget");
            return compressed;
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static String expand(byte[] value) {
        try(var zip=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(value))){
            byte[] raw=zip.readNBytes(2097153);
            if(raw.length>2097152)throw new IllegalArgumentException("Soldier roster exceeds 2 MiB");
            return new String(raw,java.nio.charset.StandardCharsets.UTF_8);
        }catch(java.io.IOException e){throw new IllegalArgumentException("Invalid soldier roster packet",e);}
    }
    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.parse("wflib:soldier_roster"));
        // Large pack catalogues share many ID prefixes; compression keeps the response below the game payload limit.
        static final StreamCodec<FriendlyByteBuf,State> CODEC=CustomPacketPayload.codec((p,b)->b.writeByteArray(compress(p.json)),b->new State(expand(b.readByteArray(524288))));
        @Override public Type<State> type() { return TYPE; }
        static void handle(State packet,IPayloadContext ctx) { ctx.enqueueWork(()->com.wf.wflib.soldier.client.SoldierEditorScreen.receive(packet.json)); }
    }
}
