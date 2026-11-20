package com.wf.wflib.armor.api.client;

import com.wf.wflib.armor.api.ArmorAssignment;
import com.wf.wflib.armor.api.ArmorProfiles;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.List;

/** A remote client builds its armour profile table off the synced {@code wflib:armor_profile} registry; an integrated server already did. */
public final class ArmorDataClient {
    private ArmorDataClient() {
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            ArmorProfiles.applyData(event.getPlayer().registryAccess().registry(ArmorAssignment.REGISTRY)
                    .map(r -> r.stream().toList()).orElse(List.of()));
        }
    }

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        ArmorProfiles.tagsChanged();
    }
}
