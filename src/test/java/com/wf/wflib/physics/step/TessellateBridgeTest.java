package com.wf.wflib.physics.step;

import com.wf.wflib.physics.api.PhysicsBody;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TessellateBridgeTest {

    private static final ClassLoader LOADER = TessellateBridgeTest.class.getClassLoader();

    @Test
    void absentApiBindsNothing() {
        assertNull(TessellateBridge.bind("org.example.absent.Api", TessellateBridge.CONTEXT, LOADER));
    }

    @Test
    void realApiIsAbsentFromTheTestClasspath() {
        assertNull(TessellateBridge.bind(TessellateBridge.API, TessellateBridge.CONTEXT, LOADER));
    }

    @Test
    void boundBridgeReadsTheRegionAndForwardsWork() {
        TessellateBridge bridge = TessellateBridge.bind(StubRegionApi.class.getName(),
                StubRegionApi.Context.class.getName(), LOADER);
        assertNotNull(bridge);
        StubRegionApi.bindRegion(12);
        assertEquals(12, bridge.currentRegion());
        StubRegionApi.bindRegion(300);
        assertEquals(300, bridge.currentRegion());
        StubRegionApi.current = null;
        assertEquals(-1, bridge.currentRegion());
        PhysicsBody notAnEntity = (PhysicsBody) Proxy.newProxyInstance(LOADER, new Class<?>[]{PhysicsBody.class},
                (self, method, args) -> null);
        assertFalse(bridge.owns(notAnEntity));
        assertTrue(bridge.owns(null, null));
        int[] ran = {0};
        StubRegionApi.QUEUED.clear();
        bridge.runOnOwner(null, null, () -> ran[0]++);
        assertEquals(1, StubRegionApi.QUEUED.size());
        StubRegionApi.QUEUED.get(0).run();
        assertEquals(1, ran[0]);
        StubRegionApi.LEASED.clear();
        bridge.runLeased(null, null, () -> ran[0]++);
        assertEquals(1, StubRegionApi.LEASED.size());
        assertEquals(1, ran[0]);
        StubRegionApi.LEASED.get(0).run();
        assertEquals(2, ran[0]);
    }

    @Test
    void signatureMismatchFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> TessellateBridge.bind(String.class.getName(), StubRegionApi.Context.class.getName(), LOADER));
    }
}
