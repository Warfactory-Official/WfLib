package com.wf.wflib.skin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.ArmorRig;
import com.wf.gemrender.direct.GemRenderArmorModel;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfImporter;
import com.wf.wflib.skin.Skinnable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One model per {@link Skinnable.Skin}, drawn with {@code models.get(Skins.index(stack))}: each substitutes the
 * model's own base colour (+ emissive) texture, read from the glTF's first textured material. Not variants of one
 * sheet: a sheet caps at 4096 px (14 skins of 1024 px do not fit) and wrapping textures cannot go on one.
 */
public final class SkinModels {
    private static final Map<List<Object>, List<ModelCache.Handle<GemRenderGltfModel>>> HANDLES = new HashMap<>();

    private SkinModels() {
    }

    /** Held / inventory / dropped models, skin i at i. */
    public static List<ModelCache.Handle<GemRenderGltfModel>> plain(ResourceLocation asset,
                                                                   List<Skinnable.Skin> skins) {
        return handles(asset, skins, false);
    }

    /** Worn models: also patched with the installed {@link ArmorRig} (elbow / knee cuts), as {@link GemRenderModels#armor}. */
    public static List<ModelCache.Handle<GemRenderGltfModel>> armor(ResourceLocation asset,
                                                                   List<Skinnable.Skin> skins) {
        return handles(asset, skins, true);
    }

    private static synchronized List<ModelCache.Handle<GemRenderGltfModel>> handles(ResourceLocation asset,
                                                                                    List<Skinnable.Skin> skins,
                                                                                    boolean worn) {
        return HANDLES.computeIfAbsent(List.of(asset, skins, worn), k -> skins.stream().map(skin -> {
            // Two items may skin one asset differently: the skin is part of the model's id.
            ResourceLocation id = asset.withPath(p -> p + (worn ? "/armor" : "") + "/skin_"
                    + Integer.toHexString(skin.hashCode()));
            return GemRenderModels.built(id, built -> {
                ResourceLocation[] own = textures(gltf(asset));
                if (own[0] == null) {
                    throw new IOException(asset + ": skins need a base colour texture with extras.resourceLocation");
                }
                ArmorRig rig = worn ? GemRenderArmorModel.rig() : null;
                return GltfImporter.load(asset, built, List.of(swaps(own, skin)), rig == null ? null
                        : r -> rig.patch(r, GemRenderArmorModel.DEFAULT_BONES));
            });
        }).toList());
    }

    /** {@code own} = {@link #textures}, swapped for {@code skin}'s (unchanged ones left out). */
    static Map<ResourceLocation, ResourceLocation> swaps(ResourceLocation[] own, Skinnable.Skin skin) {
        Map<ResourceLocation, ResourceLocation> m = new HashMap<>();
        if (!skin.texture().equals(own[0])) {
            m.put(own[0], skin.texture());
        }
        if (skin.glow() != null && own[1] != null && !skin.glow().equals(own[1])) {
            m.put(own[1], skin.glow());
        }
        return Map.copyOf(m);
    }

    /** {base colour, emissive} image locations of the first textured material; null = none. */
    static ResourceLocation[] textures(JsonObject g) {
        for (JsonElement mat : array(g, "materials")) {
            JsonObject pbr = obj(mat, "pbrMetallicRoughness");
            JsonObject base = pbr == null ? null : obj(pbr, "baseColorTexture");
            if (base != null) {
                JsonObject emissive = obj(mat, "emissiveTexture");
                return new ResourceLocation[]{image(g, base), emissive == null ? null : image(g, emissive)};
            }
        }
        return new ResourceLocation[2];
    }

    @Nullable
    private static ResourceLocation image(JsonObject g, JsonObject ref) {
        JsonObject tex = array(g, "textures").get(ref.get("index").getAsInt()).getAsJsonObject();
        JsonObject extras = obj(array(g, "images").get(tex.get("source").getAsInt()), "extras");
        return extras == null || !extras.has("resourceLocation") ? null
                : ResourceLocation.tryParse(extras.get("resourceLocation").getAsString());
    }

    /** The asset's JSON: a .gltf, or a .glb's first chunk. */
    private static JsonObject gltf(ResourceLocation asset) throws IOException {
        byte[] raw;
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(asset).open()) {
            raw = in.readAllBytes();
        }
        String json;
        if (raw.length >= 20 && raw[0] == 'g' && raw[1] == 'l' && raw[2] == 'T' && raw[3] == 'F') {
            ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            json = new String(raw, 20, b.getInt(12), StandardCharsets.UTF_8);
        } else {
            json = new String(raw, StandardCharsets.UTF_8);
        }
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonArray array(JsonObject o, String k) {
        return o.get(k) instanceof JsonArray a ? a : new JsonArray();
    }

    @Nullable
    private static JsonObject obj(JsonElement o, String k) {
        return o instanceof JsonObject j && j.get(k) instanceof JsonObject v ? v : null;
    }
}
