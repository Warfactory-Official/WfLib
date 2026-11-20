package com.wf.wflib.contract.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** The style format: what a missing block does, and the odds. Blocks need a registry, so what is checked here is the structure, not the blocks. */
class SiteStylesTest {
    @Test
    void anEmptyStyleListAlwaysPicksThePlainLook() {
        for (long hash = -50; hash < 50; hash++) assertNull(SiteStyles.pick(hash));
    }

    @Test
    void aStyleWithNoWeightIsNotOffered() {
        var json = JsonParser.parseString("{\"weight\": 0, \"roles\": {}}").getAsJsonObject();
        assertNull(SiteStyles.parse("t:zero", json));
    }

    @Test
    void aStyleWithNoRolesParsesAndKeepsItsId() {
        var json = JsonParser.parseString("{\"weight\": 3, \"roles\": {}}").getAsJsonObject();
        var style = SiteStyles.parse("t:plainish", json);
        assertNotNull(style);
        assertEquals("t:plainish", style.id());
        assertEquals(3, style.weight());
        assertEquals(0.12f, style.weatherChance());
    }
}
