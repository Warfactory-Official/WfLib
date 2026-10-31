package com.wf.wflib.fire.api;

import com.wf.wflib.fire.FireSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import com.wf.wflib.core.api.WFLib;

/**
 * Public entry point for the custom-fire system: the per-entity {@link WFFireData} data attachment plus the
 * convenience methods other systems use to set things alight ({@code WFFire.ignite(entity, FireType.PHOSPHORUS,
 * 200)}).
 */
public final class WFFire {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "fire");

    private WFFire() {
    }

    /**
     * @return the entity's fire state (data attachments are created lazily, so this is never {@code null})
     */
    public static WFFireData get(LivingEntity entity) {
        return entity.getData(FireSystem.FIRE_DATA);
    }

    /**
     * Sets an entity alight with custom fire. Server-side.
     */
    public static void ignite(LivingEntity entity, FireType type, int ticks) {
        get(entity).ignite(type, ticks);
    }
}
