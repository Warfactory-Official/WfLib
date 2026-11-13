package com.wf.wflib.mist.spray;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;

/** What a falling liquid curtain does to an entity it passes through; once per entity per curtain. Server-side. */
@FunctionalInterface
public interface SprayEntityEffect {

    void affect(ServerLevel level, Entity entity, Fluid fluid);
}
