package com.wf.wflib.round;

import com.wf.wflib.round.api.RoundEnd;
import com.wf.wflib.round.api.RoundEnded;
import com.wf.wflib.round.api.RoundPierce;
import com.wf.wflib.round.api.RoundRicochet;
import com.wf.wflib.round.api.RoundSpawn;
import com.wf.wflib.round.api.RoundSurface;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;

/** Spawns grouped by preset on the wire. */
public record RoundPacket(List<RoundSpawn> spawns, List<RoundEnded> ends, List<RoundPierce> pierces, List<RoundRicochet> ricochets)
        implements CustomPacketPayload {

    public static final Type<RoundPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "rounds"));

    private static final RoundEnd[] ENDS = RoundEnd.values();
    private static final RoundSurface[] SURFACES = RoundSurface.values();

    public static final StreamCodec<ByteBuf, RoundPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> pkt.write(new FriendlyByteBuf(buf)), buf -> read(new FriendlyByteBuf(buf)));

    private void write(FriendlyByteBuf buf) {
        Map<ResourceLocation, List<RoundSpawn>> groups = new HashMap<>();
        for (RoundSpawn s : this.spawns) {
            groups.computeIfAbsent(s.preset(), k -> new ArrayList<>()).add(s);
        }
        buf.writeVarInt(groups.size());
        for (Map.Entry<ResourceLocation, List<RoundSpawn>> g : groups.entrySet()) {
            buf.writeResourceLocation(g.getKey());
            buf.writeVarInt(g.getValue().size());
            for (RoundSpawn s : g.getValue()) {
                buf.writeLong(s.key());
                buf.writeDouble(s.x());
                buf.writeDouble(s.y());
                buf.writeDouble(s.z());
                buf.writeDouble(s.vx());
                buf.writeDouble(s.vy());
                buf.writeDouble(s.vz());
                buf.writeVarInt(s.life());
                buf.writeBoolean(s.resting());
                // +1: -1 (none) as a 1-byte varint.
                buf.writeVarInt(s.shooter() + 1);
                buf.writeVarInt(s.seq() + 1);
                buf.writeByte(s.ricochets());
            }
        }
        buf.writeVarInt(this.ends.size());
        for (RoundEnded e : this.ends) {
            buf.writeLong(e.key());
            buf.writeDouble(e.x());
            buf.writeDouble(e.y());
            buf.writeDouble(e.z());
            buf.writeByte(e.reason().ordinal());
        }
        buf.writeVarInt(this.pierces.size());
        for (RoundPierce p : this.pierces) {
            buf.writeLong(p.key());
            buf.writeLong(p.block());
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
            buf.writeByte(p.face().get3DDataValue() | (p.exited() ? 8 : 0));
            buf.writeDouble(p.px());
            buf.writeDouble(p.py());
            buf.writeDouble(p.pz());
        }
        buf.writeVarInt(this.ricochets.size());
        for (RoundRicochet r : this.ricochets) {
            buf.writeLong(r.key());
            buf.writeDouble(r.x());
            buf.writeDouble(r.y());
            buf.writeDouble(r.z());
            buf.writeFloat(r.nx());
            buf.writeFloat(r.ny());
            buf.writeFloat(r.nz());
            buf.writeByte(r.surface().ordinal());
            buf.writeLong(r.block());
        }
    }

    private static RoundPacket read(FriendlyByteBuf buf) {
        List<RoundSpawn> spawns = new ArrayList<>();
        int groups = buf.readVarInt();
        for (int g = 0; g < groups; g++) {
            ResourceLocation preset = buf.readResourceLocation();
            int n = buf.readVarInt();
            for (int k = 0; k < n; k++) {
                spawns.add(new RoundSpawn(buf.readLong(), preset, buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readBoolean(),
                        buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readByte()));
            }
        }
        int n = buf.readVarInt();
        List<RoundEnded> ends = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            ends.add(new RoundEnded(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    ENDS[buf.readByte()]));
        }
        n = buf.readVarInt();
        List<RoundPierce> pierces = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            long key = buf.readLong();
            long block = buf.readLong();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int flags = buf.readByte();
            pierces.add(new RoundPierce(key, block, x, y, z, Direction.from3DDataValue(flags & 7), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), (flags & 8) != 0));
        }
        n = buf.readVarInt();
        List<RoundRicochet> ricochets = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            ricochets.add(new RoundRicochet(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), SURFACES[buf.readByte()],
                    buf.readLong()));
        }
        return new RoundPacket(spawns, ends, pierces, ricochets);
    }

    @Override
    public Type<RoundPacket> type() {
        return TYPE;
    }
}
