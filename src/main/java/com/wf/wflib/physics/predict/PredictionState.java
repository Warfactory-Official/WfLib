package com.wf.wflib.physics.predict;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.physics.api.Predictable;
import com.wf.wflib.physics.api.Prediction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> operator: snapshot after input {@code ack}. */
public record PredictionState(int bodyId, int ack, byte[] state) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, PredictionState> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeVarInt(msg.bodyId);
                buf.writeInt(msg.ack);
                buf.writeByteArray(msg.state);
            },
            buf -> new PredictionState(buf.readVarInt(), buf.readInt(), buf.readByteArray()));
    public static final CustomPacketPayload.Type<PredictionState> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "prediction_state"));

    public static void handle(PredictionState message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().level().getEntity(message.bodyId) instanceof Predictable<?> body) {
                Prediction<?> prediction = body.prediction();
                if (prediction != null) {
                    prediction.received(message.ack, message.state);
                }
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
