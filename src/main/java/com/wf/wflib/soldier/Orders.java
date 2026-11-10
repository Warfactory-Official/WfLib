package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierRole;
import net.minecraft.world.phys.Vec3;

/** Turns an order into what {@link SoldierAi} already understands: a mode, an anchor and a hold radius. */
final class Orders {
    private Orders() {}

    /** Gives {@code b} the order; {@code here} is where the soldier stands (for "hold here"). */
    static void apply(SoldierAi.Bot b, Vec3 here, SoldierOrder o) {
        // a hold or suppress with no point stays where the soldier stands: keep that point, so it can be resumed
        if (o.pos() == null && (o.kind() == SoldierOrder.Kind.HOLD || o.kind() == SoldierOrder.Kind.SUPPRESS)) o = SoldierOrder.at(o.kind(), here);
        b.order = o;
        b.cover = null;
        b.route.clear();
        b.path.clear();
        b.patrolPoint = null;
        b.dwell = -1;
        b.depotId = "";
        b.triedDepots.clear();
        b.hunting = false;
        b.sprint = SoldierRole.tactics(b.role) != SoldierRole.HEAVY;      // each order below says if it runs
        switch (o.kind()) {
            case FOLLOW, REGROUP -> {                  // the escort brain re-anchors on the owner within a second
                b.anchor = null;
                b.mode = SoldierAi.Mode.HOLD;
                b.holdRadius = (int) SoldierBrain.ESCORT_RADIUS;
                b.sprint = true;
            }
            case HOLD -> {
                b.anchor = o.pos() != null ? o.pos() : here;
                b.mode = SoldierAi.Mode.HOLD;
                b.holdRadius = 4;
            }
            case MOVE, ATTACK -> {                     // cover to cover, then hold where it ends
                b.anchor = o.pos();
                b.mode = SoldierAi.Mode.ADVANCE;
                b.holdRadius = 4;
                b.sprint = true;
            }
            case SUPPRESS -> {                         // stay put and put fire on the point (SoldierBrain keeps the contact fresh)
                b.anchor = here;
                b.mode = SoldierAi.Mode.HOLD;
                b.holdRadius = 3;
                b.suppressAt = o.pos();
                b.suppressUntil = -1;
            }
            case PATROL, GUARD -> {
                b.anchor = here;
                b.mode = o.kind() == SoldierOrder.Kind.PATROL ? SoldierAi.Mode.ADVANCE : SoldierAi.Mode.HOLD;
                b.holdRadius = 2;
                b.sprint = false;                      // walking: Patrols lets it run when a fight starts
            }
            case RESUPPLY -> {
                b.anchor = here;
                b.mode = SoldierAi.Mode.ADVANCE;
                b.holdRadius = 2;
                b.sprint = true;
            }
        }
        if (o.kind() != SoldierOrder.Kind.SUPPRESS) {
            b.suppressAt = null;
            b.threats.remove(SoldierBrain.SUPPRESS_KEY);
        }
    }
}
