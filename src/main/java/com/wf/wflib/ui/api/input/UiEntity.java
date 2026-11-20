package com.wf.wflib.ui.api.input;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Entity an {@code EntityView} draws; {@code item} = the repeat item (null outside a repeat); null => nothing drawn. */
@FunctionalInterface
public interface UiEntity {
    @Nullable
    LivingEntity entity(Object item);
}
