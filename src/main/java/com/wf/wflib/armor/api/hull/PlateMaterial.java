package com.wf.wflib.armor.api.hull;

/**
 * Per-mm armour value of a plate material vs one mm of RHA. {@code ce}: shaped charges; {@code hesh}: transmission
 * fraction of a squash-head blast; {@code spall}: spall cone/count scale.
 */
public record PlateMaterial(String id, float ke, float ce, float hesh, float spall) {

    public PlateMaterial {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("plate material needs an id");
        }
        if (!(ke >= 0) || !(ce >= 0) || !(hesh >= 0) || !(spall >= 0)) {
            throw new IllegalArgumentException("plate material " + id + ": negative or NaN factor");
        }
    }
}
