package com.wf.wflib.pda.client.theme;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdaThemesTest {
    @Test
    void unknownSavedThemesFallbackAndMenuCyclesBothWays() {
        assertSame(PdaTheme.CLASSIC, PdaThemes.get("missing-resource-pack"));
        assertSame(PdaTheme.CLASSIC, PdaThemes.get(null));
        assertSame(PdaTheme.GAMMA, PdaThemes.get("tactical"));
        assertFalse(PdaTheme.GAMMA.hazard());
        assertEquals(0, PdaTheme.GAMMA.chamfer());
        assertSame(PdaTheme.GAMMA, PdaThemes.step("classic", 1));
        assertEquals("custom", PdaThemes.step("classic", -1).id());
        assertEquals("custom", PdaThemes.step("phosphor", 1).id());
        assertSame(PdaTheme.CLASSIC, PdaThemes.step("custom", 1));
        assertSame(PdaTheme.CLASSIC, PdaThemes.step("classic", 5));
    }

    @Test
    void registeredBuiltinsKeepTheirOrderAndStayWithinEffectCaps() {
        assertEquals(List.of("classic", "gamma", "zone", "phosphor", "custom"),
                PdaThemes.all().stream().map(PdaTheme::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> PdaThemes.all().clear());
        for (PdaTheme theme : PdaThemes.all()) {
            assertTrue(theme.scanlines() >= 0 && theme.scanlines() <= PdaTheme.MAX_SCANLINES);
            assertTrue(theme.vignette() >= 0 && theme.vignette() <= PdaTheme.MAX_VIGNETTE);
            assertTrue(theme.noise() >= 0 && theme.noise() <= PdaTheme.MAX_NOISE);
            assertTrue(theme.flicker() >= 0 && theme.flicker() <= PdaTheme.MAX_FLICKER);
        }
    }

    @Test
    void customFromDefaultSettingsColoursMatchesClassic() {
        PdaTheme custom = PdaThemes.get("custom");
        assertEquals(PdaTheme.CLASSIC.accent(), custom.accent());
        assertEquals(PdaTheme.CLASSIC.text(), custom.text());
        assertEquals(PdaTheme.CLASSIC.panel(), custom.panel());
    }

    @Test
    void argbExpressionsPreserveAllUnsignedColourBits() {
        assertEquals(4294967295d, PdaTheme.unsigned(0xFFFFFFFF));
        assertEquals(0d, PdaTheme.unsigned(0));
        assertEquals(4293060070d, PdaTheme.unsigned(PdaTheme.GAMMA.accent()));
    }
}
