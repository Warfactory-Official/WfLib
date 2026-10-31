package com.wf.wflib.round.api;

import com.wf.wflib.round.RoundPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Reads a round payload captured off a connection; any other payload reads as empty. */
public final class RoundPackets {
    private RoundPackets() {}

    public static List<RoundSpawn> spawns(CustomPacketPayload payload) {
        return payload instanceof RoundPacket p ? p.spawns() : List.of();
    }

    public static List<RoundEnded> ends(CustomPacketPayload payload) {
        return payload instanceof RoundPacket p ? p.ends() : List.of();
    }

    public static List<RoundPierce> pierces(CustomPacketPayload payload) {
        return payload instanceof RoundPacket p ? p.pierces() : List.of();
    }

    public static List<RoundRicochet> ricochets(CustomPacketPayload payload) {
        return payload instanceof RoundPacket p ? p.ricochets() : List.of();
    }
}
