package com.wf.wflib.ui.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Texture-manager entry under a fixed location that shows whichever GL texture was {@link #adopt adopted} last, so a
 * widget with a fixed {@code ResourceLocation} can draw a changing picture. Never deletes the id. Render thread only.
 */
public final class BorrowedTexture extends AbstractTexture {
    private final ResourceLocation location;
    private boolean registered;

    public BorrowedTexture(ResourceLocation location) {
        this.location = location;
    }

    public ResourceLocation location() {
        return location;
    }

    /** {@code -1} = nothing. */
    public void adopt(int glId) {
        if (!registered) {
            Minecraft.getInstance().getTextureManager().register(location, this);
            registered = true;
        }
        this.id = glId;
    }

    @Override
    public void load(ResourceManager manager) {
    }

    @Override
    public void releaseId() {
        this.id = -1;
    }

    @Override
    public int getId() {
        return this.id;
    }
}
