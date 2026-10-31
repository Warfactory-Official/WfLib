package com.wf.wflib.explosion.api;


/** Entity-damage radius, independent of block-damage radius. */
public interface IEntityRangeMutator {

    float mutateRange(ExplosionAEF explosion, float range);
}
