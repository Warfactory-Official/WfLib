package com.wf.wflib.drone;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.drone.api.DroneProgram;
import com.wf.wflib.drone.api.PdaDrones;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/** Drones app queue / typed chain -> {@link PdaDrones#program}; reply + fresh status back. */
public record DroneProgramPacket(DroneProgram program, Optional<UUID> drone) implements CustomPacketPayload {
    public static final Type<DroneProgramPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_drone_program"));
    public static final StreamCodec<FriendlyByteBuf, DroneProgramPacket> STREAM_CODEC = StreamCodec.composite(
            StreamCodec.of((buf, p) -> p.write(buf), DroneProgram::read), DroneProgramPacket::program,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), DroneProgramPacket::drone, DroneProgramPacket::new);

    public void handle(IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> {
                String line = PdaDrones.program(player, program.tasks(), drone);
                PacketDistributor.sendToPlayer(player, new DroneDispatchReplyPacket(line));
                PacketDistributor.sendToPlayer(player, new DroneStatusPacket(PdaDrones.status(player)));
            });
        }
    }

    @Override
    public Type<DroneProgramPacket> type() {
        return TYPE;
    }
}
