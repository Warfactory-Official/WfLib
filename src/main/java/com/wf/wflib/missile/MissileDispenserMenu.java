package com.wf.wflib.missile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;


public class MissileDispenserMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final BlockPos pos;


    public MissileDispenserMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, ContainerLevelAccess.NULL, buf.readBlockPos());
    }


    public MissileDispenserMenu(int id, Inventory inv, ContainerLevelAccess access, BlockPos pos) {
        super(MissileSystem.MISSILE_DISPENSER_MENU.get(), id);
        this.access = access;
        this.pos = pos;
    }

    public BlockPos pos() {
        return this.pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, MissileSystem.MISSILE_DISPENSER.get());
    }
}
