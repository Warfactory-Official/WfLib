package com.wf.wflib.skin.api.client;

import com.google.gson.JsonParser;
import com.wf.wflib.skin.api.Skinnable;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinModelsTest {
    private static ResourceLocation rl(String s) {
        return ResourceLocation.parse(s);
    }

    @Test
    void readsBaseAndEmissiveImagesOfTheFirstTexturedMaterial() {
        var g = JsonParser.parseString("""
                {"images": [{"extras": {"resourceLocation": "p:textures/a.png"}},
                            {"extras": {"resourceLocation": "p:textures/a_glow.png"}}],
                 "textures": [{"source": 0}, {"source": 1}],
                 "materials": [{"name": "plain"},
                               {"pbrMetallicRoughness": {"baseColorTexture": {"index": 0}},
                                "emissiveTexture": {"index": 1}}]}""").getAsJsonObject();
        assertArrayEquals(new ResourceLocation[]{rl("p:textures/a.png"), rl("p:textures/a_glow.png")},
                SkinModels.textures(g));
    }

    @Test
    void eachSkinSwapsOnlyWhatDiffers() {
        ResourceLocation[] own = {rl("p:textures/a.png"), rl("p:textures/a_glow.png")};
        assertEquals(Map.of(), SkinModels.swaps(own, new Skinnable.Skin("A", rl("p:textures/a.png"), null)));
        assertEquals(Map.of(own[0], rl("p:textures/b.png")),
                SkinModels.swaps(own, new Skinnable.Skin("B", rl("p:textures/b.png"), null)));
        assertEquals(Map.of(own[0], rl("p:textures/c.png"), own[1], rl("p:textures/c_glow.png")),
                SkinModels.swaps(own, new Skinnable.Skin("C", rl("p:textures/c.png"), rl("p:textures/c_glow.png"))));
    }
}
