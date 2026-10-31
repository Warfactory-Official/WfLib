package com.wf.wflib.drone.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/**
 * Registry of squad {@link Formation}s, keyed by id in the same shape as {@code FlightStages}: add a shape
 * by implementing {@link Formation} and calling {@link #register}.
 */
public final class Formations {

    /** Wedge: alternating left/right of the leader, stepping back one rank every two slots. */
    public static final Formation VEE = new Formation() {
        @Override
        public Vec3 slot(int index, Vec3 leaderPos, Vec3 forward, double spacing) {
            if (index <= 0) {
                return leaderPos;
            }
            int rank = (index + 1) / 2;
            double lateral = (index % 2 == 1 ? -1 : 1) * spacing * rank;
            double trail = -spacing * rank;
            Vec3 right = Formation.right(forward);
            return leaderPos.add(right.scale(lateral)).add(forward.scale(trail));
        }

        @Override
        public String id() {
            return "vee";
        }
    };

    /**
     * Single file behind the leader.
     */
    public static final Formation COLUMN = new Formation() {
        @Override
        public Vec3 slot(int index, Vec3 leaderPos, Vec3 forward, double spacing) {
            return index <= 0 ? leaderPos : leaderPos.add(forward.scale(-spacing * index));
        }

        @Override
        public String id() {
            return "column";
        }
    };

    /**
     * Line abreast: alternating left/right at the leader's rank, useful for a sweep.
     */
    public static final Formation LINE = new Formation() {
        @Override
        public Vec3 slot(int index, Vec3 leaderPos, Vec3 forward, double spacing) {
            if (index <= 0) {
                return leaderPos;
            }
            int rank = (index + 1) / 2;
            double lateral = (index % 2 == 1 ? -1 : 1) * spacing * rank;
            return leaderPos.add(Formation.right(forward).scale(lateral));
        }

        @Override
        public String id() {
            return "line";
        }
    };

    /** A square grid with the leader in the middle of it. */
    public static final Formation GRID = new Formation() {
        @Override
        public Vec3 slot(int index, Vec3 leaderPos, Vec3 forward, double spacing) {
            if (index <= 0) {
                return leaderPos;
            }
            int ring = 1;
            while (index >= (2 * ring + 1) * (2 * ring + 1)) {
                ring++;
            }
            int within = index - (2 * ring - 1) * (2 * ring - 1);
            int perimeter = 8 * ring;
            int step = (within + 1) / 2;
            int arc = within % 2 == 1 ? perimeter - step : step;

            int lateral;
            int along;
            if (arc <= ring) {
                lateral = arc;
                along = -ring;
            } else if (arc <= 3 * ring) {
                lateral = ring;
                along = arc - 2 * ring;
            } else if (arc <= 5 * ring) {
                lateral = 4 * ring - arc;
                along = ring;
            } else if (arc <= 7 * ring) {
                lateral = -ring;
                along = 6 * ring - arc;
            } else {
                lateral = arc - 8 * ring;
                along = -ring;
            }
            return leaderPos.add(Formation.right(forward).scale(spacing * lateral))
                    .add(forward.scale(spacing * along));
        }

        @Override
        public String id() {
            return "grid";
        }
    };

    public static final ResourceLocation DEFAULT = rl(VEE.id());

    public static final WFRegistry<Formation> REGISTRY = WFRegistry.create("drone", "drone.formations");

    private Formations() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static Formation get(ResourceLocation id) {
        Formation formation = id == null ? null : REGISTRY.get(id);
        return formation != null ? formation : REGISTRY.get(DEFAULT);
    }

    public static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) {
            return DEFAULT;
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null && REGISTRY.containsKey(parsed) ? parsed : DEFAULT;
    }

    public static Set<ResourceLocation> ids() {
        return REGISTRY.ids();
    }
}
