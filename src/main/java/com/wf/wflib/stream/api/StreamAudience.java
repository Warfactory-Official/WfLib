package com.wf.wflib.stream.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Entity tracked to a player at any range; any registered audience claiming it suffices. */
public interface StreamAudience {
    OrderedRegistry<StreamAudience> AUDIENCES = OrderedRegistry.create("stream", "stream.audiences");

    boolean watches(ServerPlayer player, Entity entity);
}
