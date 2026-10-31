package com.wf.wflib.physics.api;

/**
 * What a collision piece is beyond its shape.
 *
 * @param name  debug label only
 * @param solid false => shot at and bounds the hull, but not stood on or collided with (mudguard, aerial)
 * @param world false => collides with vehicles only
 */
public record HullTag(String name, boolean solid, boolean world) {

    public static final HullTag DEFAULT = new HullTag("", true, true);

    public HullTag {
        name = name == null ? "" : name;
    }
}
