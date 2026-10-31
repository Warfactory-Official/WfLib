package com.wf.wflib.physics.predict;

import com.wf.wflib.physics.api.Predictable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Server sender: snapshots after the level's step, at {@code TickOrder.PREDICTION}. */
public final class PredictionAcks {

    private static final List<Predictable<?>> DUE = new ArrayList<>();

    private PredictionAcks() {
    }

    public static void due(Predictable<?> body) {
        DUE.add(body);
    }

    public static void sendDue(ServerLevel level) {
        if (DUE.isEmpty()) {
            return;
        }
        for (Iterator<Predictable<?>> it = DUE.iterator(); it.hasNext(); ) {
            Predictable<?> body = it.next();
            if (body.level() != level) {
                continue;
            }
            it.remove();
            if (!body.isRemoved() && body.predictionOperator() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new PredictionState(body.getId(), body.inputSeq(), body.snapshot()));
            }
        }
    }
}
