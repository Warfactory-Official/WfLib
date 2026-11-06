package com.wf.wflib.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

final class UiKeys {
    static final KeyMapping KEY_HINTS = new KeyMapping("key.wflib.key_hints", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.wflib");

    private UiKeys() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(KEY_HINTS));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            while (KEY_HINTS.consumeClick()) {
                UiClientConfig.toggleKeyHints();
            }
        });
    }
}
