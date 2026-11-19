package com.wf.wflib.soldier.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * A way of getting soldiers. Sources are interchangeable: the server's {@code wflib-recruiting.toml} lists which are on, and any
 * mix can be at once. A source that is a menu returns its {@link Offer}s; one that is a gesture (sneak-using a soldier) returns none
 * and the interaction checks {@link Recruiting#enabled}.
 */
public interface RecruitSource {
    /** Short id used in the config: {@code rescue}, {@code pda}, {@code barracks}, {@code garrison} are WFLib's own. */
    String id();

    String title();

    /** What the player can hire through this source right now (empty: nothing, or not a menu). */
    List<Offer> offers(ServerPlayer player);
}
