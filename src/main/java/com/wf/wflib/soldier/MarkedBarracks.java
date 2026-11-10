package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.BarracksType;
import com.wf.wflib.soldier.api.Offer;
import com.wf.wflib.soldier.api.Recruiting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Barracks a map-maker marks with {@code /soldiers barracks add}: any block at all, hiring every friendly variant at the PDA price. */
final class MarkedBarracks implements BarracksType {
    @Override
    public String id() {
        return "marked";
    }

    @Override
    public boolean matches(ServerLevel level, BlockPos pos) {
        return BarracksBook.get(level.getServer()).has(level.dimension().location().toString(), pos);
    }

    @Override
    public List<Offer> offers(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return Recruiting.variants(player.getServer(), RecruitConfig.callinCost(), RecruitConfig.callinDelay());
    }
}
