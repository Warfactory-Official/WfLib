package com.wf.wflib.photo.api;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Posted on the server once a photograph's image has been received, validated and saved to the owner's gallery. */
public class PhotoStoredEvent extends Event {
    private final ServerPlayer owner;
    private final UUID id;

    public PhotoStoredEvent(ServerPlayer owner, UUID id) {
        this.owner = owner;
        this.id = id;
    }

    public ServerPlayer owner() {
        return owner;
    }

    public UUID id() {
        return id;
    }
}
