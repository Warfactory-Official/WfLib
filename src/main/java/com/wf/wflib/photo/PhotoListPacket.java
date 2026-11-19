package com.wf.wflib.photo;

import com.wf.wflib.core.api.WFLib;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the player's gallery, newest first. */
public record PhotoListPacket(List<Row> rows) implements CustomPacketPayload {
    public static final int MAX_ROWS = 512;

    public record Row(UUID id, long time, String dimension, int x, int y, int z, int size, boolean shared) {
        static final StreamCodec<FriendlyByteBuf, Row> CODEC = StreamCodec.of(
                (buf, r) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, r.id);
                    buf.writeVarLong(r.time);
                    buf.writeUtf(r.dimension, 128);
                    buf.writeInt(r.x);
                    buf.writeInt(r.y);
                    buf.writeInt(r.z);
                    buf.writeVarInt(r.size);
                    buf.writeBoolean(r.shared);
                },
                buf -> new Row(UUIDUtil.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readUtf(128), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readBoolean()));

        static Row of(PhotoEntry e) {
            return new Row(e.id(), e.time(), e.dimension(), e.x(), e.y(), e.z(), e.size(), e.shared());
        }
    }

    public static final Type<PhotoListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo_list"));
    public static final StreamCodec<FriendlyByteBuf, PhotoListPacket> STREAM_CODEC =
            Row.CODEC.apply(ByteBufCodecs.list(MAX_ROWS)).map(PhotoListPacket::new, PhotoListPacket::rows);

    @Override
    public Type<PhotoListPacket> type() {
        return TYPE;
    }
}
