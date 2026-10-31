package com.wf.wflib.cam.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** Entity feeds beyond TV rounds. Server side; first non-null by priority wins. */
public final class CameraSources {
    /** Provider: the entity's camera, or null if it has none (now). */
    public static final OrderedRegistry<Function<Entity, CameraSource>> PROVIDERS =
            OrderedRegistry.create("cam", "cam.sources");

    private CameraSources() {
    }

    @Nullable
    static CameraSource of(Entity entity) {
        for (Function<Entity, CameraSource> provider : PROVIDERS.values()) {
            CameraSource source = provider.apply(entity);
            if (source != null) {
                return source;
            }
        }
        return null;
    }
}
