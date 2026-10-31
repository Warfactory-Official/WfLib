package com.wf.wflib.hazard.client;

import com.wf.wflib.artifact.api.Artifact;
import com.wf.wflib.xray.api.XrScreen;
import com.wf.wflib.hazard.api.LivingHazardProps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.wf.wflib.core.api.WFLib;

/**
 * GAMMA's radiation on the screen ({@code actor_effects.script}): past 0.3 of a lethal dose {@code alcohol.ppe}
 * looped, past 0.45 {@code radiation.ppe} too. Dose in STALKER units: {@link Artifact#RAD_PER_UNIT} RAD each.
 */
public final class RadiationScreen {
    private static final String SWAY = "wflib:radiation/alcohol";
    private static final String RAD = "wflib:radiation/radiation";

    private RadiationScreen() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        double units = player == null ? 0.0D : LivingHazardProps.getRadiation(player) / Artifact.RAD_PER_UNIT;
        toggle(SWAY, "alcohol", units > 0.3D);
        toggle(RAD, "radiation", units > 0.45D);
    }

    private static void toggle(String id, String ppe, boolean on) {
        if (on) {
            XrScreen.post(id, XrScreen.ppe(WFLib.MODID, ppe), true, 1.0F);
        } else if (XrScreen.running(id)) {
            XrScreen.stop(id, 0.5F);
        }
    }
}
