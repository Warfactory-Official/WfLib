package com.wf.wflib.ragdoll;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.ragdoll.client.RagdollClient;
import com.wf.wflib.ragdoll.sim.RagdollPose;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class RagdollPackets {

    private static final double QUANT = 32767.0;

    private RagdollPackets() {
    }

    /** Torso position + rotations: 24 + 4 + 88 B plus id, stamp, flag. */
    public record Pose(int id, long stamp, boolean rest, double x, double y, double z, float footDrop, short[] q)
            implements CustomPacketPayload {
        static final Type<Pose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "ragdoll_pose"));
        static final StreamCodec<FriendlyByteBuf, Pose> STREAM_CODEC = CustomPacketPayload.codec(Pose::write, Pose::read);

        static Pose of(int id, long stamp, boolean rest, RagdollPose pose) {
            short[] q = new short[pose.q.length];
            for (int i = 0; i < q.length; i++) {
                q[i] = (short) Math.round(pose.q[i] * QUANT);
            }
            return new Pose(id, stamp, rest, pose.x, pose.y, pose.z, pose.footDrop, q);
        }

        private static Pose read(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            long stamp = buf.readVarLong();
            boolean rest = buf.readBoolean();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            float drop = buf.readFloat();
            short[] q = new short[44];
            for (int i = 0; i < q.length; i++) {
                q[i] = buf.readShort();
            }
            return new Pose(id, stamp, rest, x, y, z, drop, q);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeVarLong(stamp);
            buf.writeBoolean(rest);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(footDrop);
            for (short s : q) {
                buf.writeShort(s);
            }
        }

        public void apply(RagdollPose out) {
            out.x = x;
            out.y = y;
            out.z = z;
            out.footDrop = footDrop;
            for (int i = 0; i < 11; i++) {
                int r = i * 4;
                double a = q[r] / QUANT, b = q[r + 1] / QUANT, c = q[r + 2] / QUANT, d = q[r + 3] / QUANT;
                double n = 1.0 / Math.sqrt(a * a + b * b + c * c + d * d);
                out.q[r] = a * n;
                out.q[r + 1] = b * n;
                out.q[r + 2] = c * n;
                out.q[r + 3] = d * n;
            }
        }

        @Override
        public Type<Pose> type() {
            return TYPE;
        }

        static void handle(Pose packet, IPayloadContext context) {
            context.enqueueWork(() -> RagdollClient.pose(packet));
        }
    }

    /** {@code blend} > 0: stand-up blend from the held pose; 0: gone now. */
    record Stop(int id, int blend) implements CustomPacketPayload {
        static final Type<Stop> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "ragdoll_stop"));
        static final StreamCodec<FriendlyByteBuf, Stop> STREAM_CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.id);
                    buf.writeVarInt(p.blend);
                },
                buf -> new Stop(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<Stop> type() {
            return TYPE;
        }

        static void handle(Stop packet, IPayloadContext context) {
            context.enqueueWork(() -> RagdollClient.stop(packet.id, packet.blend));
        }
    }
}
