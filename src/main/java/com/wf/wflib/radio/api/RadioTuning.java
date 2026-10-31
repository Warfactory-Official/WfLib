package com.wf.wflib.radio.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public final class RadioTuning {
    /** Remote control of a carrier the sender does not ride (a PDA-linked body). */
    @FunctionalInterface
    public interface Link {
        boolean allows(ServerPlayer player, Entity carrier);
    }

    public static final OrderedRegistry<Link> LINKS = OrderedRegistry.create("radio.links");

    private RadioTuning() {
    }

    /** Sender aboard the carrier, or a {@link #LINKS} entry allows it. @return true if tuned. */
    public static boolean tune(ServerPlayer player, UUID id, String station) {
        Entity e = player.serverLevel().getEntity(id);
        if (!(e instanceof RadioCarrier carrier) || carrier.radioVoice() == null
                || !RadioCarrier.validStation(station) || !allowed(player, e)) {
            return false;
        }
        carrier.tuneRadio(station);
        return true;
    }

    private static boolean allowed(ServerPlayer player, Entity e) {
        if (player.getRootVehicle() == e.getRootVehicle() && player.getVehicle() != null) {
            return true;
        }
        for (Link link : LINKS.values()) {
            if (link.allows(player, e)) {
                return true;
            }
        }
        return false;
    }
}
