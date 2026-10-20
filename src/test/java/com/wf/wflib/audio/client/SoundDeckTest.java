package com.wf.wflib.audio.client;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SoundDeckTest {
    /** Each deck = every slot once; unit weights => no entry twice in a row, across reshuffles too. */
    @Test
    void dealsEverySlotPerDeckWithoutRepeats() {
        RandomSource random = RandomSource.create(7);
        for (int[] slots : new int[][]{{0, 1}, {0, 1, 2}, {0, 1, 1, 2, 3}}) {
            SoundDeck deck = new SoundDeck(slots.clone());
            int prev = -1;
            for (int round = 0; round < 200; round++) {
                int[] dealt = new int[slots.length];
                for (int i = 0; i < slots.length; i++) {
                    dealt[i] = deck.deal(random);
                    if (slots.length <= 3) {
                        assertNotEquals(prev, dealt[i], Arrays.toString(slots) + " round " + round);
                    }
                    prev = dealt[i];
                }
                int[] sorted = dealt.clone();
                Arrays.sort(sorted);
                assertArrayEquals(slots, sorted);
            }
        }
    }
}
