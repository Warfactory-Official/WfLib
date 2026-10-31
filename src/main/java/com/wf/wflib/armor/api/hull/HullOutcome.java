package com.wf.wflib.armor.api.hull;

/** What the armour did to a shot. */
public enum HullOutcome {

    /** Skated off. Nothing behind the plate is touched. */
    RICOCHET,
    /** Stopped by the plate; a little energy arrives as spall. */
    ABSORBED,
    /** Through, but much of the energy went into getting there. */
    PARTIAL,
    /** Through cleanly. */
    PENETRATION,
    /** Through as if the plate were not there. */
    OVERMATCH;

    public boolean isThrough() {
        return this == PARTIAL || this == PENETRATION || this == OVERMATCH;
    }
}
