package com.wf.wflib.photo.api;

import com.wf.wflib.photo.PhotoLimits;
import com.wf.wflib.photo.PhotoServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Taking a photograph from another mod's camera item. The item owns its own handling (input, cooldown, zoom, film);
 * this only fires the shutter: the server records the pose and field of view ({@link PhotoTakenEvent}), the client
 * renders the shot from exactly that pose and uploads it, and it lands in the player's gallery.
 */
public final class Photos {
    private Photos() {
    }

    /**
     * Fires the shutter for {@code player} at vertical field of view {@code fovDegrees} (clamped to
     * {@link #MIN_FOV}..{@link #MAX_FOV}). Server thread.
     *
     * @return false when refused (gallery full, or the configured film item is missing); the player has been told
     */
    public static boolean take(ServerPlayer player, float fovDegrees) {
        return PhotoServer.shutter(player, fovDegrees);
    }

    public static final float MIN_FOV = PhotoLimits.MIN_FOV;
    public static final float MAX_FOV = PhotoLimits.MAX_FOV;
    /** The field of view of an unzoomed shot. */
    public static final float DEFAULT_FOV = PhotoLimits.FOV;
}
