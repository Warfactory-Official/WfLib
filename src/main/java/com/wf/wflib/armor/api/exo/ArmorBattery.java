package com.wf.wflib.armor.api.exo;

import com.wf.wflib.compat.api.GregtechCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** Item energy in FE-equivalent: GTCEu electric item first (EU x ratio), else NeoForge item {@code EnergyStorage}. */
public final class ArmorBattery {
    public static final TagKey<Item> TAG = TagKey.create(BuiltInRegistries.ITEM.key(),
            ResourceLocation.fromNamespaceAndPath("wflib", "armor_battery"));

    /** Test/override seam; null = real backends. */
    public interface Backend {
        long stored(ItemStack stack);

        long capacity(ItemStack stack);

        long extract(ItemStack stack, long fe, boolean simulate);
    }

    @Nullable
    private static volatile Backend override;

    private ArmorBattery() {
    }

    public static void override(@Nullable Backend backend) {
        override = backend;
    }

    public static boolean accepts(ItemStack stack) {
        return stack.is(TAG);
    }

    public static long stored(ItemStack stack) {
        Backend o = override;
        if (o != null) {
            return o.stored(stack);
        }
        if (GregtechCompat.isActive()) {
            long eu = GregtechCompat.itemCharge(stack);
            if (eu >= 0) {
                return Math.round(eu * ExoConfig.get().euToFe());
            }
        }
        IEnergyStorage fe = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return fe == null ? 0 : fe.getEnergyStored();
    }

    public static long capacity(ItemStack stack) {
        Backend o = override;
        if (o != null) {
            return o.capacity(stack);
        }
        if (GregtechCompat.isActive()) {
            long eu = GregtechCompat.itemMaxCharge(stack);
            if (eu >= 0) {
                return Math.round(eu * ExoConfig.get().euToFe());
            }
        }
        IEnergyStorage fe = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return fe == null ? 0 : fe.getMaxEnergyStored();
    }

    /** @return FE actually extracted (all-or-nothing is the caller's: pass simulate first). */
    public static long extract(ItemStack stack, long fe, boolean simulate) {
        if (fe <= 0 || stack.isEmpty()) {
            return 0;
        }
        Backend o = override;
        if (o != null) {
            return o.extract(stack, fe, simulate);
        }
        if (stack.getItem() instanceof EnergyCellItem) {
            return EnergyCellItem.drain(stack, fe, simulate);
        }
        if (GregtechCompat.isActive()) {
            double ratio = ExoConfig.get().euToFe();
            long eu = (long) Math.ceil(fe / ratio);
            long got = GregtechCompat.itemDischarge(stack, eu, simulate);
            if (got >= 0) {
                return Math.min(fe, Math.round(got * ratio));
            }
        }
        IEnergyStorage cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return cap == null ? 0 : cap.extractEnergy((int) Math.min(Integer.MAX_VALUE, fe), simulate);
    }

    /** All-or-nothing draw of {@code amount} in the configured unit. */
    public static boolean draw(ItemStack stack, double amount) {
        if (stack.isEmpty()) {
            return false;
        }
        long fe = (long) Math.ceil(ExoConfig.get().toFe(amount));
        if (fe <= 0) {
            return true;
        }
        if (extract(stack, fe, true) < fe) {
            return false;
        }
        return extract(stack, fe, false) >= fe;
    }

    /** Charge fraction 0..1; 0 when no battery. */
    public static double fraction(ItemStack stack) {
        long cap = capacity(stack);
        return cap <= 0 ? 0 : Math.min(1, (double) stored(stack) / cap);
    }
}
