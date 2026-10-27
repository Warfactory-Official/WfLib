package com.wf.wflib.compat.wfweight;

import com.warfactory.ultimateweight.api.IWeightCompatProvider;
import com.warfactory.ultimateweight.api.WeightCompatRegistry;
import com.warfactory.ultimateweight.core.WeightResolutionContext;
import com.warfactory.ultimateweight.v1211.WeightViews1211;
import com.wf.wflib.ammo.Ammo;
import com.wf.wflib.armor.ArmorPieceItem;
import com.wf.wflib.armor.ArmorStacks;
import com.wf.wflib.item.ItemMasses;
import com.wf.wflib.magazine.MagFill;
import com.wf.wflib.magazine.MagazineItem;
import com.wf.wflib.magazine.MagazineItems;
import com.wf.wflib.magazine.PodItem;
import com.wf.wflib.magazine.PodTubes;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalDouble;

/** {@link ItemMasses} plus contents (rounds, pod tubes, armour inserts). Priority above wfweight nested (100). */
public final class ItemWeights implements IWeightCompatProvider {
    static final int PRIORITY = 150;

    private ItemWeights() {
    }

    public static void register() {
        WeightCompatRegistry.register(new ItemWeights());
    }

    @Override
    public OptionalDouble getUnitWeight(Object stack) {
        if (!(stack instanceof ItemStack s) || s.isEmpty()) {
            return OptionalDouble.empty();
        }
        int depth = WeightResolutionContext.currentDepth() + 1;
        double kg = ItemMasses.unit(s.getItem());
        if (kg > 0) {
            kg += switch (s.getItem()) {
                case MagazineItem m -> rounds(m, s);
                case PodItem p -> tubes(s, depth);
                case ArmorPieceItem a -> inserts(s, depth);
                default -> 0;
            };
        }
        return kg > 0 ? OptionalDouble.of(kg) : OptionalDouble.empty();
    }

    private static double rounds(MagazineItem m, ItemStack s) {
        var calibre = Ammo.calibre(m.calibre());
        return calibre == null ? 0 : MagFill.contents(s).total() * (double) calibre.massKg();
    }

    private static double tubes(ItemStack s, int depth) {
        PodTubes tubes = s.get(MagazineItems.POD_TUBES.get());
        double kg = 0;
        if (tubes != null && depth <= WeightViews1211.maxNestedDepth()) {
            for (ItemStack t : tubes.tubes()) {
                kg += WeightViews1211.stackWeight(t, depth);
            }
        }
        return kg;
    }

    private static double inserts(ItemStack s, int depth) {
        double kg = 0;
        if (depth <= WeightViews1211.maxNestedDepth()) {
            for (ItemStack i : ArmorStacks.inserts(s)) {
                kg += WeightViews1211.stackWeight(i, depth);
            }
        }
        return kg;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
