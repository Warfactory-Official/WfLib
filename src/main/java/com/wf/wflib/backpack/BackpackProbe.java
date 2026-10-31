package com.wf.wflib.backpack;

import com.wf.wflib.probe.api.ProbeActions;
import com.wf.wflib.backpack.api.Backpacks;

/** Probe action {@link Backpacks#OPEN} on a player: another player's worn backpack, unless locked. */
final class BackpackProbe {
    private BackpackProbe() {
    }

    static void registerServer() {
        ProbeActions.registerEntity(Backpacks.OPEN, (player, entity, arg) -> BackpackServer.open(player, entity));
    }
}
