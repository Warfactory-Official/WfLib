package com.wf.wflib.hazard;

import com.wf.wflib.hazard.modifier.IHazardModifier;
import com.wf.wflib.hazard.type.IHazardType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HazardEntry implements Cloneable {

    public final IHazardType type;
    public final double baseLevel;

    private @Nullable List<IHazardModifier> mods;

    public HazardEntry(IHazardType type) {
        this(type, 1D);
    }

    public HazardEntry(IHazardType type, double level) {
        this.type = type;
        this.baseLevel = level;
    }

    public HazardEntry addMod(IHazardModifier mod) {
        if (mods == null) mods = new ArrayList<>(1);
        mods.add(mod);
        return this;
    }

    public List<IHazardModifier> getMods() {
        return mods == null ? List.of() : mods;
    }

    public void applyHazard(ItemStack stack, LivingEntity entity) {
        type.onUpdate(entity, IHazardModifier.evalAllModifiers(stack, entity, baseLevel, getMods()), stack);
    }

    @Override
    public HazardEntry clone() {
        try {
            return (HazardEntry) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public HazardEntry clone(double mult) {
        HazardEntry clone = new HazardEntry(type, baseLevel * mult);
        clone.mods = this.mods;
        return clone;
    }
}
