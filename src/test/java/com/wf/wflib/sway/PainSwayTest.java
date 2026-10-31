package com.wf.wflib.sway;

import com.wf.wflib.sway.api.PainSway;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PainSwayTest {
    @Test
    void oneSourceFloorWhileAimingSecondSetThrows() {
        assertEquals(0, PainSway.intensity(null, true));
        assertEquals(0, PainSway.amplitudeDeg());
        PainSway.SOURCE.set("a", new PainSway.Source() {
            @Override
            public float intensity(Player p) {
                return 0.2F;
            }

            @Override
            public float aimFloor(Player p) {
                return 0.6F;
            }

            @Override
            public float amplitudeDeg() {
                return 2.5F;
            }
        });
        assertEquals(0.2F, PainSway.intensity(null, false));
        assertEquals(0.6F, PainSway.intensity(null, true));
        assertEquals(2.5F, PainSway.amplitudeDeg());
        assertThrows(IllegalStateException.class, () -> PainSway.SOURCE.set("b", PainSway.SOURCE.get()));
    }

    @Test
    void wobbleAddsNothingWithoutPain() {
        float[] out = {0.25F, -0.5F};
        PainSway.wobble(0, 10, 1, out);
        PainSway.wobble(-1, 10, 1, out);
        assertEquals(0.25F, out[0]);
        assertEquals(-0.5F, out[1]);
    }
}
