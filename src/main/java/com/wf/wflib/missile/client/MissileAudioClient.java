package com.wf.wflib.missile.client;

import com.wf.wflib.missile.MissileFlightAudioPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import com.wf.wflib.core.api.WFLib;


@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class MissileAudioClient {

    private static final Map<UUID, RemoteMissileFlightSound> ACTIVE = new java.util.HashMap<>();

    private MissileAudioClient() {
    }

    /** Client main thread only; {@code ACTIVE} isn't synchronized. */
    public static void upsert(MissileFlightAudioPacket pkt) {
        RemoteMissileFlightSound existing = ACTIVE.get(pkt.id());
        if (existing != null && !existing.isDone()) {
            existing.update(pkt);
            return;
        }
        RemoteMissileFlightSound instance = new RemoteMissileFlightSound(pkt);
        ACTIVE.put(pkt.id(), instance);
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<Map.Entry<UUID, RemoteMissileFlightSound>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isDone()) {
                it.remove();
            }
        }
    }
}
