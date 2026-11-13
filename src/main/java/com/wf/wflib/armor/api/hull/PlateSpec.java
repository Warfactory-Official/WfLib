package com.wf.wflib.armor.api.hull;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Named plate of a {@link HullProfile}: declared normal + layers. Gson-bound.
 *
 * <ul>
 *   <li>{@code facing}: front / side / rear / top / bottom, or azimuth degrees (0 = front, MC yaw sense); frame =
 *       owning part at rest; {@code side} mirrors by the sign of the struck point's rest x.</li>
 *   <li>{@code slope}: degrees from vertical, 0 when absent; ignored for top / bottom.</li>
 *   <li>{@code curved}: no declared normal, incidence from the mesh normal. Excludes facing, slope, los.</li>
 *   <li>{@code layers}: outermost first, solid layers with air {@code gap}s between (never first or last).
 *       {@code los}: line-of-sight RHAe at the declared slope, converted to normal thickness
 *       {@code los * cos(slope)}, one layer.</li>
 *   <li>An {@code {era, cells}} layer is an integral reactive array: first layer only, beside either {@code los} or
 *       solid layers; its bricks are not RHAe ({@link #nominal} excludes them).</li>
 * </ul>
 */
public final class PlateSpec {

    /** Published line-of-sight RHAe; {@code ce} 0 = {@code ke}. */
    public record Los(@SerializedName("ke") float ke, @SerializedName("ce") float ce) {
    }

    @SerializedName("slope")
    @Nullable
    public Float slope;

    @SerializedName("facing")
    @Nullable
    public String facing;

    @SerializedName("curved")
    public boolean curved;

    @SerializedName("los")
    @Nullable
    public Los los;

    @SerializedName("layers")
    public List<PlateLayer> layers = List.of();

    private transient volatile Compiled compiled;

    /** Solid layer in RHA-equivalent mm, or air when {@code gap > 0}. */
    private record Step(float ke, float ce, float spall, float hesh, float gap) {
    }

    private record Compiled(float ke, float ce, Step[] steps, @Nullable Vector3f positive,
                            @Nullable Vector3f negative, @Nullable EraType era, int eraCells) {
    }

    /** Integral array type, null = none. */
    @Nullable
    public EraType era() {
        return compiled().era;
    }

    /** Bricks of the integral array; 0 = none. */
    public int eraCells() {
        return compiled().eraCells;
    }

    /** Sum of solid layers, normal thickness, RHAe mm. */
    public float nominal(boolean shapedCharge) {
        Compiled c = compiled();
        return shapedCharge ? c.ce : c.ke;
    }

    /** First solid layer alone: what overmatch and ricochet see. */
    public float outer(boolean shapedCharge) {
        Step s = compiled().steps[0];
        return shapedCharge ? s.ce : s.ke;
    }

    /** Feeds the layers, outermost first, to {@code walk}; {@code thicknessScale} thins every solid layer. */
    void addTo(PlateWalk walk, float thicknessScale, float cos) {
        for (Step s : compiled().steps) {
            if (s.gap > 0) {
                walk.gap(s.gap);
            } else {
                walk.rhae(s.ke, s.ce, s.spall, s.hesh, thicknessScale, cos);
            }
        }
    }

    /**
     * Declared outward normal in the owning part's rest frame, shared (do not mutate); null = take the mesh normal.
     *
     * @param restX struck point x, rest frame
     */
    @Nullable
    public Vector3f restNormal(float restX) {
        Compiled c = compiled();
        return restX >= 0 ? c.positive : c.negative;
    }

    private Vector3f eraNormal(HullMesh mesh, int node, Vector3f rest, Vector3f worldNormal) {
        Vector3f n = restNormal(rest.x);
        return n != null ? n : mesh.restDirection(node, worldNormal);
    }

    /** Brick of the integral array under a strike; {@link #eraCells} {@code > 0}. */
    public int eraCell(HullMesh mesh, int node, Vec3 point, Vector3f worldNormal) {
        Vector3f rest = mesh.restPoint(node, point);
        return EraGrid.cell(rest, eraNormal(mesh, node, rest, worldNormal), eraCells());
    }

    /** {@link EraGrid#edge} of the strike in its brick. */
    public float eraEdge(HullMesh mesh, int node, Vec3 point, Vector3f worldNormal) {
        Vector3f rest = mesh.restPoint(node, point);
        return EraGrid.edge(rest, eraNormal(mesh, node, rest, worldNormal));
    }

    /** Idempotent; invalid declaration => {@link IllegalStateException} naming {@code id}. */
    public void compile(String id) {
        try {
            compiled = build();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("plate '" + id + "': " + e.getMessage(), e);
        }
    }

    private Compiled compiled() {
        Compiled c = compiled;
        if (c == null) {
            compile("?");
            c = compiled;
        }
        return c;
    }

    private Compiled build() {
        if (curved && (facing != null || slope != null || los != null)) {
            throw new IllegalArgumentException("curved excludes facing, slope and los");
        }
        if (facing == null && slope != null) {
            throw new IllegalArgumentException("slope needs facing");
        }
        if (facing == null && los != null) {
            throw new IllegalArgumentException("los needs facing");
        }
        float slopeDeg = slope == null ? 0 : slope;
        if (!(slopeDeg >= 0 && slopeDeg <= 90)) {
            throw new IllegalArgumentException("slope " + slope + " outside 0..90");
        }
        Vector3f positive = null, negative = null;
        float losCos = 1;
        if (facing != null) {
            String f = facing.trim().toLowerCase(java.util.Locale.ROOT);
            switch (f) {
                case "top" -> positive = negative = new Vector3f(0, 1, 0);
                case "bottom" -> positive = negative = new Vector3f(0, -1, 0);
                default -> {
                    float hx, hz;
                    switch (f) {
                        case "front" -> {
                            hx = 0;
                            hz = 1;
                        }
                        case "rear" -> {
                            hx = 0;
                            hz = -1;
                        }
                        case "side" -> {
                            hx = 1;
                            hz = 0;
                        }
                        default -> {
                            double az;
                            try {
                                az = Math.toRadians(Double.parseDouble(f));
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException("facing '" + facing + "'");
                            }
                            hx = (float) -Math.sin(az);
                            hz = (float) Math.cos(az);
                        }
                    }
                    double s = Math.toRadians(slopeDeg);
                    float c = (float) Math.cos(s), u = (float) Math.sin(s);
                    positive = new Vector3f(hx * c, u, hz * c);
                    negative = f.equals("side") ? new Vector3f(-hx * c, u, hz * c) : positive;
                    losCos = c;
                }
            }
        }
        EraType era = null;
        int eraCells = 0;
        List<PlateLayer> solid = layers;
        if (!layers.isEmpty() && layers.get(0).isEra()) {
            PlateLayer first = layers.get(0);
            if (first.mm() != 0 || first.mat() != null || first.isGap()) {
                throw new IllegalArgumentException("layer 0 is an era array, a gap or solid, not several");
            }
            if (first.cells() < 1 || first.cells() > 64) {
                throw new IllegalArgumentException("era cells " + first.cells() + " outside 1..64");
            }
            era = EraTypes.get(first.era());
            eraCells = first.cells();
            solid = layers.subList(1, layers.size());
        }
        for (PlateLayer layer : solid) {
            if (layer.isEra()) {
                throw new IllegalArgumentException("an era array is the first layer only");
            }
        }
        if (los != null) {
            if (!solid.isEmpty()) {
                throw new IllegalArgumentException("los excludes layers");
            }
            if (!(los.ke > 0)) {
                throw new IllegalArgumentException("los.ke must be > 0");
            }
            float ke = los.ke * losCos;
            float ce = los.ce > 0 ? los.ce * losCos : ke;
            return new Compiled(ke, ce, new Step[] {new Step(ke, ce, 1.0f, 1.0f, 0.0f)}, positive, negative, era,
                    eraCells);
        }
        if (solid.isEmpty()) {
            throw new IllegalArgumentException("needs layers or los");
        }
        Step[] steps = new Step[solid.size()];
        float ke = 0, ce = 0;
        for (int i = 0; i < steps.length; i++) {
            PlateLayer layer = solid.get(i);
            if (layer.isGap()) {
                if (layer.mm() != 0 || layer.mat() != null) {
                    throw new IllegalArgumentException("layer " + i + " is a gap or solid, not both");
                }
                if (i == 0 || i == steps.length - 1 || solid.get(i - 1).isGap()) {
                    throw new IllegalArgumentException("gap " + i + " must sit between solid layers");
                }
                steps[i] = new Step(0, 0, 0, 0, layer.gap());
            } else {
                if (!(layer.mm() > 0)) {
                    throw new IllegalArgumentException("layer " + i + " needs mm > 0 or a gap");
                }
                PlateMaterial m = layer.material();
                steps[i] = new Step(layer.mm() * m.ke(), layer.mm() * m.ce(), m.spall(), m.hesh(), 0.0f);
                ke += steps[i].ke;
                ce += steps[i].ce;
            }
        }
        return new Compiled(ke, ce, steps, positive, negative, era, eraCells);
    }
}
