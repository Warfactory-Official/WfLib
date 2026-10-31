package com.wf.wflib.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import com.wf.wflib.drone.api.DroneContent;

/**
 * Slotless menu for the drone pad's config screen, mirroring {@link MissileDispenserMenu}: it exists only to carry
 * the pad's position to the client so the screen can read its stored mission and address it in packets.
 */
public class DronePadMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final BlockPos pos;

    public DronePadMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public DronePadMenu(int id, Inventory inv, ContainerLevelAccess access, BlockPos pos) {
        super(DroneSystem.DRONE_PAD_MENU.get(), id);
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
        return stillValid(this.access, player, DroneContent.DRONE_PAD.get());
    }
}
