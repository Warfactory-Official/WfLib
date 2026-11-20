package com.wf.wflib.soldier.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

class CombinedWeaponTest {
    /** A gun mod that knows guns by a prefix and answers for the held gun it was told about. */
    private static final class Fake implements SoldierWeapon {
        final String name;
        final String holds;
        final String guns;
        String armed;

        Fake(String name, String holds, String guns) {
            this.name = name;
            this.holds = holds;
            this.guns = guns;
        }

        @Override
        public Ammo ammo(ServerPlayer p) {
            return Holder.held.equals(holds) ? new Ammo(7, 30, 30) : null;
        }

        @Override
        public boolean busy(ServerPlayer p) {
            return name.equals("b");
        }

        @Override
        public boolean blocked(ServerPlayer p) {
            return false;
        }

        @Override
        public boolean jammed(ServerPlayer p) {
            return false;
        }

        @Override
        public boolean unjam(ServerPlayer p) {
            return false;
        }

        @Override
        public boolean reload(ServerPlayer p) {
            return false;
        }

        @Override
        public void cancel(ServerPlayer p) {
        }

        @Override
        public Result fire(ServerPlayer p) {
            return new Result(Shot.FIRED, name);
        }

        @Override
        public boolean usable(ResourceLocation gun) {
            return gun.getNamespace().equals(guns);
        }

        @Override
        public List<ResourceLocation> guns() {
            return List.of(ResourceLocation.fromNamespaceAndPath(guns, "one"), ResourceLocation.fromNamespaceAndPath("shared", "gun"));
        }

        @Override
        public String arm(ServerPlayer p, ResourceLocation gun, int spares, List<ResourceLocation> grenades) {
            return name + " armed " + gun;
        }
    }

    /** What the test soldier holds. */
    private static final class Holder {
        static String held = "";
    }

    private final Fake a = new Fake("a", "ga", "mod_a"), b = new Fake("b", "gb", "mod_b");
    private final CombinedWeapon both = new CombinedWeapon(List.of(a, b));

    @Test
    void theModThatKnowsTheHeldGunWorksIt() {
        Holder.held = "gb";
        assertEquals("b", both.fire(null).verdict());
        assertTrue(both.busy(null), "b's answer");
        Holder.held = "ga";
        assertEquals("a", both.fire(null).verdict());
        assertFalse(both.busy(null));
        assertEquals(7, both.ammo(null).left());
    }

    @Test
    void aGunNoneKnowsIsNotArmedAndFallsToTheFirst() {
        Holder.held = "stick";
        assertNull(both.ammo(null));
        assertEquals("a", both.fire(null).verdict());
    }

    @Test
    void usableAndGunsAreUnionsAndArmGoesToTheModThatCanUseTheGun() {
        assertTrue(both.usable(ResourceLocation.fromNamespaceAndPath("mod_a", "x")));
        assertTrue(both.usable(ResourceLocation.fromNamespaceAndPath("mod_b", "x")));
        assertFalse(both.usable(ResourceLocation.fromNamespaceAndPath("mod_c", "x")));
        assertEquals(3, both.guns().size(), "one each and the shared one once");
        assertEquals("b armed mod_b:x", both.arm(null, ResourceLocation.fromNamespaceAndPath("mod_b", "x"), 2, List.of()));
    }

    @Test
    void servicesCombineAnExtraBesideTheMainSlot() {
        try {
            SoldierServices.weapon(a);
            assertEquals(a, SoldierServices.weapon());
            SoldierServices.addWeapon(b);
            Holder.held = "gb";
            assertEquals("b", SoldierServices.weapon().fire(null).verdict());
            assertTrue(SoldierServices.hasWeapon());
        } finally {
            SoldierServices.weapon(null);
        }
    }
}
