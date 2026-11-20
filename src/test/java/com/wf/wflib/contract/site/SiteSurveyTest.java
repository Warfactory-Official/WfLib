package com.wf.wflib.contract.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class SiteSurveyTest {
    /** A synthetic world: height, surface, wet, hollow and cover are functions of x, z. */
    static final class Fake implements Terrain {
        BiFunction<Integer, Integer, Integer> height = (x, z) -> 64;
        BiFunction<Integer, Integer, Surface> surface = (x, z) -> Surface.SOLID;
        BiFunction<Integer, Integer, Boolean> wet = (x, z) -> false, hollow = (x, z) -> false, cover = (x, z) -> false;

        public boolean available(int x, int z) { return true; }
        public int ground(int x, int z) { return height.apply(x, z); }
        public Surface surface(int x, int z) { return surface.apply(x, z); }
        public boolean wet(int x, int z) { return wet.apply(x, z); }
        public boolean hollow(int x, int z) { return hollow.apply(x, z); }
        public boolean cover(int x, int z) { return cover.apply(x, z); }
    }

    @Test
    void flatSolidGroundIsFine() {
        var r = SiteSurvey.assess(new Fake(), 0, 0, 20, 5);
        assertTrue(r.ok(), r.reason());
        assertEquals(64, r.padY());
        assertEquals(0, r.range());
    }

    @Test
    void anOceanCoastLakeOrLavaIsRefused() {
        var coast = new Fake();
        coast.wet = (x, z) -> x > 0;
        assertFalse(SiteSurvey.assess(coast, 0, 0, 20, 5).ok());
        var lake = new Fake();
        lake.surface = (x, z) -> x * x + z * z < 100 ? Terrain.Surface.WATER : Terrain.Surface.SOLID;
        assertFalse(SiteSurvey.assess(lake, 0, 0, 20, 5).ok());
        var lava = new Fake();
        lava.surface = (x, z) -> x == 8 && z == 8 ? Terrain.Surface.LAVA : Terrain.Surface.SOLID;
        assertEquals("lava", SiteSurvey.assess(lava, 0, 0, 20, 5).reason());
    }

    @Test
    void sandIceAndCavesAreRefusedButAPatchIsTolerated() {
        var sand = new Fake();
        sand.surface = (x, z) -> Terrain.Surface.LOOSE;
        assertEquals("loose ground", SiteSurvey.assess(sand, 0, 0, 20, 5).reason());
        var cave = new Fake();
        cave.hollow = (x, z) -> true;
        assertEquals("hollow ground", SiteSurvey.assess(cave, 0, 0, 20, 5).reason());
        var patch = new Fake();
        patch.surface = (x, z) -> x == 4 && z == 4 ? Terrain.Surface.LOOSE : Terrain.Surface.SOLID;
        assertTrue(SiteSurvey.assess(patch, 0, 0, 20, 5).ok());
    }

    @Test
    void slopesSteeperThanTheBuilderCanGradeAreRefused() {
        var slope = new Fake();
        slope.height = (x, z) -> 64 + x / 2;
        assertEquals("too steep", SiteSurvey.assess(slope, 0, 0, 20, 5).reason());
        assertTrue(SiteSurvey.assess(slope, 0, 0, 20, 25).ok());
    }

    @Test
    void coverAndACommandingCentreScoreHigher() {
        var plain = SiteSurvey.assess(new Fake(), 0, 0, 20, 5).score();
        var wooded = new Fake();
        wooded.cover = (x, z) -> (x + z) % 3 == 0;
        assertTrue(SiteSurvey.assess(wooded, 0, 0, 20, 5).score() > plain);
        var hill = new Fake();
        hill.height = (x, z) -> x * x + z * z <= 20 * 20 ? 66 : 62;           // a flat top the size of the footprint, lower all round
        assertTrue(SiteSurvey.assess(hill, 0, 0, 20, 5).score() > plain);
    }
}
