package com.wf.wflib.ui.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Client skin already streamed for a player's model; nothing uploaded or owned here. */
public final class PlayerSkins {
    public record Skin(ResourceLocation texture, boolean slim) {
    }

    private static final Skin DEFAULT = new Skin(DefaultPlayerSkin.getDefaultTexture(), false);

    private PlayerSkins() {
    }

    /** {@code entityId < 0} = local player; not a tracked client player => default skin. */
    public static Skin forEntity(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = entityId < 0 ? mc.player : mc.level == null ? null : mc.level.getEntity(entityId);
        if (entity instanceof AbstractClientPlayer player) {
            PlayerSkin skin = player.getSkin();
            return new Skin(skin.texture(), skin.model() == PlayerSkin.Model.SLIM);
        }
        return DEFAULT;
    }
}
