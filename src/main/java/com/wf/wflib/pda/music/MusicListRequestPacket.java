package com.wf.wflib.pda.music;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.wf.wflib.core.api.WFLib;

/** Music app SERVER tab -> {@link MusicListPacket}; {@code backgrounds} -> {@link BackgroundListPacket}. */
public record MusicListRequestPacket(boolean backgrounds) implements CustomPacketPayload {
    public static final MusicListRequestPacket AUDIO = new MusicListRequestPacket(false);
    public static final MusicListRequestPacket BACKGROUNDS = new MusicListRequestPacket(true);
    public static final Type<MusicListRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_music_list_request"));
    public static final StreamCodec<FriendlyByteBuf, MusicListRequestPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, MusicListRequestPacket::backgrounds,
                    MusicListRequestPacket::new);

    public void handle(IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            ctx.enqueueWork(() -> ServerMusic.list(player, backgrounds));
        }
    }

    @Override
    public Type<MusicListRequestPacket> type() {
        return TYPE;
    }
}
