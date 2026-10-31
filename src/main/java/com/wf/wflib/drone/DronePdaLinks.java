package com.wf.wflib.drone;

import com.wf.wflib.cam.api.CameraChannels;
import com.wf.wflib.cam.api.CameraContent;
import com.wf.wflib.cam.api.FeedTarget;
import com.wf.wflib.drone.api.ControllableDrone;
import com.wf.wflib.drone.api.DroneTracker;
import com.wf.wflib.drone.api.PdaDrones;
import com.wf.wflib.pda.api.Pda;
import com.wf.wflib.pda.api.TerminalNoun;
import com.wf.wflib.radio.api.RadioTuning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Drones reached through the held PDA's channels: Terminal noun, radio tuning. */
final class DronePdaLinks {
    private DronePdaLinks() {
    }

    static final RadioTuning.Link RADIO = (player, carrier) -> {
        if (!PdaDrones.channels(Pda.carriedStack(player)).has(FeedTarget.ofDrone(carrier.getUUID()))) {
            return false;
        }
        ControllableDrone body = DroneTracker.byId(player.serverLevel(), carrier.getUUID());
        return body == null || !body.orders().isClassified();
    };

    static final TerminalNoun TERMINAL = new TerminalNoun() {
        @Override
        public String plural() {
            return "drones";
        }

        @Override
        public String singular() {
            return "drone";
        }

        @Override
        public String none() {
            return "no linked drones in range";
        }

        /** Held PDA's channel drones, loaded, channel order. */
        @Override
        public List<Entry> entries(ServerPlayer player) {
            CameraChannels channels = Pda.carriedStack(player).getOrDefault(CameraContent.CHANNELS.get(),
                    CameraChannels.EMPTY);
            List<Entry> out = new ArrayList<>();
            for (FeedTarget target : channels.targets()) {
                UUID id = target.drone().orElse(null);
                ControllableDrone drone = id == null ? null : DroneTracker.byId(player.serverLevel(), id);
                if (drone != null && drone.carrierAlive()) {
                    out.add(entry(drone));
                }
            }
            return out;
        }

        private Entry entry(ControllableDrone d) {
            return new Entry() {
                @Override
                public String name() {
                    return d.callsign();
                }

                @Override
                public String line(ServerPlayer viewer) {
                    Entity body = d.asEntity();
                    return d.callsign() + "  " + d.flight().getDroneState().name() + "  "
                            + (int) viewer.distanceTo(body) + " m";
                }

                @Override
                public String rename(ServerPlayer player, @Nullable String name) {
                    d.setCallsign(name);
                    return "drone renamed " + d.callsign();
                }
            };
        }
    };
}
