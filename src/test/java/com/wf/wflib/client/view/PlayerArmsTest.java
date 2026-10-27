package com.wf.wflib.client.view;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerArmsTest {
    @Test
    void armouredForearmFillsTheElbowWideBoxToTheFist() {
        // Skin section corner at the fist end (0.085 x 0.090 m) lands on the armour box's (0.105 m square).
        Vector3f fist = PlayerArms.fitArmour(new Matrix4f(), 1).transformPosition(new Vector3f(0.0425F, 0.3F, 0.045F));
        assertEquals(0.0525F, fist.x, 1e-6F);
        assertEquals(0.3F, fist.y, 1e-6F);
        assertEquals(0.0525F, fist.z, 1e-6F);
        // Elbow: already as wide as the box, untouched.
        Vector3f elbow = PlayerArms.fitArmour(new Matrix4f(), 0).transformPosition(new Vector3f(0.0525F, 0, 0.0525F));
        assertEquals(0.0525F, elbow.x, 1e-6F);
        assertEquals(0.0525F, elbow.z, 1e-6F);
    }
}
