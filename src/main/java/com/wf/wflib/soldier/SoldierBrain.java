package com.wf.wflib.soldier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * What a spawn-egg soldier wants when nothing is shooting at it, as the orders {@link SoldierAi} already understands:
 * a friendly stays with whoever placed it, an enemy holds its post and goes to where it last saw someone.
 */
final class SoldierBrain {
    /** How far a friendly lets its owner get before it follows, and how loosely it stays around them. */
    static final double FOLLOW_RANGE = 64, ESCORT_RADIUS = 5;
    /** An enemy gives up a chase this far from its post. */
    static final double LEASH = 56;
    private static final int SEARCH_DELAY = 40;

    private SoldierBrain() {
    }

    static void tick(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b) {
        if (p.tickCount % 10 != 0) return;
        if (b.huntsPlayers) hunt(p, b);
        else escort(server, p, b);
    }

    private static void escort(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b) {
        ServerPlayer owner = b.ownerId == null ? null : server.getPlayerList().getPlayer(b.ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != p.level() || !owner.onGround()
                || owner.distanceTo(p) > FOLLOW_RANGE) return;                 // out of reach: hold the last spot
        Vec3 spot = slot(owner.position(), p.getGameProfile().getName().hashCode());
        if (b.anchor == null || b.anchor.distanceToSqr(owner.position()) > ESCORT_RADIUS * ESCORT_RADIUS * 2.25) {
            b.anchor = spot;
            b.mode = SoldierAi.Mode.HOLD;
            b.holdRadius = (int) ESCORT_RADIUS;
        }
    }

    private static void hunt(ServerPlayer p, SoldierAi.Bot b) {
        Vec3 post = b.spawn;
        if (post == null) return;
        long now = p.level().getGameTime();
        SoldierAi.Threat last = null;
        for (SoldierAi.Threat t : b.threats.values()) if (last == null || t.seen > last.seen) last = t;
        boolean lost = last != null && last.visible == 0 && now - last.seen > SEARCH_DELAY;
        if (lost && p.position().distanceToSqr(post) < LEASH * LEASH) {
            b.anchor = last.eye.subtract(0, 1.62, 0);                           // where they were, not where they are
            b.mode = SoldierAi.Mode.ADVANCE;
            b.holdRadius = 4;
            b.hunting = true;
        } else if (b.hunting && (last == null || p.position().distanceToSqr(post) >= LEASH * LEASH)) {
            b.anchor = post;                                                    // nothing left to find: back to the post
            b.mode = SoldierAi.Mode.HOLD;
            b.holdRadius = 8;
            b.hunting = false;
        }
    }

    /** A spot around {@code centre}, a different one for each soldier (by name hash). */
    static Vec3 slot(Vec3 centre, int seed) {
        double angle = Math.toRadians(Math.floorMod(seed * 47, 360));
        double radius = 2.5 + Math.floorMod(seed, 3);
        return centre.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }
}
