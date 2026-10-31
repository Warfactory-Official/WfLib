package com.wf.wflib.recon.api;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** What an entity is carrying that changes how it presents to a sensor. */
public interface CountermeasureProvider {

    /**
     * @return what this entity carries, or null to mean "not mine, try the next". An empty array is a
     *      different answer from null: it claims the entity and says it carries nothing.
     */
    @Nullable
    Countermeasure[] of(Entity entity);
}
