package com.wf.wflib.photo;

import java.util.UUID;

/** One stored photograph. {@code shared} = printed, so any player may fetch it by id; otherwise only {@code owner}. */
public record PhotoEntry(UUID id, UUID owner, String ownerName, long time, String dimension, int x, int y, int z,
                         float yaw, float pitch, int size, boolean shared) {
    public PhotoEntry withShared(boolean value) {
        return new PhotoEntry(id, owner, ownerName, time, dimension, x, y, z, yaw, pitch, size, value);
    }
}
