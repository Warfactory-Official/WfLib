package com.wf.wflib.magazine.api;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/** Tube {@code i} -> its round, empty = empty tube; one item each. Equality by item + components. */
public record PodTubes(List<ItemStack> tubes) {
    public static final Codec<PodTubes> CODEC =
            ItemStack.OPTIONAL_CODEC.listOf().xmap(PodTubes::new, PodTubes::tubes);
    public static final StreamCodec<RegistryFriendlyByteBuf, PodTubes> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(PodTubes::new, PodTubes::tubes);

    public PodTubes {
        tubes = tubes.stream().map(s -> s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1)).toList();
    }

    public static PodTubes empty(int tubes) {
        return new PodTubes(Collections.nCopies(tubes, ItemStack.EMPTY));
    }

    public int loaded() {
        return (int) tubes.stream().filter(s -> !s.isEmpty()).count();
    }

    public PodTubes with(int tube, ItemStack round) {
        ItemStack[] out = tubes.toArray(ItemStack[]::new);
        out[tube] = round;
        return new PodTubes(List.of(out));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PodTubes p) || tubes.size() != p.tubes.size()) {
            return false;
        }
        for (int i = 0; i < tubes.size(); i++) {
            if (!ItemStack.matches(tubes.get(i), p.tubes.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (ItemStack tube : tubes) {
            hash = hash * 31 + ItemStack.hashItemAndComponents(tube);
        }
        return hash;
    }
}
