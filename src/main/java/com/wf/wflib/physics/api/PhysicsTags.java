package com.wf.wflib.physics.api;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class PhysicsTags {

    /** Excluded from the chunk collision bake and live probes; bodies pass through. */
    public static final TagKey<Block> VEHICLE_GHOST = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "vehicle_ghost"));

    private PhysicsTags() {
    }
}
