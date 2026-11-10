package com.wf.wflib.soldier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The registry-free part of resupply; moving real items is {@link ForceGameTest}. */
class ResupplyTest {
    @Test void aDepotSurvivesItsJsonAndOnlyTheOwnersSideUsesIt() {
        var d = new Depot("armoury", "friendly", "minecraft:overworld", new net.minecraft.core.BlockPos(1, 2, 3), true);
        assertEquals(d, Depot.read(d.json()));
        var book = new DepotBook();
        book.put(d);
        book.put(new Depot("theirs", "enemy", "minecraft:overworld", new net.minecraft.core.BlockPos(2, 2, 3), false));
        book.put(new Depot("common", "", "minecraft:overworld", new net.minecraft.core.BlockPos(50, 2, 3), false));
        var near = new net.minecraft.core.BlockPos(0, 2, 3);
        assertEquals("armoury", book.nearest("friendly", "minecraft:overworld", near, 100, java.util.Set.of()).id());
        assertEquals("common", book.nearest("friendly", "minecraft:overworld", near, 100, java.util.Set.of("armoury")).id(), "skips depots already tried; never the enemy's");
        assertNull(book.nearest("friendly", "minecraft:the_nether", near, 100, java.util.Set.of()));
        assertNull(book.nearest("friendly", "minecraft:overworld", near, 10, java.util.Set.of("armoury")));
    }
}
