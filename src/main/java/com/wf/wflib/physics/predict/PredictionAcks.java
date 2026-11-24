package com.wf.wflib.physics.predict;

import com.wf.wflib.physics.api.Predictable;
import com.wf.wflib.physics.api.RegionHost;
import com.wf.wflib.physics.step.RigidStep;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Server sender: snapshots after the body's step. Main-thread dues at {@code TickOrder.PREDICTION}; region-thread dues
 * (region host on) after that region's partition step, same thread.
 */
public final class PredictionAcks {

    private static final List<Predictable<?>> DUE = new ArrayList<>();
    private static final ThreadLocal<RegionDue> REGION_DUE = ThreadLocal.withInitial(RegionDue::new);

    private static final class RegionDue {
        final List<Predictable<?>> bodies = new ArrayList<>();
        final IntArrayList regions = new IntArrayList();
    }

    private PredictionAcks() {
    }

    public static void due(Predictable<?> body) {
        RegionHost host = RigidStep.regionHost();
        int region = host == null ? -1 : host.currentRegion();
        if (region < 0) {
            DUE.add(body);
            return;
        }
        RegionDue due = REGION_DUE.get();
        due.bodies.add(body);
        due.regions.add(region);
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
            send(body);
        }
    }

    /** Region thread, its tick end. Entries of another region / level = a failed earlier region tick: dropped. */
    public static void sendRegion(ServerLevel level, int region) {
        RegionDue due = REGION_DUE.get();
        if (due.bodies.isEmpty()) {
            return;
        }
        for (int i = 0, size = due.bodies.size(); i < size; i++) {
            Predictable<?> body = due.bodies.get(i);
            if (due.regions.getInt(i) == region && body.level() == level) {
                send(body);
            }
        }
        due.bodies.clear();
        due.regions.clear();
    }

    private static void send(Predictable<?> body) {
        if (!body.isRemoved() && body.predictionOperator() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new PredictionState(body.getId(), body.inputSeq(), body.snapshot()));
        }
    }
}
