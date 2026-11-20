package com.wf.wflib.contract.api;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Who plays together: a claim or team mod registers a lookup, and contracts split pay and share one job by it. Alone without one. */
public final class Parties {
    private static volatile Function<UUID, Set<UUID>> provider;

    private Parties() {
    }

    /** Set by the mod that knows the teams (Pluginless); null clears it. */
    public static void provider(Function<UUID, Set<UUID>> source) {
        provider = source;
    }

    /** The player and everyone grouped with them; just the player when there is no group. */
    public static Set<UUID> membersOf(UUID player) {
        var source = provider;
        var group = source == null ? null : source.apply(player);
        return group == null || !group.contains(player) ? Set.of(player) : group;
    }
}
