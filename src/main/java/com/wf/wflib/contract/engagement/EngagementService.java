package com.wf.wflib.contract.engagement;

import com.wf.wflib.map.api.MarkerData;
import com.wf.wflib.soldier.api.Site;
import com.wf.wflib.soldier.api.SiteFinder;
import com.wf.wflib.soldier.api.SiteSpec;
import com.wf.wflib.soldier.api.Sites;
import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.List;

/**
 * A major engagement: two forces fight over a patch of ground a few hundred blocks from a player, announced on the map.
 * It runs {@link #FIGHT_TICKS}, then a quiet {@link #AFTERMATH_TICKS} for scavenging before the soldiers are taken away.
 * The two squads are of different factions, so they fight each other without any further instruction.
 */
public final class EngagementService {
    /** Soldiers per side. A site raises them a few at a time, so a big number costs time, not a stall. */
    public static final int SIDE = 12;
    public static final int GAP_MIN = 60, FIGHT_TICKS = 20 * 1200, AFTERMATH_TICKS = 5 * 1200;
    private static final RandomSource RNG = RandomSource.create();

    private EngagementService() {
    }

    static final class State extends SavedData {
        String site = "";
        long fightEnd, end, nextAt;

        static State get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, (tag, reg) -> {
                State s = new State();
                s.site = tag.getString("site");
                s.fightEnd = tag.getLong("fightEnd");
                s.end = tag.getLong("end");
                s.nextAt = tag.getLong("nextAt");
                return s;
            }), "wflib_engagement");
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putString("site", site);
            tag.putLong("fightEnd", fightEnd);
            tag.putLong("end", end);
            tag.putLong("nextAt", nextAt);
            return tag;
        }
    }

    public static void tick(MinecraftServer server) {
        var state = State.get(server);
        long now = server.overworld().getGameTime();
        if (!state.site.isEmpty()) {
            if (now >= state.end || Sites.get(server, state.site).isEmpty()) finish(server, state, now);
            else if (now >= state.fightEnd && state.fightEnd > 0) {
                state.fightEnd = 0;                                       // announced once
                state.setDirty();
                server.getPlayerList().broadcastSystemMessage(Component.literal("The fighting dies down. The field is open.").withStyle(ChatFormatting.GRAY), false);
            }
            return;
        }
        if (state.nextAt == 0) {
            state.nextAt = now + GAP_MIN * 1200L;
            state.setDirty();
        }
        if (now >= state.nextAt) start(server, state, now);
    }

    /** Starts one now (the schedule calls this; so can a command). Returns false when none fits. */
    public static boolean start(MinecraftServer server, State state, long now) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        var blue = com.wf.wflib.soldier.api.RandomForces.all(server, "friendly");
        var red = com.wf.wflib.soldier.api.RandomForces.all(server, "enemy");
        if (players.isEmpty() || blue.isEmpty() || red.isEmpty()) {
            state.nextAt = now + 6000;                                     // nobody to see it or nothing to raise: look again in five minutes
            state.setDirty();
            return false;
        }
        ServerPlayer anchor = players.get(RNG.nextInt(players.size()));
        var spot = SiteFinder.find(anchor.serverLevel(), new SiteFinder.Request(anchor.position(), 400, 900, 30, 100, true, 16), RNG);
        if (spot.isEmpty()) {
            state.nextAt = now + 6000;
            state.setDirty();
            return false;
        }
        String id = "me" + (now % 100000);
        try {
            Sites.create(anchor.serverLevel(), id, spot.get(), new SiteSpec(30, List.of(
                    new SiteSpec.Squad(blue, SIDE, SoldierOrder.Kind.PATROL), new SiteSpec.Squad(red, SIDE, SoldierOrder.Kind.PATROL))));
        } catch (IllegalArgumentException e) {
            com.wf.wflib.contract.ContractSystem.LOGGER.warn("Engagement not raised: {}", e.getMessage());
            state.nextAt = now + 6000;
            state.setDirty();
            return false;
        }
        state.site = id;
        state.fightEnd = now + FIGHT_TICKS;
        state.end = now + FIGHT_TICKS + AFTERMATH_TICKS;
        state.setDirty();
        BlockPos at = BlockPos.containing(spot.get());
        MarkerData markers = MarkerData.get(server);
        for (ServerPlayer p : players) markers.addAndSync(server, MarkerData.side(p), anchor.level().dimension().location(), at, "Engagement");
        server.getPlayerList().broadcastSystemMessage(Component.literal("A major engagement has broken out near " + at.getX() + ", " + at.getZ() + ".")
                .withStyle(ChatFormatting.RED), false);
        return true;
    }

    private static void finish(MinecraftServer server, State state, long now) {
        var site = Sites.get(server, state.site);
        int blue = site.map(x -> x.squadRemaining(0)).orElse(0), red = site.map(x -> x.squadRemaining(1)).orElse(0);
        site.ifPresent(Site::remove);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.wf.wflib.contract.api.EngagementEndedEvent(
                blue == red ? "" : blue > red ? "friendly" : "enemy", blue, red, SIDE));
        state.site = "";
        state.fightEnd = 0;
        state.end = 0;
        state.nextAt = now + GAP_MIN * 1200L;
        state.setDirty();
    }

    public static boolean startNow(MinecraftServer server) {
        var state = State.get(server);
        return state.site.isEmpty() && start(server, state, server.overworld().getGameTime());
    }
}
