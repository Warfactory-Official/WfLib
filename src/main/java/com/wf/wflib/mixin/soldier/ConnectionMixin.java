package com.wf.wflib.mixin.soldier;

import com.wf.wflib.soldier.api.ChannelSetter;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Connection.class)
abstract class ConnectionMixin implements ChannelSetter {
    @Shadow
    private Channel channel;

    /** NeoForge's payload checks read the channel's attributes, so a soldier's connection needs one. */
    @Override
    public void wflib$setChannel(Channel channel) {
        this.channel = channel;
    }
}
