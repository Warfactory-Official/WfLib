package com.wf.wflib.pda.client.theme;

import org.junit.jupiter.api.Test;
import com.wf.wflib.ui.api.theme.PdaTheme;
import static org.junit.jupiter.api.Assertions.*;

class PdaEffectsTest {
    @Test void everyThemeHasFourIndependentEffectsAndMasterSuppressesAll() {
        for (var theme : PdaThemes.all()) {
            assertEquals(new PdaEffects.Frame(0,0,0,0), PdaEffects.frame(theme,false,true,true,true,true,1));
            var scan = PdaEffects.frame(theme,true,true,false,false,false,1);
            assertTrue(scan.scanlines()>0,theme.id()); assertEquals(0,scan.vignette()+scan.noise()+scan.flicker());
            var vig = PdaEffects.frame(theme,true,false,true,false,false,1);
            assertTrue(vig.vignette()>0,theme.id()); assertEquals(0,vig.scanlines()+vig.noise()+vig.flicker());
            var noise = PdaEffects.frame(theme,true,false,false,true,false,1);
            assertTrue(noise.noise()>0,theme.id()); assertEquals(0,noise.scanlines()+noise.vignette()+noise.flicker());
            var flick = PdaEffects.frame(theme,true,false,false,false,true,1);
            assertTrue(flick.flicker()>0,theme.id()); assertEquals(0,flick.scanlines()+flick.vignette()+flick.noise());
        }
    }
    @Test void flickerNeverFlashesOrBlanksTextAndItsWaveActuallyChanges() {
        for (var theme : PdaThemes.all()) {
            float min=1,max=0;
            for (int i=0;i<3000;i++) {
                var f=PdaEffects.frame(theme,true,true,true,true,true,i/100.0);
                assertTrue(f.flicker()>=0 && f.flicker()<=.03f);
                assertTrue(f.scanlines()<=PdaTheme.MAX_SCANLINES && f.vignette()<=PdaTheme.MAX_VIGNETTE && f.noise()<=PdaTheme.MAX_NOISE);
                min=Math.min(min,f.flicker());max=Math.max(max,f.flicker());
            }
            assertTrue(max-min>.005f,theme.id());
        }
    }
}
