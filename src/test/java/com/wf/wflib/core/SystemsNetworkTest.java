package com.wf.wflib.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemsNetworkTest {
    @Test
    void mismatchNamesSystemsOfEveryMod() {
        Set<String> client = Set.of("wflib:round", "ma:gun");
        Set<String> server = Set.of("wflib:round", "wflib:laser", "ma:gun", "ma:coop");
        assertEquals(Set.of("wflib:laser", "ma:coop"), SystemsNetwork.differing(client, server));
        assertEquals(Set.of(), SystemsNetwork.differing(server, server));
    }
}
