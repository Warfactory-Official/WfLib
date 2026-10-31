package com.wf.wflib.missile.api;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/** The defusal gesture shared by mines and dud missiles. */
public final class Defusal {

    private Defusal() {
    }

    /** @return why a {@code method}/{@code tool} defusal cannot be started, or null if it can. */
    @Nullable
    public static Component refusal(Player player, DefuseMethod method, @Nullable TagKey<Item> tool) {
        if (method == DefuseMethod.NONE) {
            return Component.translatable("probe.wflib.mine.cannot_defuse");
        }
        if (method == DefuseMethod.TOOL && tool != null
                && !player.getMainHandItem().is(tool)
                && !player.getOffhandItem().is(tool)) {
            return Component.translatable("probe.wflib.mine.need_defuser");
        }
        if (!player.isCrouching()) {
            return Component.translatable("probe.wflib.mine.need_crouch");
        }
        return null;
    }
}
