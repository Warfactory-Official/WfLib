package com.wf.wflib.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wf.wflib.contract.news.News;
import com.wf.wflib.contract.profile.Profile;
import org.junit.jupiter.api.Test;

class NewsTest {
    @Test
    void theArchiveKeepsTheNewestAndReturnsThemNewestFirst() {
        var n = new News();
        for (int i = 0; i < News.KEEP + 10; i++) n.push("line " + i);
        assertEquals("line " + (News.KEEP + 9), n.latest(3).get(0));
        assertEquals(3, n.latest(3).size());
        assertEquals(News.KEEP, n.latest(1000).size());
    }

    @Test
    void aProfileSurvivesASaveWithItsLeadsAndCooldowns() {
        var p = new Profile();
        p.rep = 120;
        p.kills.put("enemy", 4);
        p.leads.add(new String[]{"wflib:intel_hvt", "GOOD"});
        p.cooldowns.put("wflib:hvt", 99L);
        var back = Profile.read(p.json());
        assertEquals(120, back.rep);
        assertEquals(4, back.kills.get("enemy"));
        assertEquals("GOOD", back.leads.get(0)[1]);
        assertEquals(99L, back.cooldowns.get("wflib:hvt"));
    }
}
