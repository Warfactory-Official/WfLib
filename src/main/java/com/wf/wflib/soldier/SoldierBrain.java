package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierRole;
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
    /** The chase counts as having reached where the contact was seen this close (blocks). */
    private static final double ARRIVED = 4;
    /** Following: beyond this many blocks a soldier runs, and keeps running until it is within {@code RUN_CAUGHT_UP}. */
    static final double RUN_FAR = 14, RUN_CAUGHT_UP = 8;
    /** A contact seen this recently (game ticks) means a fight is on. */
    private static final long FIGHT_TICKS = 100;

    private SoldierBrain() {
    }

    /** The key of the stand-in contact a suppress order keeps fresh at the ordered point. */
    static final String SUPPRESS_KEY = "~suppress";
    /** How long a suppress order keeps firing at its point (game ticks). */
    static final int SUPPRESS_TICKS = 600;

    static void tick(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b) {
        if (p.tickCount % 10 != 0) return;
        long now = p.level().getGameTime();
        Resupply.tick(server, p, b, now);
        switch (b.order.kind()) {
            case FOLLOW, REGROUP -> {
                Squads.catchUp(server, p, b, now);
                Squads.greet(server, p, b, now);
                if (b.huntsPlayers) hunt(p, b);
                else escort(server, p, b);
                if (b.order.kind() == com.wf.wflib.soldier.api.SoldierOrder.Kind.REGROUP && b.anchor != null
                        && p.position().distanceToSqr(b.anchor) < ESCORT_RADIUS * ESCORT_RADIUS * 4) {
                    b.order = com.wf.wflib.soldier.api.SoldierOrder.FOLLOW;           // back with the owner
                }
            }
            case PATROL, GUARD -> {
                if (b.huntsPlayers) hunt(p, b);
                Patrols.tick(server, p, b, now);
            }
            case MOVE, ATTACK -> {                                                    // arrived: it holds where it stands
                if (b.anchor != null && b.threats.isEmpty() && Patrols.horizontal(p.position(), b.anchor) <= 3 + b.holdRadius) {
                    Orders.apply(b, p.position(), com.wf.wflib.soldier.api.SoldierOrder.at(com.wf.wflib.soldier.api.SoldierOrder.Kind.HOLD, p.position()));
                }
            }
            case SUPPRESS -> suppress(p, b, now);
            case HOLD, RESUPPLY -> { }                                                // the AI mode / Resupply carry these out
        }
    }

    /** Keeps a stand-in contact at the ordered point so the AI lays suppressing fire on it, for {@link #SUPPRESS_TICKS}. */
    private static void suppress(ServerPlayer p, SoldierAi.Bot b, long now) {
        if (b.suppressAt == null) return;
        if (b.suppressUntil < 0) b.suppressUntil = now + SUPPRESS_TICKS;
        if (now >= b.suppressUntil) {
            Orders.apply(b, p.position(), com.wf.wflib.soldier.api.SoldierOrder.at(com.wf.wflib.soldier.api.SoldierOrder.Kind.HOLD, p.position()));
            return;
        }
        SoldierAi.Threat t = b.threats.computeIfAbsent(SUPPRESS_KEY, k -> new SoldierAi.Threat());
        t.eye = b.suppressAt.add(0, 1.6, 0);
        t.seen = now;
        t.visible = 0;
        t.confirmed = true;
        t.value = 1f;
    }

    private static void escort(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b) {
        ServerPlayer owner = b.ownerId == null ? null : server.getPlayerList().getPlayer(b.ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != p.level() || !owner.onGround()
                || owner.distanceTo(p) > FOLLOW_RANGE) return;                 // out of reach: hold the last spot
        // The formation faces the way the owner is travelling, not where they look: turning the head must not move anyone.
        Vec3 moved = b.ownerSeen == null ? Vec3.ZERO : owner.position().subtract(b.ownerSeen).multiply(1, 0, 1);
        b.ownerSeen = owner.position();
        if (moved.lengthSqr() > 1) b.heading = moved.normalize();                // more than a block in half a second: really walking
        else if (b.heading == null) b.heading = owner.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 spot = roleSlot(owner.position(), b.heading, b.role, p.getGameProfile().getName().hashCode());
        long now = p.level().getGameTime();
        boolean fight = b.threats.values().stream().anyMatch(t -> now - t.seen < FIGHT_TICKS);
        b.sprint = followSprint(b.sprint, owner.distanceTo(p), owner.isSprinting(), fight)
                && com.wf.wflib.soldier.api.SoldierRole.tactics(b.role) != com.wf.wflib.soldier.api.SoldierRole.HEAVY;
        if (b.anchor == null || b.anchor.distanceToSqr(spot) > ESCORT_RADIUS * ESCORT_RADIUS * 2.25) {
            b.anchor = spot;
            b.mode = SoldierAi.Mode.HOLD;
            b.holdRadius = (int) ESCORT_RADIUS;
        }
    }

    /** Walks beside a calm owner; runs when the owner runs, when it has fallen well behind (until it has caught up), or in a fight. */
    static boolean followSprint(boolean sprinting, double distance, boolean ownerSprinting, boolean fight) {
        if (fight) return true;
        if (ownerSprinting && distance > 3) return true;
        return distance > RUN_FAR || sprinting && distance > RUN_CAUGHT_UP;
    }

    private static void hunt(ServerPlayer p, SoldierAi.Bot b) {
        Vec3 post = b.spawn;
        if (post == null) return;
        long now = p.level().getGameTime();
        SoldierAi.Threat last = null;
        for (SoldierAi.Threat t : b.threats.values()) if (last == null || t.seen > last.seen) last = t;
        if (b.hunting && last != null && last.visible == 0 && p.position().distanceToSqr(b.anchor) < ARRIVED * ARRIVED) {
            b.huntedSeen = last.seen;                                           // got there: nothing, so stop looking
        }
        boolean lost = !SoldierRole.tactics(b.role).support() && last != null && worthChasing(now, last.seen, last.visible, b.huntedSeen);
        if (lost && p.position().distanceToSqr(post) < LEASH * LEASH) {
            b.anchor = last.eye.subtract(0, 1.62, 0);                           // where they were, not where they are
            b.mode = SoldierAi.Mode.ADVANCE;
            b.holdRadius = 4;
            b.hunting = true;
        } else if (b.hunting && (last == null || last.seen == b.huntedSeen
                || p.position().distanceToSqr(post) >= LEASH * LEASH)) {
            b.anchor = post;                                                    // nothing left to find: back to the post
            b.mode = SoldierAi.Mode.HOLD;
            b.holdRadius = 8;
            b.hunting = false;
        }
    }

    /** Go to where a contact was last seen: once it has been out of sight a moment, and not again after a chase there found nothing. */
    static boolean worthChasing(long now, long lastSeen, int visible, long huntedSeen) {
        return visible == 0 && now - lastSeen > SEARCH_DELAY && lastSeen != huntedSeen;
    }

    static Vec3 roleSlot(Vec3 centre,Vec3 forward,String role,int seed) {
        int back=SoldierRole.tactics(role).formationBack;
        return slot(centre,seed).subtract(forward.multiply(1,0,1).normalize().scale(back));
    }
    /** A spot around {@code centre}, a different one for each soldier (by name hash). */
    static Vec3 slot(Vec3 centre, int seed) {
        double angle = Math.toRadians(Math.floorMod(seed * 47, 360));
        double radius = 2.5 + Math.floorMod(seed, 3);
        return centre.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }
}
