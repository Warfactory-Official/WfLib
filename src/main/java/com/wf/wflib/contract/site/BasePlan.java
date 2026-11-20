package com.wf.wflib.contract.site;

import java.util.List;

/**
 * What a base is, as data: where its pieces stand, where its soldiers are posted and which way they walk, where its vehicles
 * are parked. Absolute block coordinates; {@code y} is the level a person stands on. Made by {@link BaseLayout}, built by
 * {@link BaseBuilder}, read by whoever raises the soldiers.
 */
public record BasePlan(BaseKind kind, int cx, int cz, int padY, Wall wall, List<Piece> pieces, List<Zone> zones, List<Slot> vehicles,
                       int[] cache) {
    public BasePlan {
        pieces = List.copyOf(pieces);
        zones = List.copyOf(zones);
        vehicles = List.copyOf(vehicles);
    }

    public enum Type { COMMAND, BARRACKS, TENT, DEPOT, TOWER, BUNKER, NEST, CAMP, MAST, BAY, LIGHT, TRENCH }

    /** A piece of the base. {@code (x, y, z)} is its centre at standing level; {@code w} runs along x and {@code d} along z before {@code facing} turns it. */
    public record Piece(Type type, int x, int y, int z, int w, int d, int facing, int height) {
    }

    /** The perimeter wall: a square of side {@code 2 * half}, with gates in the middle of two opposite sides ({@code axis} 0: north and south, 1: east and west). */
    public record Wall(int half, int height, int axis) {
    }

    public enum Role {
        /** The command post: whoever a kill-the-target contract wants. */
        COMMAND("heavy"), GATE("rifle"), TOWER("sniper"), WALL_PATROL("rifle"), OUTER_PATROL("assault"), NEST("mg"), OUTPOST("rifle"), RESERVE("assault");

        public final String prefers;

        Role(String prefers) {
            this.prefers = prefers;
        }
    }

    /**
     * A post or a beat for one squad. With an empty route the squad guards a spot of {@code radius} blocks round {@code (x, y, z)};
     * with a route it walks it. {@code weight} is its share of the base's soldiers.
     */
    public record Zone(Role role, int x, int y, int z, int radius, List<int[]> route, double weight) {
        public Zone {
            route = List.copyOf(route);
        }

        public boolean patrols() {
            return !route.isEmpty();
        }
    }

    /** A parked vehicle: where, which way it faces, and what class ("light", "armour"). */
    public record Slot(int x, int y, int z, float yaw, String kind) {
    }
}
