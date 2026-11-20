package com.wf.wflib.soldier.client;

import com.wf.wflib.ui.UiSignals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SquadScreenLayoutTest {
    @Test
    void layoutCompiles() {
        UiSignals.register();
        new SquadManage();
        assertNotNull(SquadScreen.build().instantiate());
    }
}
