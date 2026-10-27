package com.wf.wflib.radiation;

import org.jetbrains.annotations.Nullable;

/** Sidecar bytes on a {@code LevelChunk}: IOWorker read -> {@code ChunkDataEvent.Load} -> {@code ChunkEvent.Load}. Transient. */
public interface RadiationCarrier {

    /** Tag key the IOWorker read writes into the loaded chunk tag. */
    String NBT_KEY = "wflib:radiation";

    byte @Nullable [] wflib$getRadiation();

    void wflib$setRadiation(byte @Nullable [] bytes);
}
