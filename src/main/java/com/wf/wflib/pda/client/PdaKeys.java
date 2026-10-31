package com.wf.wflib.pda.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import com.wf.wflib.core.api.WFLib;

/** Unbound by default: choose bindings under Controls > WFLib without taking another action's key. */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class PdaKeys {
    public static final KeyMapping TOGGLE = key("pda_toggle");
    public static final KeyMapping TERMINAL = key("pda_terminal");
    public static final KeyMapping PLUGINLESS = key("pda_pluginless");

    private PdaKeys() {
    }

    private static KeyMapping key(String name) {
        return new KeyMapping("key.wflib." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), "key.categories.wflib");
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(TERMINAL);
        event.register(PLUGINLESS);
    }
}
