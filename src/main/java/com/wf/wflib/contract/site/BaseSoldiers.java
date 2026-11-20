package com.wf.wflib.contract.site;

import com.wf.wflib.contract.site.BasePlan.Role;
import com.wf.wflib.contract.site.BasePlan.Zone;
import com.wf.wflib.soldier.api.SiteSpec;
import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Posts a force on a base. The contract's own squads keep their places in the list (the first is the one a kill-the-target
 * contract wants, and it holds the command post); each takes the most important post left, in the order command, wall patrol,
 * outer patrol, reserve, outposts, gates, towers, nests. Soldiers the contract did not ask for, up to the size the base wants,
 * fill the posts it left empty in proportion to their weight, marksmen on towers and gunners in nests where the force has them.
 */
public final class BaseSoldiers {
    private BaseSoldiers() {
    }

    /** One of the contract's squads: who, how many. */
    public record Group(List<String> units, int count) {
    }

    private static int priority(Role r) {
        return switch (r) {
            case COMMAND -> 0;
            case WALL_PATROL -> 1;
            case OUTER_PATROL -> 2;
            case RESERVE -> 3;
            case OUTPOST -> 4;
            case GATE -> 5;
            case TOWER -> 6;
            case NEST -> 7;
        };
    }

    /**
     * @param wanted how many soldiers the base should hold in all (never fewer than the contract's own)
     * @param pool variants extra soldiers are raised from
     * @param roleOf a variant's role id ("marksman", "mg", ...)
     */
    public static List<SiteSpec.Squad> assign(BasePlan plan, List<Group> own, int wanted, List<String> pool, Function<String, String> roleOf, Random rng) {
        List<Zone> zones = new ArrayList<>(plan.zones());
        zones.sort(Comparator.comparingInt(z -> priority(z.role())));
        List<SiteSpec.Squad> out = new ArrayList<>();
        int used = 0, total = 0;
        for (Group g : own) {
            Zone z = zones.get(used % zones.size());
            used++;
            out.add(squad(z, g.units(), g.count()));
            total += g.count();
        }
        int firstFree = Math.min(own.size(), zones.size());
        int budget = Math.max(0, wanted - total);
        List<Zone> free = new ArrayList<>(zones.subList(firstFree, zones.size()));
        if (budget == 0 || free.isEmpty() || pool.isEmpty()) return out;
        double weight = free.stream().mapToDouble(Zone::weight).sum();
        int[] share = new int[free.size()];
        double[] frac = new double[free.size()];
        int given = 0;
        for (int i = 0; i < free.size(); i++) {
            double exact = budget * free.get(i).weight() / weight;
            share[i] = (int) Math.floor(exact);
            frac[i] = exact - share[i];
            given += share[i];
        }
        for (; given < budget; given++) {                                  // the remainder goes to the biggest fractions
            int best = 0;
            for (int i = 1; i < free.size(); i++) if (frac[i] > frac[best]) best = i;
            share[best]++;
            frac[best] = -1;
        }
        for (int i = 0; i < free.size(); i++) {
            if (share[i] < 1) continue;
            Zone z = free.get(i);
            List<String> preferred = pool.stream().filter(u -> roleOf.apply(u).equals(z.role().prefers)).toList();
            out.add(squad(z, preferred.isEmpty() ? pool : preferred, share[i]));
        }
        return out;
    }

    private static SiteSpec.Squad squad(Zone z, List<String> units, int count) {
        List<Vec3> route = new ArrayList<>();
        for (int[] p : z.route()) route.add(new Vec3(p[0] + 0.5, p[1], p[2] + 0.5));
        return new SiteSpec.Squad(units, count, z.patrols() ? SoldierOrder.Kind.PATROL : SoldierOrder.Kind.GUARD,
                new Vec3(z.x() + 0.5, z.y(), z.z() + 0.5), Math.max(2, z.radius()), route);
    }
}
