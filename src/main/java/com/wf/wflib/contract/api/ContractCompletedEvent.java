package com.wf.wflib.contract.api;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.UUID;

/** A contract was finished successfully; posted on the game bus once the pay and reputation are in. {@code money} is the contract's base pay. */
public final class ContractCompletedEvent extends Event {
    private final ResourceLocation contract;
    private final String employer;
    private final List<UUID> party;
    private final int money;

    public ContractCompletedEvent(ResourceLocation contract, String employer, List<UUID> party, int money) {
        this.contract = contract;
        this.employer = employer;
        this.party = List.copyOf(party);
        this.money = money;
    }

    public ResourceLocation contract() {
        return contract;
    }

    /** The faction whose side the contract was on; empty when neutral. */
    public String employer() {
        return employer;
    }

    public List<UUID> party() {
        return party;
    }

    public int money() {
        return money;
    }
}
