package com.wf.wflib.ammo.api;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmmoKindTest {
    @BeforeAll
    static void define() {
        com.wf.wflib.ammo.AmmoCatalogue.define();
    }

    private static AmmoKind kind(String path) {
        return AmmoKind.of(com.wf.wflib.ammo.AmmoCatalogue.variant(ResourceLocation.fromNamespaceAndPath("wflib", path)));
    }

    @Test
    void catalogueLoads() {
        assertEquals(AmmoKind.BALL, kind("5.56x45/ball"));
        assertEquals(AmmoKind.TRACER, kind("5.56x45/tracer"));
        assertEquals(AmmoKind.AP, kind("5.56x45/ap"));
        assertEquals(AmmoKind.AP, kind("12.7x99/slap"));
        assertEquals(AmmoKind.INCENDIARY, kind("12.7x99/m1"));
        assertEquals(AmmoKind.HE, kind("12.7x99/mk211"));
        assertEquals(AmmoKind.AP, kind("12.7x99/m8"));
        assertEquals(AmmoKind.AP, kind("406mm_naval/ap"));
        assertEquals(AmmoKind.HE, kind("25x59b/ball"));
        assertEquals(AmmoKind.HEAT, kind("rpg7/heat"));
        assertEquals(AmmoKind.TANDEM, kind("rpg7/tandem"));
        assertEquals(AmmoKind.SHOT, kind("12ga/buck"));
        assertEquals(AmmoKind.SUBSONIC, kind("9x19/subsonic"));
        assertEquals(AmmoKind.MISSILE, kind("fgm_148/missile"));
    }
}
