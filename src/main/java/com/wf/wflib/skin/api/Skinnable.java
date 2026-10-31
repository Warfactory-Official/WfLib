package com.wf.wflib.skin.api;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Equipment wearing one of several looks: the same model in other textures, picked per stack ({@link Skins#SKIN})
 * from the skin screen. Opt-in: an item implements this and lists its skins; nothing is skinnable by default.
 * Rendering follows through {@link com.wf.wflib.skin.api.client.SkinModels} (armour: {@code ArmorPieceClient}).
 */
public interface Skinnable {

    /** Looks in screen order; the first is an unskinned stack's. Fewer than two => nothing to choose. */
    List<Skin> skins();

    /**
     * @param texture replaces the model's base colour texture; same size
     * @param glow    replaces its emissive texture; null => the model's own
     */
    record Skin(String name, ResourceLocation texture, @Nullable ResourceLocation glow) {
    }
}
