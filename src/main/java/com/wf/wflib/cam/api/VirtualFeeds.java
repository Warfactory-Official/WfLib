package com.wf.wflib.cam.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Feeds that are neither entities (positive ids) nor fixed cameras: a system owns an id range outright. */
public final class VirtualFeeds {

    public interface Feed {
        boolean owns(int feedId);

        /** Null once the feed has ended. */
        @Nullable
        CameraSource source(ServerLevel level, int feedId);

        boolean entitled(ServerPlayer player, int feedId);

        /** Streamed-chunk window centre, or null if nothing should be streamed for this feed. */
        @Nullable
        Vec3 target(ServerLevel level, int feedId);
    }

    public static final OrderedRegistry<Feed> FEEDS = OrderedRegistry.create("cam", "cam.virtual_feeds");

    private VirtualFeeds() {
    }

    public static boolean owns(int feedId) {
        for (Feed feed : FEEDS.values()) {
            if (feed.owns(feedId)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static CameraSource source(ServerLevel level, int feedId) {
        for (Feed feed : FEEDS.values()) {
            if (feed.owns(feedId)) {
                return feed.source(level, feedId);
            }
        }
        return null;
    }

    public static boolean entitled(ServerPlayer player, int feedId) {
        for (Feed feed : FEEDS.values()) {
            if (feed.owns(feedId) && feed.entitled(player, feedId)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Vec3 target(ServerLevel level, int feedId) {
        for (Feed feed : FEEDS.values()) {
            if (feed.owns(feedId)) {
                return feed.target(level, feedId);
            }
        }
        return null;
    }
}
