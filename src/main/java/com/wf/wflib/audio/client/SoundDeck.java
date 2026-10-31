package com.wf.wflib.audio.client;

import com.wf.wflib.mixin.audio.AccessorWeighedSoundEvents;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Variant pick per event: shuffled deck over the top-level entries (each repeated by its weight), dealt in order,
 * reshuffled when out; a reshuffle never leads with the entry dealt last. Total weight > {@link #MAX_SLOTS} => vanilla
 * draw. Keyed by event object => resource reload starts fresh decks. Client thread.
 */
public final class SoundDeck {
    static final int MAX_SLOTS = 64;
    private static final Map<WeighedSoundEvents, SoundDeck> DECKS = new WeakHashMap<>();

    private final int[] slots;
    private int next;
    private int last = -1;

    SoundDeck(int[] slots) {
        this.slots = slots;
        next = slots.length;
    }

    public static Sound deal(WeighedSoundEvents events, RandomSource random) {
        List<Weighted<Sound>> list = ((AccessorWeighedSoundEvents) events).wflib$list();
        if (list.size() < 2) {
            return events.getSound(random);
        }
        SoundDeck deck = DECKS.get(events);
        if (deck == null) {
            int[] slots = slots(list);
            if (slots == null) {
                return events.getSound(random);
            }
            DECKS.put(events, deck = new SoundDeck(slots));
        }
        return list.get(deck.deal(random)).getSound(random);
    }

    private static int[] slots(List<Weighted<Sound>> list) {
        int total = 0;
        for (Weighted<Sound> w : list) {
            if (w.getWeight() <= 0) {
                return null;
            }
            total += w.getWeight();
        }
        if (total > MAX_SLOTS) {
            return null;
        }
        int[] slots = new int[total];
        int k = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int n = list.get(i).getWeight(); n > 0; n--) {
                slots[k++] = i;
            }
        }
        return slots;
    }

    int deal(RandomSource random) {
        if (next == slots.length) {
            shuffle(slots, last, random);
            next = 0;
        }
        return last = slots[next++];
    }

    /** Fisher-Yates; then {@code slots[0] != avoid} when another entry exists. */
    static void shuffle(int[] slots, int avoid, RandomSource random) {
        for (int i = slots.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = slots[i];
            slots[i] = slots[j];
            slots[j] = t;
        }
        if (slots[0] == avoid) {
            for (int j = 1; j < slots.length; j++) {
                if (slots[j] != avoid) {
                    slots[0] = slots[j];
                    slots[j] = avoid;
                    return;
                }
            }
        }
    }
}
