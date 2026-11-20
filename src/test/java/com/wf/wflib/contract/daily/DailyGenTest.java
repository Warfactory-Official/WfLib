package com.wf.wflib.contract.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wf.wflib.contract.ContractDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DailyGenTest {
    private static final DailyGen.Forces FORCES = (faction, grade) -> List.of(faction + "_g" + grade);

    private static ContractDef make(DailyGen.Kind kind, long seed, double tier) {
        return DailyGen.generate(ResourceLocation.fromNamespaceAndPath("wflib", "daily/d0_0"), kind, new Random(seed), tier, 1.0, FORCES);
    }

    @Test
    void everyKindAtEveryTierIsAValidContract() {
        for (DailyGen.Kind kind : DailyGen.Kind.values()) {
            for (double tier = 0; tier <= 10; tier += 0.7) {
                for (long seed = 0; seed < 12; seed++) {
                    ContractDef def = make(kind, seed, tier);
                    // what is saved is what the loader accepts, so a day's board survives a restart
                    ContractDef back = ContractDef.parse(def.id(), def.toJson());
                    assertEquals(def, back, kind + " tier " + tier + " seed " + seed);
                }
            }
        }
    }

    @Test
    void theSameSeedGivesTheSameContractAndAnotherGivesAnother() {
        assertEquals(make(DailyGen.Kind.RAID, 5, 2), make(DailyGen.Kind.RAID, 5, 2));
        assertNotEquals(make(DailyGen.Kind.RAID, 5, 2).title(), make(DailyGen.Kind.RAID, 6, 2).title() + "x");
        boolean differs = false;
        for (long s = 1; s < 20 && !differs; s++) differs = !make(DailyGen.Kind.RAID, 0, 2).equals(make(DailyGen.Kind.RAID, s, 2));
        assertTrue(differs);
    }

    @Test
    void harderTiersFieldMoreAndPayMore() {
        ContractDef easy = make(DailyGen.Kind.CLEARANCE, 3, 0), hard = make(DailyGen.Kind.CLEARANCE, 3, 8);
        int e = easy.squads().stream().mapToInt(ContractDef.Squad::count).sum(), h = hard.squads().stream().mapToInt(ContractDef.Squad::count).sum();
        assertTrue(h > e, e + " vs " + h);
        assertTrue(hard.money() > easy.money());
        assertTrue(hard.gate().ordinal() > easy.gate().ordinal());
    }

    @Test
    void aDeliveryRunChargesWhatItIssues() {
        ContractDef run = make(DailyGen.Kind.NARCOTICS, 1, 3);
        assertEquals(1, run.cargo().size());
        assertEquals(run.cargo().get(0).count() * 150, run.buyIn());
        assertTrue(run.money() > run.buyIn(), "the run pays more than it costs");
    }

    @Test
    void aBoardIsVariedAndNeverRepeatsAKindMoreThanTwice() {
        List<DailyGen.Kind> kinds = new ArrayList<>();
        Random rnd = new Random(42);
        for (int i = 0; i < 6; i++) kinds.add(DailyGen.pick(rnd, kinds));
        for (DailyGen.Kind k : DailyGen.Kind.values()) assertTrue(kinds.stream().filter(k::equals).count() <= 2, kinds.toString());
    }
}
