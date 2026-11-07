package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierMedical;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotMedicalTest {
    @Test void disabledHandsRequestHelpWithoutPretendingTheSoldierIsUnconscious() {
        var arms = new SoldierMedical.Condition(false, true, false, false, 1, 0, 0.2f);
        assertFalse(arms.downed());
        assertTrue(arms.needsHelp());
        assertFalse(arms.critical());
    }

    @Test void aDownedCasualtyHasPriorityOverAMobileWoundedSoldier() {
        var arms = new SoldierMedical.Condition(false, true, false, false, 1, 0, 0.2f);
        var down = new SoldierMedical.Condition(true, true, false, true, 0.68, 0.3, 0.4f);
        assertTrue(down.urgency() > arms.urgency());
        assertTrue(down.critical());
        assertFalse(SoldierMedical.Condition.HEALTHY.needsHelp());
    }
}
