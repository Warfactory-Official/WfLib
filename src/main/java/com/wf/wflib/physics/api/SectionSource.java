package com.wf.wflib.physics.api;

import org.jetbrains.annotations.Nullable;

/** Collision query's section source: live or frozen snapshot for one vehicle tick. Missing section reads empty. */
public interface SectionSource {

    @Nullable
    SectionCollision section(long key);

}
