package com.wf.wflib.contract.site;

/** What a site survey needs to know about the ground, so the rules can be tested without a world. Coordinates are block x, z. */
public interface Terrain {
    /** What the top surface of a column is made of, as far as standing a base on it goes. */
    enum Surface { SOLID, LOOSE, SLIPPERY, WATER, LAVA }

    /** False when the column is outside the world border or its chunk cannot be had. */
    boolean available(int x, int z);

    /** The y of the first free block above the ground (trees and leaves ignored). */
    int ground(int x, int z);

    Surface surface(int x, int z);

    /** True when the biome there is an ocean or a river: no base is built on a coast that floods. */
    boolean wet(int x, int z);

    /** True when the ground is hollow just below the surface (a cave roof, a ravine edge): it would not carry a wall. */
    boolean hollow(int x, int z);

    /** Natural cover at the column (a tree trunk, a boulder), which a defender can use and an attacker can hide behind. */
    boolean cover(int x, int z);
}
