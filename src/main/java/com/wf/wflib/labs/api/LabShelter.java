package com.wf.wflib.labs.api;

import net.minecraft.world.entity.LivingEntity;

/** A place an occupant stands in (vehicle, bunker) and the real code that shields it. */
public interface LabShelter {
    String id();

    /**
     * Runs the shelter's own shielding for one exposure of {@code occupant} (the lab's unarmoured entity) and returns
     * what reaches the occupant. Server thread only.
     *
     * @param kind   hazard kind ({@link LabHazard#kind()}: {@code radiation}, {@code gas}, {@code fire}, ...)
     * @param amount per exposure: RAD/s for {@code radiation}, hit damage otherwise
     */
    double shield(LivingEntity occupant, String kind, double amount);
}
