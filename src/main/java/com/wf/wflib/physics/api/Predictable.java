package com.wf.wflib.physics.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Body its operator's client steps ahead of the server ({@link Prediction}). Snapshot layout starts with position
 * (3 doubles), rotation (3 floats, degrees), velocity (3 doubles): {@link Prediction} compares those.
 *
 * @param <I> one tick's operator input
 */
public interface Predictable<I> {

    int getId();

    Level level();

    boolean isRemoved();

    /** Empty history slot, filled by {@link #copyInput}. */
    I newInput();

    void copyInput(I from, I into);

    /** Input the next {@link #predictStep} consumes. */
    void applyInput(I input);

    /** The server's step for this body alone, minus side effects. */
    void predictStep();

    /** After a replayed step, before its snapshot. */
    void afterReplayStep();

    byte[] snapshot();

    void restoreSnapshot(byte[] state);

    /** Client: null while not predicted. */
    @Nullable
    Prediction<I> prediction();

    /** Server: player whose inputs drive the body, null none. */
    @Nullable
    ServerPlayer predictionOperator();

    /** Server: seq of the input consumed this tick. */
    int inputSeq();
}
