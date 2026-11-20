package com.wf.wflib.soldier.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** Layouts compile lazily on first draw, so a bad expression crashes the client; build them here instead. */
class SquadUiLayoutTest {
    @Test
    void layoutsBind() {
        com.wf.wflib.ui.UiSignals.register();
        com.wf.wflib.pda.PdaShellOnce.ensure();
        HireApp.register();
        SquadHud.register();
        CommandWheel.register();
        HireScreen.register();
        assertDoesNotThrow(() -> SquadHud.build().instantiate());
        assertDoesNotThrow(() -> CommandWheel.build().instantiate());
        assertDoesNotThrow(() -> HireScreen.build().instantiate());
    }
}
