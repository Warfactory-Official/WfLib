package com.wf.wflib.cam.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A kind of entity that carries a linkable camera (drones); cam asks, never names the entity type. */
public interface FeedHost {
    OrderedRegistry<FeedHost> HOSTS = OrderedRegistry.create("cam", "cam.feed_hosts");

    boolean hosts(Entity entity);

    /** @return the fitted camera, or null if none. */
    @Nullable
    CameraSpec spec(Entity entity);

    /** Live fitted hosts within {@code range} of the point, nearest first. */
    List<Entity> near(ServerLevel level, double x, double y, double z, double range);

    /** Bring a host back that left the loaded world. @return true if worth re-resolving at once. */
    boolean recall(ServerLevel level, UUID id);

    /** A classified host's feed is watchable by nobody. */
    boolean classified(ServerLevel level, Entity entity);
}
