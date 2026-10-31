package com.wf.wflib.round.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Round (re)sync as clients see it.
 *
 * @param shooter   shooter entity id; -1 none
 * @param seq       {@link Rounds#launch} {@code shooterSeq}; {@link Rounds#NO_SEQ} none
 * @param ricochets > 0 => tumbling
 */
public record RoundSpawn(long key, ResourceLocation preset, double x, double y, double z,
                         double vx, double vy, double vz, int life, boolean resting, int shooter, int seq,
                         int ricochets) {
}
