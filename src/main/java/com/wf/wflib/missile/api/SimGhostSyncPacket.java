package com.wf.wflib.missile.api;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.core.api.WFLib;

/**
 * Off-world {@link SimGhosts} near one player, render only: no target, owner or orders on the wire.
 *
 * @param added   first sight of a handle; precedes its move in the same packet
 * @param moved   position + velocity (blocks/tick) at send time; client extrapolates
 * @param removed handles gone from view (rematerialized, dead, out of range)
 */
public record SimGhostSyncPacket(List<Added> added, List<Moved> moved, int[] removed) implements CustomPacketPayload {

    public static final int INTERVAL = 10;
    /** Client ghost entity id = base + handle; never a server entity id. */
    public static final int GHOST_ID_BASE = -0x4000_0000;

    public static boolean isGhostId(int id) {
        return id >= GHOST_ID_BASE && id < 0;
    }

    /** @param sim the sim's UUID = the real entity's once it rematerializes */
    public record Added(int handle, java.util.UUID sim, ResourceLocation kind, ResourceLocation model, int exhaust) {
    }

    public record Moved(int handle, double x, double y, double z, float vx, float vy, float vz) {
    }

    public static final Type<SimGhostSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "sim_ghosts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SimGhostSyncPacket> STREAM_CODEC =
            StreamCodec.of(SimGhostSyncPacket::encode, SimGhostSyncPacket::decode);

    @Override
    public Type<SimGhostSyncPacket> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, SimGhostSyncPacket pkt) {
        buf.writeVarInt(pkt.added.size());
        for (Added a : pkt.added) {
            buf.writeVarInt(a.handle);
            buf.writeUUID(a.sim);
            buf.writeResourceLocation(a.kind);
            buf.writeResourceLocation(a.model);
            buf.writeInt(a.exhaust);
        }
        buf.writeVarInt(pkt.moved.size());
        for (Moved m : pkt.moved) {
            buf.writeVarInt(m.handle);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeFloat(m.vx);
            buf.writeFloat(m.vy);
            buf.writeFloat(m.vz);
        }
        buf.writeVarIntArray(pkt.removed);
    }

    private static SimGhostSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int addedCount = buf.readVarInt();
        List<Added> added = new ArrayList<>(addedCount);
        for (int i = 0; i < addedCount; i++) {
            added.add(new Added(buf.readVarInt(), buf.readUUID(), buf.readResourceLocation(), buf.readResourceLocation(),
                    buf.readInt()));
        }
        int movedCount = buf.readVarInt();
        List<Moved> moved = new ArrayList<>(movedCount);
        for (int i = 0; i < movedCount; i++) {
            moved.add(new Moved(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        return new SimGhostSyncPacket(added, moved, buf.readVarIntArray());
    }
}
