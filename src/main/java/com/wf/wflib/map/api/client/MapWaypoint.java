package com.wf.wflib.map.api.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** A place the map mod should keep as one of its own waypoints (name, beacon, distance in the world), shown until it is replaced. */
public record MapWaypoint(String name, ResourceKey<Level> dimension, BlockPos pos, int color) {
}
