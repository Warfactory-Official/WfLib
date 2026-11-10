package com.wf.wflib.soldier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Once per in-game day each owner's soldiers are paid from the owner's pockets ({@link RecruitConfig}). One that cannot be paid
 * leaves, and with the configured chance turns on its former owner instead. Off unless the config turns it on.
 */
final class Wages {
    static final long DAY = 24000;
    private static final RandomSource RNG = RandomSource.create();

    private Wages() {}

    /** Whether an unpaid soldier turns hostile, given the dice ({@code roll} 0-1). */
    static boolean defects(boolean enabled, double percent, double roll) {
        return enabled && roll * 100 < percent;
    }

    static void tick(MinecraftServer server, long now) {
        if (!RecruitConfig.wages()) return;
        var book = SquadBook.get(server);
        long day = now / DAY;
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            if (owner instanceof com.wf.wflib.soldier.api.SoldierPlayer) continue;
            var squad = Squads.deployed(owner.getUUID());
            if (squad.isEmpty()) continue;
            Long last = book.wageDay(owner.getUUID());
            if (last == null || last >= day) {
                if (last == null) book.wageDay(owner.getUUID(), day);                // the first day is on the house
                continue;
            }
            book.wageDay(owner.getUUID(), day);
            settle(owner, squad, RecruitConfig.wage(), RecruitConfig.defection(), RecruitConfig.defectionPercent(), RNG);
        }
    }

    /** Pays each of the owner's soldiers {@code wage}; one that cannot be paid leaves, or turns hostile by the dice. Returns how many were paid. */
    static int settle(ServerPlayer owner, java.util.List<SoldierAi.Bot> squad, com.wf.wflib.soldier.api.Cost wage, boolean defection, double percent, RandomSource rng) {
        int paid = 0;
        for (var b : squad) {
            if (wage.charge(owner)) {
                paid++;
                continue;
            }
            boolean hostile = defects(defection, percent, rng.nextDouble());
            String name = b.name;
            Squads.walkOut(owner, b, hostile);
            owner.sendSystemMessage(Component.literal(name + (hostile ? " was not paid and has turned on you!" : " was not paid and has left.")));
        }
        if (paid > 0) owner.sendSystemMessage(Component.literal("Paid " + paid + (paid == 1 ? " soldier: " : " soldiers: ") + wage.describe() + " each"));
        return paid;
    }
}
