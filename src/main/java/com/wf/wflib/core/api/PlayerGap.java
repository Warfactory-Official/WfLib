package com.wf.wflib.core.api;

import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Per-player request rate: one per {@code gapMs} sustained, {@code burst} back to back. Server thread only. */
public final class PlayerGap {
    private static final List<PlayerGap> ALL = new CopyOnWriteArrayList<>();

    private final long gapMs;
    private final long slackMs;
    /** Earliest time the next request conforms with no burst credit left (GCRA). */
    private final Map<UUID, Long> due = new HashMap<>();

    public PlayerGap(long gapMs) {
        this(gapMs, 1);
    }

    public PlayerGap(long gapMs, int burst) {
        this.gapMs = gapMs;
        this.slackMs = (burst - 1) * gapMs;
        ALL.add(this);
    }

    /** @return false, consuming nothing, when over the rate */
    public boolean allow(ServerPlayer player) {
        long now = Util.getMillis();
        long next = Math.max(now, due.getOrDefault(player.getUUID(), now));
        if (next - now > slackMs) {
            return false;
        }
        due.put(player.getUUID(), next + gapMs);
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        for (PlayerGap gap : ALL) {
            gap.due.remove(event.getEntity().getUUID());
        }
    }
}
