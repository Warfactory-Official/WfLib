package com.wf.wflib.physics.api;

import org.jetbrains.annotations.Nullable;

/** Mixed into every entity; parent-frame storage slot. */
public interface DeckRider {

    @Nullable
    DeckAttachment wflib$deckAttachment();

    void wflib$setDeckAttachment(@Nullable DeckAttachment attachment);

    @Nullable
    DeckRejoin wflib$deckRejoin();

    void wflib$setDeckRejoin(@Nullable DeckRejoin rejoin);

}
