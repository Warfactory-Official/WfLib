package com.wf.wflib.cam.api;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.core.api.WFLib;

/** Holders dependents name; registered by {@code CamSystem}. */
public final class CameraContent {
    /** The bound cameras on a handheld receiver. The monitor block keeps the same type in its own NBT. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CameraChannels>> CHANNELS =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE,
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "camera_channels"));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block>
            CAMERA_MONITOR = DeferredHolder.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "camera_monitor"));

    private CameraContent() {
    }
}
