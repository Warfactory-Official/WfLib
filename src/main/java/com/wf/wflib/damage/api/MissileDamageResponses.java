package com.wf.wflib.damage.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;

import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** Named {@link MissileDamageResponse}s, keyed by {@link ResourceLocation}. */
public final class MissileDamageResponses {

    /** Pass-through: the missile takes damage exactly as dealt (vanilla behaviour, the default). */
    public static final MissileDamageResponse STANDARD = (missile, source, amount) -> amount;

    /** Immune to everything except explosion damage: the canonical "hardened, blast-only" airframe. */
    public static final MissileDamageResponse EXPLOSION_ONLY = (missile, source, amount) ->
            source.is(DamageTypeTags.IS_EXPLOSION) ? amount : 0.0f;

    /** Takes no damage from any source in flight (still detonates on impact / when it reaches its target). */
    public static final MissileDamageResponse INVULNERABLE = (missile, source, amount) -> 0.0f;

    private static final ResourceLocation DEFAULT_ID = rl("standard");
    private static final WFRegistry<MissileDamageResponse> RESPONSES = WFRegistry.create("missile damage responses");

    private MissileDamageResponses() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) {
            return DEFAULT_ID;
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null ? parsed : DEFAULT_ID;
    }

    public static void register(ResourceLocation id, MissileDamageResponse response) {
        RESPONSES.register(id, response);
    }

    public static MissileDamageResponse get(ResourceLocation id) {
        MissileDamageResponse response = RESPONSES.get(id);
        return response != null ? response : STANDARD;
    }

    public static boolean exists(ResourceLocation id) {
        return RESPONSES.containsKey(id);
    }

    public static Set<ResourceLocation> ids() {
        return RESPONSES.ids();
    }

    public static ResourceLocation defaultId() {
        return DEFAULT_ID;
    }
}
