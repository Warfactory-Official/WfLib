package com.wf.wflib.wiki.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.wiki.api.Article;
import com.wf.wflib.wiki.api.Wiki;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Article / inline image drawing: item particle sprites and textures, sizes read once per reload. */
final class WikiIcons {
    private static final float[] NONE = new float[0];
    private static final Map<Item, TextureAtlasSprite> SPRITES = new HashMap<>();
    private static final Map<ResourceLocation, float[]> SIZES = new HashMap<>();

    private WikiIcons() {
    }

    static void clear() {
        SPRITES.clear();
        SIZES.clear();
    }

    static void draw(Canvas canvas, Article.Icon icon, float x, float y, float size) {
        if (icon.texture() != null) {
            canvas.sprite(icon.texture(), x, y, x + size, y + size, -1);
        } else {
            item(canvas, icon.item(), x, y, size);
        }
    }

    static void item(Canvas canvas, ResourceLocation id, float x, float y, float size) {
        Item item = Wiki.item(id);
        if (item == null) {
            canvas.fill(x, y, x + size, y + size, 0xFF8A3A3A);
            return;
        }
        TextureAtlasSprite sprite = SPRITES.get(item);
        if (sprite == null) {
            sprite = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(item)
                    .getParticleIcon(ModelData.EMPTY);
            // An item drawn by a special renderer has no particle of its own (a mod's reactor columns): its block still does.
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) && item instanceof BlockItem block) {
                sprite = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(block.getBlock().defaultBlockState());
            }
            SPRITES.put(item, sprite);
        }
        canvas.sprite(sprite, x, y, x + size, y + size, -1);
    }

    /** Texture size in px, null if unreadable. */
    static float[] size(ResourceLocation texture) {
        float[] size = SIZES.get(texture);
        if (size == null) {
            size = NONE;
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                    size = new float[]{image.getWidth(), image.getHeight()};
                } catch (Exception e) {
                    LogUtils.getLogger().warn("Wiki image {}: unreadable: {}", texture, e.toString());
                }
            }
            SIZES.put(texture, size);
        }
        return size == NONE ? null : size;
    }
}
