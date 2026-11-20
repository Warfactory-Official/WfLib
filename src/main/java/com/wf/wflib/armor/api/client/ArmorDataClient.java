package com.wf.wflib.armor.api.client;

import com.wf.wflib.armor.api.ArmorAssignment;
import com.wf.wflib.armor.api.ArmorProfiles;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

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

    /** Armour another mod made, given a profile by data, shows the same numbers WFLib's own pieces do; its own pieces already do it themselves. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var item = event.getItemStack().getItem();
        if (item instanceof com.wf.wflib.armor.api.ArmorPieceItem || item instanceof com.wf.wflib.armor.ArmorInsertItem) return;
        com.wf.wflib.armor.ArmorTooltip.append(event.getItemStack(), event.getToolTip(), event.getFlags());
    }

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        ArmorProfiles.tagsChanged();
    }
}
