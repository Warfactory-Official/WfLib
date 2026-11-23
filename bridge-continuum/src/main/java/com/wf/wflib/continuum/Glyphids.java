package com.wf.wflib.continuum;

import com.wf.wflib.colony.api.EntityGlyphid;
import dev.golem.core.world.Spawns;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * WFLib's colony replaces Continuum's glyphids (docs, 3.3): Continuum's glyphids no longer spawn naturally (a Golem natural-spawn veto on every `hbm:entity_glyphid*` type; the hive world
 * feature is switched off in {@link BridgeEntry#substitute}), and WFLib's glyphids drop what Continuum's did, with its odds: half the time, twice the
 * body scale in meat plus looting, grilled if the glyphid died on fire. Eggs, spawners and hive blocks stay Continuum's items and decor.
 */
final class Glyphids {
    private Glyphids() {
    }

    static void install() {
        Spawns.onSpawnCheck((mob, level, reason) -> {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            return id.getNamespace().equals("hbm") && id.getPath().contains("glyphid");   // entity_glyphid, entity_glyphid_behemoth, ...
        });
        EntityGlyphid.DROPS.add((glyphid, level, looting) -> {
            if (glyphid.getRandom().nextInt(2) != 0) return;
            Item meat = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("hbm", glyphid.isOnFire() ? "glyphid_meat_grilled" : "glyphid_meat"));
            if (meat == net.minecraft.world.item.Items.AIR) return;
            glyphid.spawnAtLocation(new ItemStack(meat, (int) glyphid.getGlyphidScale() * 2 + looting));
        });
    }
}
