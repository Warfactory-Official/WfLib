package com.wf.wflib.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.wf.wflib.contract.profile.Profile;
import com.wf.wflib.contract.profile.Rank;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SessionTest {
    private static final UUID OWNER = UUID.randomUUID(), FRIEND = UUID.randomUUID(), STRANGER = UUID.randomUUID();
    private static final ResourceLocation DEF = ResourceLocation.fromNamespaceAndPath("wflib", "hvt");

    private static Session session(BlockPos cache) {
        return new Session(UUID.randomUUID(), DEF, OWNER, Set.of(FRIEND), "site-1", "minecraft:overworld", new Vec3(10.5, 64, -300.25), 123456L, cache);
    }

    @Test
    void aSessionSurvivesASaveWithAndWithoutItsCache() {
        for (BlockPos cache : new BlockPos[]{null, new BlockPos(-12, 70, 4000)}) {
            Session s = session(cache);
            Session back = Session.read(JsonParser.parseString(s.json().toString()).getAsJsonObject());
            assertEquals(s, back);
            if (cache == null) assertNull(back.cache());
        }
    }

    @Test
    void ownerAndPartyAreInvolvedAndStrangersAreNot() {
        Session s = session(null);
        assertTrue(s.involves(OWNER));
        assertTrue(s.involves(FRIEND));
        assertFalse(s.involves(STRANGER));
    }

    @Test
    void aPartyCannotBeChangedAfterwards() {
        var party = new java.util.HashSet<>(Set.of(FRIEND));
        Session s = new Session(UUID.randomUUID(), DEF, OWNER, party, "s", "d", Vec3.ZERO, 0, null);
        party.add(STRANGER);
        assertFalse(s.involves(STRANGER));
    }

    @Test
    void profileHelpersAddUp() {
        Profile p = new Profile();
        p.kills.put("enemy", 3);
        p.kills.put("bandit", 4);
        p.rep = 700;
        assertEquals(7, p.totalKills());
        assertEquals(Rank.TRUSTED, p.rank());
        assertEquals(Rank.ELITE, Rank.byId("elite").orElseThrow());
        assertTrue(Rank.byId("emperor").isEmpty());
    }
}
