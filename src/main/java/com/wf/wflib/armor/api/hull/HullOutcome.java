package com.wf.wflib.armor.api.hull;

/** What the armour did to a shot. */
public enum HullOutcome {

    /** Skated off. Nothing behind the plate is touched. */
    RICOCHET,
    /** Stopped by the plate; a little energy arrives as spall. */
    ABSORBED,
    /** Stopped, but only just: hull damage ramps up, nothing behind the armour is reached. */
    PARTIAL,
    /** Through cleanly. */
    PENETRATION,
    /** Through as if the plate were not there. */
    OVERMATCH;

    /** Residual penetration left: crew and modules behind the armour are reachable. */
    public boolean isThrough() {
        return this == PENETRATION || this == OVERMATCH;
    }
}
