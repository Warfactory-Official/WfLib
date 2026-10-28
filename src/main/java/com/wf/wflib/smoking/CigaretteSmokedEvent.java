package com.wf.wflib.smoking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/** A cigarette was smoked to the end (before it is consumed). Game bus, server. */
public final class CigaretteSmokedEvent extends Event {
    private final ServerPlayer player;
    private final ItemStack cigarette;

    public CigaretteSmokedEvent(ServerPlayer player, ItemStack cigarette) {
        this.player = player;
        this.cigarette = cigarette;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public ItemStack cigarette() {
        return this.cigarette;
    }
}
