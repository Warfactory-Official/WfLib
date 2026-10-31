package com.wf.wflib.probe.client;

import com.wf.wflib.probe.api.ProbeElement;
import com.wf.wflib.probe.api.ProbeProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.wf.wflib.core.api.WFLib;

/** What the probe says about a thing nothing else described: its name, and its icon beside it. */
public final class ProbeDefaults {

    private ProbeDefaults() {
    }

    public static void register() {
        ProbeProviders.addAnyBlock(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "default_block"), (info, ctx, state, pos, blockEntity) -> {
            info.titleFallback(state.getBlock().getName());
            ItemStack pick = state.getCloneItemStack(ctx.hit(), ctx.level(), pos, ctx.player());
            if (!pick.isEmpty()) {
                info.titleIconFallback(new ProbeElement.Icon(pick, 12, false));
            }
        }, ProbeProviders.DEFAULT_PRIORITY);

        ProbeProviders.addAnyEntity(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "default_entity"), (info, ctx, entity) -> {
            info.titleFallback(entity.getDisplayName());
            if (entity instanceof LivingEntity living) {
                float health = living.getHealth();
                float max = Math.max(1.0f, living.getMaxHealth());
                info.bar(health / max, 90, 0xFFCC3333,
                        Component.literal(round(health) + " / " + round(max)));
            }
        }, ProbeProviders.DEFAULT_PRIORITY);
    }

    private static String round(float value) {
        return value >= 10.0f ? String.valueOf(Math.round(value)) : String.format("%.1f", value);
    }
}
