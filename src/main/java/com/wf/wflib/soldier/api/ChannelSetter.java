package com.wf.wflib.soldier.api;

import io.netty.channel.Channel;

/** Implemented by {@code Connection} through {@code mixin.soldier.ConnectionMixin}. */
public interface ChannelSetter {
    void wflib$setChannel(Channel channel);
}
