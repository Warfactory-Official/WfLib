package com.wf.wflib.probe.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Two bindings, and only two. */
public final class ProbeKeys {

    private static final String CATEGORY = "key.categories.wflib";

    /** Hold to make the scroll wheel move the probe's action list. */
    public static final KeyMapping MODIFIER = new KeyMapping(
            "key.wflib.probe_modifier", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, CATEGORY);

    /** Press to perform whichever action is selected. */
    public static final KeyMapping ACTIVATE = new KeyMapping(
            "key.wflib.probe_activate", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY);

    private ProbeKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(MODIFIER);
        event.register(ACTIVATE);
    }
}
