package com.wf.wflib.explosion.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public interface IPlayerProcessor {

    void process(ExplosionAEF explosion, Level level, double x, double y, double z, Map<Player, Vec3> affectedPlayers);
}
