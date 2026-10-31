package com.wf.wflib.physics.api;

import com.wf.wflib.core.api.OrderedRegistry;

/** Register during mod construction or setup; frozen at load complete. */
public final class CollisionProviders {

    public static final OrderedRegistry<CollisionProvider> PROVIDERS = OrderedRegistry.create("physics", "physics.collision_providers");

    private CollisionProviders() {}

}
