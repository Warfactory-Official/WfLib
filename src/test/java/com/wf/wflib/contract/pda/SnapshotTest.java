package com.wf.wflib.contract.pda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import com.wf.wflib.contract.ContractDef;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SnapshotTest {
    @Test
    void aSnapshotSurvivesTheWire() {
        var offer = new Snapshot.Offer("wflib:hvt", "High Value Target", "Kill him.", "$750", 30, 30, 300, 600, "", "contractor", "enemy", "", List.of("Kill the target"));
        var snap = new Snapshot("proven", 320, 700, "trusted", 4, 1, 22, "$5,000", 2, List.of(offer),
                new Snapshot.Active("Gold Run", 120, -40, 600, List.of("Deliver 16 gold ingot")),
                List.of(new Snapshot.Heat("enemy", 45, "HUNTED", 10, "$500")), List.of("a headline"), "BLACK SITE 1 2", "EMBARGO", "done", "Tier 2.0 (Regular)");
        assertEquals(snap, Snapshot.fromJson(snap.toJson()));
    }

    @Test
    void noActiveContractStaysNull() {
        assertNull(Snapshot.fromJson(Snapshot.empty().toJson()).active());
    }

    @Test
    void noticeReplacesOnlyTheNotice() {
        var s = Snapshot.empty().withNotice("hi");
        assertEquals("hi", s.notice());
        assertEquals(Snapshot.empty().rank(), s.rank());
    }

    @Test
    void objectivesReadAsShortLines() {
        var def = ContractDef.parse(ResourceLocation.fromNamespaceAndPath("wflib", "x"), JsonParser.parseString("""
                { "title": "t", "offer": { "distance": [100, 200], "time_limit_min": 10 },
                  "site": { "radius": 10, "squads": [ { "faction": "enemy", "count": 2 } ] },
                  "objectives": [ { "type": "kill_target", "squad": 0 }, { "type": "retrieve_item", "item": "wflib:data_drive", "count": 2 }, { "type": "clear_area" } ],
                  "rewards": { "money": 1, "rep": 1 } }""").getAsJsonObject());
        assertEquals(List.of("Kill the target", "Bring back 2 data drive", "Clear the site"), SnapshotBuilder.describe(def));
    }
}
