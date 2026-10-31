package com.wf.wflib.particle.xr;

import com.wf.wflib.particle.api.XrGroups;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XrGroupsTest {
    /** TODO: some shipped groups name effects the asset tool never copied (distortion, a few eugenium idles); XrParticles skips them. */
    private static int found;
    private static final Path ASSETS = Path.of("src/main/resources/assets/wflib");

    private static Path file(ResourceLocation location) {
        return ASSETS.resolve(location.getPath());
    }

    private static List<String> keys() throws IllegalAccessException {
        List<String> keys = new ArrayList<>();
        for (Field f : XrGroups.class.getFields()) {
            keys.add((String) f.get(null));
        }
        return keys;
    }

    @Test
    void everyGroupKeyResolvesToEffectsAndTextures() throws Exception {
        List<String> keys = keys();
        assertFalse(keys.isEmpty());
        for (String key : keys) {
            Path pg = file(XrDefs.location(key, "pg"));
            assertTrue(Files.exists(pg), key + ": no " + pg);
            XrDefs.Group group = XrDefs.Group.parse(Files.readString(pg));
            assertFalse(group.entries().isEmpty(), key + " has no effects");
            int before = found;
            for (XrDefs.GroupEntry entry : group.entries()) {
                for (String name : new String[] {entry.effect(), entry.birthChild(), entry.playChild(),
                        entry.deadChild()}) {
                    if (name != null) {
                        effect(key, name);
                    }
                }
            }
            assertTrue(found > before, key + " resolves no effect");
        }
    }

    private static void effect(String key, String name) throws IOException {
        Path pe = file(XrDefs.location(name, "pe"));
        if (!Files.exists(pe)) {
            return;
        }
        found++;
        XrDefs.Effect effect = XrDefs.Effect.parse(name, Files.readString(pe));
        if (effect.texture() != null) {
            assertTrue(Files.exists(file(effect.texture())), key + ": " + name + " has no texture " + effect.texture());
        }
    }

    @Test
    void keysAreUnique() throws IllegalAccessException {
        List<String> keys = keys();
        assertTrue(keys.size() == keys.stream().distinct().count());
    }
}
