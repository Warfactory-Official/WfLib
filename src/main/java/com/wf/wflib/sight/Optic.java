package com.wf.wflib.sight;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Magnified optic channel ({@code client.sight.PipLens}): zoom levels, reticle (null => duplex), reticle width MOA
 * ({@code <= 0} => lens fill), second focal plane zoom ({@code <= 0} => first), mask, eye relief (null => eye at
 * full ADS).
 */
public interface Optic {
    List<Float> zoom();

    @Nullable
    ResourceLocation reticle();

    float reticleSize();

    float reticleZoom();

    @Nullable
    ResourceLocation mask();

    @Nullable
    Float eyeRelief();
}
