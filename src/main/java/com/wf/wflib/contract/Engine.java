package com.wf.wflib.contract;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** The contract rules with no Minecraft in them: given what the world looks like now, is the contract won, lost, or running? */
public final class Engine {
    private Engine() {
    }

    public enum Status {
        ACTIVE, COMPLETE, TIMED_OUT, ABANDONED
    }

    /**
     * What the world shows at one look.
     *
     * @param now game time
     * @param squadRemaining soldiers alive or still to come, per squad index
     * @param held how many of each item the party carries
     * @param heldAtSite how many of each item party members carry while standing within the site's radius
     */
    public record Snapshot(long now, Map<Integer, Integer> squadRemaining, Map<ResourceLocation, Integer> held,
                           Map<ResourceLocation, Integer> heldAtSite) {
        public Snapshot(long now, Map<Integer, Integer> squadRemaining, Map<ResourceLocation, Integer> held) {
            this(now, squadRemaining, held, Map.of());
        }

        public int remaining(int squad) {
            return squadRemaining.getOrDefault(squad, 0);
        }

        public int total() {
            return squadRemaining.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Completion wins over the clock when both hold on the same look: a contract finished in time is finished. */
    public static Status evaluate(ContractDef def, long deadline, Snapshot s) {
        if (def.objectives().stream().allMatch(o -> done(o, s))) return Status.COMPLETE;
        return s.now() > deadline ? Status.TIMED_OUT : Status.ACTIVE;
    }

    public static boolean done(ContractDef.Objective o, Snapshot s) {
        return switch (o) {
            case ContractDef.KillTarget k -> s.remaining(k.squad()) == 0;
            case ContractDef.RetrieveItem r -> s.held().getOrDefault(r.item(), 0) >= r.count();
            case ContractDef.DeliverItem d -> s.heldAtSite().getOrDefault(d.item(), 0) >= d.count();
            case ContractDef.ClearArea c -> s.total() == 0;
        };
    }

    /** Objectives still open, for a status line. */
    public static long open(ContractDef def, Snapshot s) {
        return def.objectives().stream().filter(o -> !done(o, s)).count();
    }
}
