package com.wf.wflib.soldier.api;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;

/** The wire of a soldier, which has no client: nothing is sent and nothing is received. */
public final class SoldierConnection extends Connection {
    public static final SoldierConnection INSTANCE = new SoldierConnection();

    private SoldierConnection() {
        super(PacketFlow.SERVERBOUND);
        ((ChannelSetter) (Object) this).wflib$setChannel(new EmbeddedChannel());
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public void handleDisconnection() {
    }

    @Override
    public void setListenerForServerboundHandshake(PacketListener listener) {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
    }
}
