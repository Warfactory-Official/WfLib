package com.wf.wflib.physics.api;

import com.wf.wflib.physics.PhysicsConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Deck config snapshot ({@code wflib-physics.toml}); {@link #override} wins over the file.
 *
 * @param parenting entities ride decks in the carrier frame
 * @param carrierDecks bodies rest on other bodies' landing pieces
 */
public record DeckSettings(boolean parenting, boolean carrierDecks) {

    public static final DeckSettings DEFAULTS = new DeckSettings(true, true);

    private static volatile DeckSettings override;

    public static DeckSettings current() {
        DeckSettings forced = override;
        return forced != null ? forced : PhysicsConfig.deck();
    }

    /** {@code null} clears. */
    public static void override(@Nullable DeckSettings settings) {
        override = settings;
    }
}
