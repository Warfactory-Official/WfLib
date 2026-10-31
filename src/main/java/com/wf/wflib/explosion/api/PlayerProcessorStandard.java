package com.wf.wflib.explosion.api;

import com.wf.wflib.explosion.ExplosionKnockbackPacket;
import com.wf.wflib.core.api.WFNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class PlayerProcessorStandard implements IPlayerProcessor {

    @Override
    public void process(ExplosionAEF explosion, Level level, double x, double y, double z, Map<Player, Vec3> affectedPlayers) {
        for (Map.Entry<Player, Vec3> entry : affectedPlayers.entrySet()) {
            if (entry.getKey() instanceof ServerPlayer player) {
                WFNetwork.sendToPlayer(player, new ExplosionKnockbackPacket(entry.getValue()));
            }
        }
    }
}
