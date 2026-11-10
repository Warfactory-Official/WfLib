package com.wf.wflib.soldier.api;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What a soldier has been told to do. A player's command and a standing assignment (patrol, guard) are the same thing:
 * one order, carried out by the squad AI, shown by the HUD and acknowledged by voice.
 *
 * @param pos where, for the kinds that need a point
 * @param region a territory id, for patrol and guard
 */
public record SoldierOrder(Kind kind, @Nullable Vec3 pos, String region) {
    public enum Kind {
        FOLLOW("follow", "Follow", "ack_follow", false, false),
        /** Run back to the owner, then follow. */
        REGROUP("regroup", "Regroup", "ack_regroup", false, false),
        HOLD("hold", "Hold", "ack_hold", false, false),
        MOVE("move", "Move", "ack_move", true, false),
        ATTACK("attack", "Attack", "ack_attack", true, false),
        SUPPRESS("suppress", "Suppress", "ack_suppress", true, false),
        PATROL("patrol", "Patrol", "ack_patrol", false, true),
        GUARD("guard", "Guard", "ack_hold", false, true),
        RESUPPLY("resupply", "Resupply", "ack_resupply", false, false);

        public final String id, title, ack;
        /** The order needs a point in the world (the crosshair) / a named territory. */
        public final boolean needsPos, needsRegion;

        Kind(String id, String title, String ack, boolean needsPos, boolean needsRegion) {
            this.id = id;
            this.title = title;
            this.ack = ack;
            this.needsPos = needsPos;
            this.needsRegion = needsRegion;
        }

        public static @Nullable Kind of(String id) {
            for (Kind k : values()) if (k.id.equals(id.toLowerCase(Locale.ROOT))) return k;
            return null;
        }
    }

    public static final SoldierOrder FOLLOW = new SoldierOrder(Kind.FOLLOW, null, "");

    public SoldierOrder {
        if (kind == null) throw new IllegalArgumentException("kind");
        if (region == null) region = "";
        if (pos != null && !Double.isFinite(pos.lengthSqr())) throw new IllegalArgumentException("Finite position required");
        if (kind.needsPos && pos == null) throw new IllegalArgumentException(kind.title + " needs a position");
        if (kind.needsRegion && region.isEmpty()) throw new IllegalArgumentException(kind.title + " needs a territory");
    }

    public static SoldierOrder of(Kind kind) {
        return new SoldierOrder(kind, null, "");
    }

    public static SoldierOrder at(Kind kind, Vec3 pos) {
        return new SoldierOrder(kind, pos, "");
    }

    public static SoldierOrder in(Kind kind, String region) {
        return new SoldierOrder(kind, null, region);
    }

    /** Short line for the HUD and chat: "Move", "Patrol north_gate". */
    public String label() {
        return region.isEmpty() ? kind.title : kind.title + " " + region;
    }
}
