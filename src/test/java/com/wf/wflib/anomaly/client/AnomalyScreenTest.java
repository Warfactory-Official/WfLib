package com.wf.wflib.anomaly.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnomalyScreenTest {
    @Test
    void rampsFromRadiusMaxToRadiusMin() {
        assertEquals(0.0F, AnomalyScreen.factor(10.0D, 10.0D, 0.3F, 1.0F));
        assertEquals(1.0F, AnomalyScreen.factor(3.0D, 10.0D, 0.3F, 1.0F), 1e-6F);
        assertEquals(1.0F, AnomalyScreen.factor(0.0D, 10.0D, 0.3F, 1.0F), 1e-6F);
        assertEquals(0.5F, AnomalyScreen.factor(6.5D, 10.0D, 0.3F, 1.0F), 1e-6F);
        assertEquals(0.01F, AnomalyScreen.factor(9.9999D, 10.0D, 0.3F, 1.0F), 1e-6F);
    }
}
