package com.wf.wflib.orbital;

import com.wf.wflib.orbital.api.CargoTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import com.wf.wflib.orbital.api.OrbitalContent;

public class CargoShuttleBlockEntity extends BlockEntity {

    private final ItemStackHandler items = new ItemStackHandler(CargoTable.SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public CargoShuttleBlockEntity(BlockPos pos, BlockState state) {
        super(OrbitalContent.CARGO_SHUTTLE_BE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public void fill(List<ItemStack> manifest) {
        for (int i = 0; i < manifest.size() && i < items.getSlots(); i++) {
            items.setStackInSlot(i, manifest.get(i).copy());
        }
        setChanged();
    }

    public int occupiedSlots() {
        int used = 0;
        for (int i = 0; i < items.getSlots(); i++) {
            if (!items.getStackInSlot(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    public boolean isEmpty() {
        return occupiedSlots() == 0;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
    }
}
