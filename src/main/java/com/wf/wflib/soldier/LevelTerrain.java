package com.wf.wflib.soldier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The live world, through the same checks the AI has always used. Tick thread only. */
final class LevelTerrain implements Terrain {
    private final ServerLevel level;
    private final Entity viewer;

    LevelTerrain(ServerLevel level, Entity viewer) {
        this.level = level;
        this.viewer = viewer;
    }

    @Override
    public boolean walkable(BlockPos feet) {
        return BotTactics.walkable(level, feet);
    }

    @Override
    public boolean clear(BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public boolean exposed(BlockPos cell, List<Vec3> threats, double h) {
        return BotTactics.exposed(level, cell, threats, h, viewer);
    }
}
