package com.wf.wflib.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.wf.wflib.contract.profile.Rank;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ContractCoreTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("wflib", "hvt");
    private static final String GOOD = """
            { "title": "High Value Target", "gate": { "rank": "contractor" },
              "offer": { "distance": [300, 600], "time_limit_min": 30 },
              "site": { "radius": 14, "squads": [ { "faction": "enemy", "count": 1, "mode": "guard" }, { "faction": "enemy", "count": 4, "mode": "patrol" } ] },
              "objectives": [ { "type": "kill_target", "squad": 0 } ],
              "rewards": { "money": 750, "rep": 30 }, "failure": { "penalty_rep": 5 } }""";

    private static ContractDef parse(String json) {
        return ContractDef.parse(ID, JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void aGoodContractParses() {
        var def = parse(GOOD);
        assertEquals("High Value Target", def.title());
        assertEquals(Rank.CONTRACTOR, def.gate());
        assertEquals(2, def.squads().size());
        assertEquals(new ContractDef.KillTarget(0), def.objectives().get(0));
        assertEquals(750, def.money());
        assertEquals(5, def.penaltyRep());
    }

    @Test
    void rewardItemsSurviveTheFileRoundTrip() {
        var def = parse(GOOD.replace("\"rep\": 30 }", "\"rep\": 30, \"items\": [ { \"item\": \"minecraft:gold_ingot\", \"count\": 5 } ] }"));
        assertEquals(5, def.items().get(0).count());
        assertEquals(def.items(), ContractDef.parse(ID, def.toJson()).items());
        assertTrue(parse(GOOD).items().isEmpty());
    }

    @Test
    void partiesAreJustYouUntilAProviderIsRegistered() {
        var me = java.util.UUID.randomUUID();
        var mate = java.util.UUID.randomUUID();
        assertEquals(java.util.Set.of(me), com.wf.wflib.contract.api.Parties.membersOf(me));
        com.wf.wflib.contract.api.Parties.provider(u -> java.util.Set.of(me, mate));
        try {
            assertEquals(java.util.Set.of(me, mate), com.wf.wflib.contract.api.Parties.membersOf(me));
            assertEquals(java.util.Set.of(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")),
                    com.wf.wflib.contract.api.Parties.membersOf(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")));
        } finally {
            com.wf.wflib.contract.api.Parties.provider(null);
        }
    }

    @Test
    void badContractsAreRefusedWithAReason() {
        assertThrows(IllegalArgumentException.class, () -> parse(GOOD.replace("\"squad\": 0", "\"squad\": 5")));
        assertThrows(IllegalArgumentException.class, () -> parse(GOOD.replace("kill_target", "teleport")));
        assertThrows(IllegalArgumentException.class, () -> parse(GOOD.replace("contractor", "emperor")));
        assertThrows(IllegalArgumentException.class, () -> parse(GOOD.replace("[300, 600]", "[600, 300]")));
        assertThrows(IllegalArgumentException.class, () -> parse(GOOD.replace("\"money\": 750", "\"money\": -1")));
        assertThrows(IllegalArgumentException.class, () -> parse(GOOD.replace("\"faction\": \"enemy\", \"count\": 1", "\"count\": 1")));
    }

    @Test
    void ranksFollowReputation() {
        assertEquals(Rank.UNKNOWN, Rank.of(0));
        assertEquals(Rank.UNKNOWN, Rank.of(99));
        assertEquals(Rank.CONTRACTOR, Rank.of(100));
        assertEquals(Rank.ELITE, Rank.of(99999));
        assertTrue(Rank.PROVEN.atLeast(Rank.CONTRACTOR));
    }

    @Test
    void aSplitAddsUpExactly() {
        assertEquals(1000, java.util.Arrays.stream(Payout.split(1000, 3, 1.0)).sum());
        assertEquals(334, Payout.split(1000, 3, 1.0)[0]);
        assertEquals(333, Payout.split(1000, 3, 1.0)[2]);
        assertEquals(1500, java.util.Arrays.stream(Payout.split(1000, 4, 1.5)).sum());
        assertEquals(750, Payout.split(750, 1, 1.0)[0]);
        assertThrows(IllegalArgumentException.class, () -> Payout.split(100, 0, 1));
    }

    @Test
    void killTargetNeedsItsSquadDeadNotTheWholeSite() {
        var def = parse(GOOD);
        var live = new Engine.Snapshot(100, Map.of(0, 1, 1, 4), Map.of());
        var targetDead = new Engine.Snapshot(100, Map.of(0, 0, 1, 4), Map.of());
        assertEquals(Engine.Status.ACTIVE, Engine.evaluate(def, 500, live));
        assertEquals(Engine.Status.COMPLETE, Engine.evaluate(def, 500, targetDead));
    }

    @Test
    void theClockEndsAContractOnlyWhenItIsNotWon() {
        var def = parse(GOOD);
        var live = new Engine.Snapshot(600, Map.of(0, 1), Map.of());
        var won = new Engine.Snapshot(600, Map.of(0, 0), Map.of());
        assertEquals(Engine.Status.TIMED_OUT, Engine.evaluate(def, 500, live));
        assertEquals(Engine.Status.COMPLETE, Engine.evaluate(def, 500, won));
    }

    @Test
    void retrievingAnItemNeedsTheFullCount() {
        var drive = ResourceLocation.fromNamespaceAndPath("wflib", "data_drive");
        var def = parse(GOOD.replace("{ \"type\": \"kill_target\", \"squad\": 0 }", "{ \"type\": \"retrieve_item\", \"item\": \"wflib:data_drive\", \"count\": 2 }"));
        assertEquals(Engine.Status.ACTIVE, Engine.evaluate(def, 500, new Engine.Snapshot(1, Map.of(), Map.of(drive, 1))));
        assertEquals(Engine.Status.COMPLETE, Engine.evaluate(def, 500, new Engine.Snapshot(1, Map.of(), Map.of(drive, 2))));
    }

    @Test
    void severalObjectivesAllHaveToHold() {
        var def = parse(GOOD.replace("[ { \"type\": \"kill_target\", \"squad\": 0 } ]",
                "[ { \"type\": \"kill_target\", \"squad\": 0 }, { \"type\": \"clear_area\" } ]"));
        assertEquals(Engine.Status.ACTIVE, Engine.evaluate(def, 500, new Engine.Snapshot(1, Map.of(0, 0, 1, 2), Map.of())));
        assertEquals(Engine.Status.COMPLETE, Engine.evaluate(def, 500, new Engine.Snapshot(1, Map.of(0, 0, 1, 0), Map.of())));
    }

    private static final String RUN = """
            { "title": "Gold Run", "offer": { "distance": [500, 900], "time_limit_min": 30 },
              "site": { "radius": 10, "squads": [ { "faction": "enemy", "count": 4, "mode": "patrol" } ] },
              "smuggling": { "buy_in": 2000, "cargo": [ { "item": "minecraft:gold_ingot", "count": 16 } ] },
              "objectives": [ { "type": "deliver_item", "item": "minecraft:gold_ingot", "count": 16 } ],
              "rewards": { "money": 4200, "rep": 40 } }""";

    @Test
    void aSmugglingRunParsesWithItsBuyInAndCargo() {
        var def = parse(RUN);
        assertEquals(2000, def.buyIn());
        assertEquals(1, def.cargo().size());
        assertEquals(16, def.cargo().get(0).count());
    }

    @Test
    void aDeliveryNeedsCargoAndCargoNeedsADelivery() {
        assertThrows(IllegalArgumentException.class, () -> parse(RUN.replaceAll("\"smuggling\".*?\\},\\s*\\n", "")));
        assertThrows(IllegalArgumentException.class, () -> parse(GOOD.replace("\"rewards\"", "\"smuggling\": { \"buy_in\": 1, \"cargo\": [ { \"item\": \"minecraft:gold_ingot\", \"count\": 1 } ] }, \"rewards\"")));
    }

    @Test
    void goodsOnlyCountWhereTheyAreDelivered() {
        var gold = ResourceLocation.fromNamespaceAndPath("minecraft", "gold_ingot");
        var def = parse(RUN);
        var carried = new Engine.Snapshot(1, Map.of(0, 4), Map.of(gold, 16), Map.of());
        var delivered = new Engine.Snapshot(1, Map.of(0, 4), Map.of(gold, 16), Map.of(gold, 16));
        assertEquals(Engine.Status.ACTIVE, Engine.evaluate(def, 500, carried));
        assertEquals(Engine.Status.COMPLETE, Engine.evaluate(def, 500, delivered));
    }
}
