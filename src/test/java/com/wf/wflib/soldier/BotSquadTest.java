package com.wf.wflib.soldier;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BotSquadTest {
    @Test void aSupportGunCoversTheFirstRifleBoundThenGetsItsOwnTurn() {
        var rifle = new BotSquad.Member("rifle", "rifle", -1000, true);
        var mg = new BotSquad.Member("mg", "mg", -1000, true);
        assertEquals(List.of("rifle"), BotSquad.movers(List.of(mg, rifle), 1));
        assertEquals(List.of("mg"), BotSquad.movers(List.of(mg,
                new BotSquad.Member("rifle", "rifle", 100, true)), 1));
    }

    @Test void CasualtiesAndOccupiedMedicsCannotTakeABoundSlot() {
        assertEquals(List.of("ready"), BotSquad.movers(List.of(
                new BotSquad.Member("downed", "rifle", -1000, false),
                new BotSquad.Member("medic", "medic", -1000, false),
                new BotSquad.Member("ready", "rifle", 100, true)), 2));
    }

    @Test void sharedObjectivesPutSupportAndMedicalSlotsBehindTheAssault() {
        Vec3 objective = new Vec3(10, 0, 0), forward = new Vec3(1, 0, 0);
        assertEquals(10, BotSquad.formation(objective, forward, "rifle", 0).x);
        assertEquals(6, BotSquad.formation(objective, forward, "medic", 1).x);
        assertEquals(4, BotSquad.formation(objective, forward, "mg", 2).x);
        assertEquals(4, BotSquad.formation(objective, forward, "rifle", 0)
                .distanceTo(BotSquad.formation(objective, forward, "rifle", 1)));
    }
}
