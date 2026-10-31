package com.wf.wflib.armor.api.exo;

import com.wf.wflib.armor.ArmorComponents;
import com.wf.wflib.core.api.WFLib;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Rechargeable FE container for the exo battery slot; charge in {@link ArmorComponents#ENERGY}, absent = empty. */
public final class EnergyCellItem extends Item {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WFLib.MODID);

    public static final DeferredHolder<Item, EnergyCellItem> CELL =
            ITEMS.register("exo_cell", () -> new EnergyCellItem(400_000, 2_000));
    public static final DeferredHolder<Item, EnergyCellItem> CELL_LARGE =
            ITEMS.register("exo_cell_large", () -> new EnergyCellItem(2_000_000, 10_000));

    private final int capacity;
    private final int transfer;

    private EnergyCellItem(int capacity, int transfer) {
        super(new Properties().stacksTo(1));
        this.capacity = capacity;
        this.transfer = transfer;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (DeferredHolder<Item, EnergyCellItem> holder : List.of(CELL, CELL_LARGE)) {
            event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new Storage(stack, holder.get()),
                    holder.get());
        }
    }

    public ItemStack full() {
        ItemStack stack = new ItemStack(this);
        stack.set(ArmorComponents.ENERGY.get(), capacity);
        return stack;
    }

    /** Exo-only discharge; the capability is charge-only. */
    static long drain(ItemStack stack, long fe, boolean simulate) {
        int stored = stored(stack);
        int given = (int) Math.min(fe, stored);
        if (!simulate && given > 0) {
            stack.set(ArmorComponents.ENERGY.get(), stored - given);
        }
        return given;
    }

    static int stored(ItemStack stack) {
        return stack.getOrDefault(ArmorComponents.ENERGY.get(), 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * stored(stack) / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.33F * stored(stack) / capacity, 0.8F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ExoConfig.Values cfg = ExoConfig.get();
        double unit = cfg.toFe(1);
        tooltip.add(Component.translatable("wflib.exo.cell.charge." + cfg.unit().name().toLowerCase(),
                Math.round(stored(stack) / unit), Math.round(capacity / unit)));
    }

    private record Storage(ItemStack stack, EnergyCellItem cell) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int stored = stored(stack);
            int taken = Mth.clamp(toReceive, 0, Math.min(cell.transfer, cell.capacity - stored));
            if (!simulate && taken > 0) {
                stack.set(ArmorComponents.ENERGY.get(), stored + taken);
            }
            return taken;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return stored(stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return cell.capacity;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
