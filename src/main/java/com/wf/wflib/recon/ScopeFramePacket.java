package com.wf.wflib.recon;

import com.wf.wflib.recon.api.ScopeFrame;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.wf.wflib.core.api.WFLib;

/** One network's scope picture, sent to the players who can see a display for it. */
public record ScopeFramePacket(ScopeFrame frame) implements CustomPacketPayload {

    public static final Type<ScopeFramePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "scope_frame"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScopeFramePacket> STREAM_CODEC =
            StreamCodec.of(ScopeFramePacket::encode, ScopeFramePacket::decode);

    @Override
    public Type<ScopeFramePacket> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ScopeFramePacket pkt) {
        ScopeFrame.STREAM_CODEC.encode(buf, pkt.frame);
    }

    private static ScopeFramePacket decode(RegistryFriendlyByteBuf buf) {
        return new ScopeFramePacket(ScopeFrame.STREAM_CODEC.decode(buf));
    }
}
