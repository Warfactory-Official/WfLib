package com.wf.wflib.ui.api.input;

import com.wf.wflib.core.api.OrderedRegistry;

/** Claims a mouse turn before the player camera gets it; first claimant by priority wins. */
public interface MouseSteer {
    OrderedRegistry<MouseSteer> STEERS = OrderedRegistry.create("ui", "ui.mouse_steer");

    /** @return true if the turn was consumed. */
    boolean steer(double yaw, double pitch);
}
