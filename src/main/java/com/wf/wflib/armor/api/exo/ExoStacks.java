package com.wf.wflib.armor.api.exo;

import com.wf.wflib.armor.ArmorComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/** The one place that knows how exo state is stored on a stack. Writes on a worn stack are equipment-sync packets. */
public final class ExoStacks {
    private ExoStacks() {
    }

    public static int hp(ItemStack stack, ExoSpec spec, int part) {
        List<Integer> raw = stack.get(ArmorComponents.EXO_PARTS.get());
        int max = spec.parts().get(part).maxHp();
        return raw == null || part >= raw.size() ? max : Math.min(raw.get(part), max);
    }

    public static PartState[] states(ItemStack stack, ExoSpec spec) {
        PartState[] out = new PartState[spec.parts().size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = PartState.of(hp(stack, spec, i), spec.parts().get(i).maxHp());
        }
        return out;
    }

    private static void setHp(ItemStack stack, ExoSpec spec, int part, int hp) {
        List<Integer> next = new ArrayList<>(spec.parts().size());
        boolean full = true;
        for (int i = 0; i < spec.parts().size(); i++) {
            int v = i == part ? hp : hp(stack, spec, i);
            next.add(v);
            full &= v >= spec.parts().get(i).maxHp();
        }
        if (full) {
            stack.remove(ArmorComponents.EXO_PARTS.get());
        } else {
            stack.set(ArmorComponents.EXO_PARTS.get(), List.copyOf(next));
        }
    }

    /** @return hp actually removed */
    public static int damage(ItemStack stack, ExoSpec spec, int part, int amount) {
        int before = hp(stack, spec, part);
        int after = Math.max(0, before - amount);
        if (after != before) {
            setHp(stack, spec, part, after);
        }
        return before - after;
    }

    /** @return hp actually restored */
    public static int repair(ItemStack stack, ExoSpec spec, int part, int amount) {
        int before = hp(stack, spec, part);
        int after = Math.min(spec.parts().get(part).maxHp(), before + amount);
        if (after != before) {
            setHp(stack, spec, part, after);
        }
        return after - before;
    }

    public static boolean repairs(ExoSpec spec, ItemStack item) {
        if (item.isEmpty()) {
            return false;
        }
        for (net.minecraft.resources.ResourceLocation tag : spec.repairTags()) {
            if (item.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag))) {
                return true;
            }
        }
        return false;
    }

    /** Spends one repair item on the most damaged part. */
    public static boolean repairWith(ItemStack garment, ExoSpec spec, ItemStack item) {
        int part = worstPart(garment, spec);
        if (part < 0 || !repairs(spec, item)) {
            return false;
        }
        repair(garment, spec, part, ExoConfig.get().repairHp());
        item.shrink(1);
        return true;
    }

    public static int worstPart(ItemStack stack, ExoSpec spec) {
        int worst = -1;
        double low = 2;
        for (int i = 0; i < spec.parts().size(); i++) {
            double f = (double) hp(stack, spec, i) / spec.parts().get(i).maxHp();
            if (f < 1 && f < low) {
                low = f;
                worst = i;
            }
        }
        return worst;
    }

    public static ItemStack battery(ItemStack garment) {
        return held(garment, ArmorComponents.EXO_BATTERY.get());
    }

    public static void setBattery(ItemStack garment, ItemStack battery) {
        put(garment, ArmorComponents.EXO_BATTERY.get(), battery);
    }

    public static ItemStack belt(ItemStack garment) {
        return held(garment, ArmorComponents.EXO_BELT.get());
    }

    public static void setBelt(ItemStack garment, ItemStack belt) {
        put(garment, ArmorComponents.EXO_BELT.get(), belt);
    }

    private static ItemStack held(ItemStack garment, DataComponentType<ItemContainerContents> type) {
        ItemContainerContents c = garment.get(type);
        return c == null ? ItemStack.EMPTY : c.copyOne();
    }

    private static void put(ItemStack garment, DataComponentType<ItemContainerContents> type, ItemStack held) {
        if (held.isEmpty()) {
            garment.remove(type);
        } else {
            garment.set(type, ItemContainerContents.fromItems(List.of(held)));
        }
    }
}
