package com.wf.wflib.soldier.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** Layouts compile lazily on first draw, so a bad expression crashes the client; build it here instead. */
class GearLayoutTest {
    @Test
    void layoutBinds() {
        com.wf.wflib.pda.PdaShellOnce.ensure();
        com.wf.wflib.ui.UiSignals.register();
        GearScreen.register();
        assertDoesNotThrow(() -> GearLayout.layout().instantiate());
    }
}
