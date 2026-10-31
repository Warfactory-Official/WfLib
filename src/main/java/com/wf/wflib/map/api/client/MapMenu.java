package com.wf.wflib.map.api.client;

import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

/** A right-click menu on a fullscreen map; actions get the clicked block. */
public interface MapMenu {

    void item(String label, Consumer<BlockPos> action);

    MapMenu sub(String label);
}
