package com.wf.wflib.armor.api.hull;

import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.geom.api.OBB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Hull-box-face armour model. */
public final class HullResolver {

    /** Past this ratio: full payload. */
    private static final float OVERMATCH_RATIO = 1.5f;
    /** Under this ratio: fully stopped. */
    private static final float ABSORB_RATIO = 0.75f;
    /** Payload fraction kept at ratio=1.0 (bare penetration); ramps to 1.0 at OVERMATCH_RATIO. */
    private static final float PARTIAL_FLOOR = 0.35f;
    /** Spall fraction at ABSORB_RATIO; scales by ratio/ABSORB_RATIO below (rifle vs MBT => ~0). */
    private static final float ABSORBED_SPALL = 0.06f;
    /** cos(incidence) floor; avoids divide-near-zero at grazing hits. */
    private static final float MIN_COS = 0.05f;
    /** Metres the main box grows by for {@link #entryFace}: mesh skin (skirts, fenders) sits just outside it. */
    private static final double ENTRY_MARGIN = 0.25;

    /** Crossings of one plate closer than this along the line are the two faces of one sheet, m. */
    private static final double PAIR_GAP = 0.1;
    private static final ThreadLocal<List<HullMesh.Crossing>> SCRATCH = ThreadLocal.withInitial(ArrayList::new);

    private HullResolver() {
    }

    /**
     * Ricochet: entry surface incidence {@code > threat.ricochetDeg()} unless {@code caliber / nominal >= overmatchTD
     * > 0} (nominal = the entry plate's outer layer). Else the budget {@code threat.penetrationMm()} walks every
     * crossing along the line ({@link PlateWalk}). Outcome and hull damage ramp = the entry plate alone
     * ({@code pen / } {@link HullResult#effectiveThickness}); {@link HullResult#through} only with
     * {@link HullResult#interiorPen} {@code > 0}; the later plates decide {@link HullResult#residualPen}, the
     * round's exit.
     * <p>Later crossings count only when plate-bound: they exist where the mesh order says so, thickness is
     * declared. Crossings of one plate within {@link #PAIR_GAP} are one sheet. Profile without plates: entry only.
     * Null: no profile, nothing struck, or plate {@code <= 0} mm => caller's own damage model.
     */
    @Nullable
    public static HullResult resolve(HullBody body, Vec3 start, Vec3 direction, Vec3 hitPos, Threat threat,
                                     float incomingDamage) {
        return resolve(body, start, direction, hitPos, threat, incomingDamage, null);
    }

    /**
     * {@link #resolve(HullBody, Vec3, Vec3, Vec3, Threat, float)} with layers in front of the hull: {@code lead} feeds
     * the walk before the entry plate ({@link PlateWalk#cut}, {@link PlateWalk#gap}), so mounted brick and slat
     * panels spend the same budget as the plates. Integral ERA bricks the walk sets off are read from
     * {@link HullBody#eraLive} and listed in {@link WalkResult#eraHits}, never spent here.
     */
    @Nullable
    public static HullResult resolve(HullBody body, Vec3 start, Vec3 direction, Vec3 hitPos, Threat threat,
                                     float incomingDamage, @Nullable Consumer<PlateWalk> lead) {
        float penetration = threat.penetrationMm();
        HullProfile profile = body.hullProfile();
        if (profile == null || !profile.isDefined() || penetration <= 0 || direction.lengthSqr() == 0) {
            return null;
        }
        Vec3 shotDir = direction.normalize();
        boolean shaped = threat.kind().isShapedCharge();
        Vec3 limit = hitPos.add(shotDir.scale(0.1));
        HullMesh mesh = body.hits();
        List<HullMesh.Crossing> crossings = null;
        Plate plate;
        if (mesh != null && !profile.plates.isEmpty()) {
            crossings = SCRATCH.get();
            crossings.clear();
            Vec3 far = hitPos.add(shotDir.scale(reach(body, hitPos)));
            mesh.surfaces(start, far, crossings);
            if (crossings.isEmpty()
                    || crossings.get(0).point().distanceToSqr(start) > limit.distanceToSqr(start)) {
                return null;
            }
            HullMesh.Crossing first = crossings.get(0);
            plate = plate(body, profile, first.node(), first.point(), first.normal(), first.plate(), shotDir, shaped);
        } else {
            plate = plate(body, profile, start, limit, shotDir, shaped);
        }
        if (plate == null) {
            return null;
        }
        float nominal = plate.nominal();
        Vec3 normal = new Vec3(plate.normal());
        Vec3 face = new Vec3(plate.faceNormal());
        PlateSpec entrySpec = plate.plateId() == null ? null : profile.plates.get(plate.plateId());
        float outer = entrySpec == null ? nominal : entrySpec.outer(shaped);
        boolean overmatch = threat.overmatchTD() > 0 && threat.caliberMm() >= outer * threat.overmatchTD();
        if (plate.surfaceIncidence() > threat.ricochetDeg() && !overmatch) {
            return new HullResult(HullOutcome.RICOCHET, 0, 0, nominal,
                    plate.effective(threat.slopeExp()), penetration, normal, face, WalkResult.NONE);
        }

        PlateWalk walk = new PlateWalk(threat);
        if (lead != null) {
            lead.accept(walk);
        }
        boolean entering = crossings == null || crossings.get(0).entering();
        walk.at((float) plate.point().distanceTo(start), entering);
        layers(body, walk, entrySpec, plate, crossings == null ? -1 : crossings.get(0).node(), entering);
        walk.mark();
        if (crossings != null) {
            String last = plate.plateId();
            double lastAt = crossings.get(0).point().distanceTo(start);
            for (int k = 1, n = crossings.size(); k < n && walk.alive(); k++) {
                HullMesh.Crossing c = crossings.get(k);
                if (c.plate() == null) {
                    continue;
                }
                double at = c.point().distanceTo(start);
                if (c.plate().equals(last) && at - lastAt < PAIR_GAP) {
                    continue;
                }
                Plate p = plate(body, profile, c.node(), c.point(), c.normal(), c.plate(),
                        c.entering() ? shotDir : shotDir.reverse(), shaped);
                if (p == null) {
                    continue;
                }
                walk.nextCrossing().at((float) at, c.entering());
                layers(body, walk, profile.plates.get(c.plate()), p, c.node(), c.entering());
                last = c.plate();
                lastAt = at;
            }
        }
        WalkResult result = walk.result();
        float effective = result.interiorNeeded();
        if (result.interiorPen() > 0.0f) {
            float ratio = penetration / effective;
            if (ratio >= OVERMATCH_RATIO) {
                return new HullResult(HullOutcome.OVERMATCH, incomingDamage, incomingDamage, nominal, effective,
                        penetration, normal, face, result);
            }
            float t = (ratio - 1.0f) / (OVERMATCH_RATIO - 1.0f);
            float scale = Mth.lerp(t, PARTIAL_FLOOR, 1.0f);
            return new HullResult(HullOutcome.PENETRATION, incomingDamage * scale, incomingDamage * scale,
                    nominal, effective, penetration, normal, face, result);
        }
        float ratio = Math.min(penetration / effective, 1.0f);
        if (ratio < ABSORB_RATIO) {
            float spall = incomingDamage * ABSORBED_SPALL * ratio / ABSORB_RATIO;
            return new HullResult(HullOutcome.ABSORBED, spall, spall, nominal, effective, penetration, normal, face,
                    result);
        }
        float t = (ratio - ABSORB_RATIO) / (1.0f - ABSORB_RATIO);
        float scale = Mth.lerp(t, ABSORBED_SPALL, PARTIAL_FLOOR);
        return new HullResult(HullOutcome.PARTIAL, incomingDamage * scale, incomingDamage * scale, nominal, effective,
                penetration, normal, face, result);
    }

    /** Declared layers of the plate, else its one equivalent layer (zone, part or hull face). */
    private static void layers(HullBody body, PlateWalk walk, @Nullable PlateSpec spec, Plate plate, int node,
                               boolean entering) {
        if (spec != null) {
            EraType era = spec.era();
            if (era != null && entering && node >= 0) {
                HullMesh mesh = body.hits();
                int cell = spec.eraCell(mesh, node, plate.point(), plate.normal());
                walk.era(plate.plateId(), cell, era, body.eraLive(plate.plateId(), cell), plate.cos(),
                        spec.eraEdge(mesh, node, plate.point(), plate.normal()));
            }
            spec.addTo(walk, plate.wear(), plate.cos());
        } else {
            walk.rhae(plate.nominal(), plate.nominal(), 1.0f, 1.0f, plate.wear(), plate.cos());
        }
    }

    /** Farthest a line through {@code from} can still meet the body: to the far side of the widest box. */
    private static double reach(HullBody body, Vec3 from) {
        double reach = 0.0;
        List<OBB> boxes = body.getOBBs();
        for (int i = 0, n = boxes.size(); i < n; i++) {
            OBB b = boxes.get(i);
            double dx = b.centerX() - from.x, dy = b.centerY() - from.y, dz = b.centerZ() - from.z;
            reach = Math.max(reach, Math.sqrt(dx * dx + dy * dy + dz * dz) + b.extents().length());
        }
        return reach + 0.5;
    }

    /**
     * Plate met along {@code start -> end}. Profile values = RHAe (slope included) => face and {@code cos} from the
     * main box face the shot entered ({@link #entryFace}), else the shot's aspect (dominant hull axis of
     * {@code -shotDir}); a part with its own faces: aspect in its rest frame. Mesh slope counts only for ricochet
     * ({@code surfaceCos}) and bound plates ({@link HullMesh.Strike#plate}).
     * Precedence: plate id > zone > part faces > hull faces. A plate's {@code cos} and ricochet incidence use its
     * declared normal, the mesh normal when {@code curved} or undeclared; {@code meshCos} is always the mesh's.
     *
     * @param normal     struck surface, world frame; toward the shooter from a trace
     * @param faceNormal {@code face} axis, outward, world frame
     * @param part       part with its own faces, else null (hull)
     * @param zone       matched zone name, else null
     * @param plateId    bound plate, else null
     * @param surfaceCos clamped to {@link #MIN_COS}
     */
    public record Plate(Vec3 point, Vector3f normal, HullFace face, Vector3f faceNormal, @Nullable String part,
                        @Nullable String zone, @Nullable String plateId, float nominal, float wear,
                        float cos, float surfaceCos, float meshCos) {
        public float effective() {
            return nominal * wear / cos;
        }

        /** Effective thickness under slope exponent {@code slopeExp}: {@code nominal * wear / cos^n}. */
        public float effective(float slopeExp) {
            return nominal * wear / (slopeExp == 1.0f ? cos : (float) Math.pow(cos, slopeExp));
        }

        public float incidence() {
            return degrees(cos);
        }

        public float surfaceIncidence() {
            return degrees(surfaceCos);
        }

        public float meshIncidence() {
            return degrees(meshCos);
        }

        private static float degrees(float cos) {
            return (float) Math.toDegrees(Math.acos(Mth.clamp(cos, -1, 1)));
        }
    }

    /** Null: missed, or plate {@code <= 0} mm. */
    @Nullable
    public static Plate plate(HullBody body, HullProfile profile, Vec3 start, Vec3 end, Vec3 shotDir,
                              boolean shapedCharge) {
        HullMesh mesh = body.hits();
        if (mesh != null) {
            HullMesh.Strike hit = mesh.surface(start, end);
            return hit == null ? null
                    : plate(body, profile, hit.node(), hit.point(), hit.normal(), hit.plate(), shotDir, shapedCharge);
        }
        OBB.SegmentHit hit = OBB.first(body.getOBBs(), start, end);
        return hit == null ? null : plate(body, profile, -1, hit.point(), hit.obb().faceNormal(hit.point()), null,
                shotDir, shapedCharge);
    }

    /**
     * Plate at a known strike.
     *
     * @param node        {@link HullMesh} node, -1 = structure boxes
     * @param worldNormal struck surface, either sign
     * @param plateId     {@link HullProfile#plates} id bound to the struck face, null = unbound
     */
    @Nullable
    public static Plate plate(HullBody body, HullProfile profile, int node, Vec3 struck, Vector3f worldNormal,
                              @Nullable String plateId, Vec3 shotDir, boolean shapedCharge) {
        HullMesh mesh = body.hits();
        String part = null;
        HullFaces faces = profile;
        if (node >= 0) {
            String owner = mesh.partOf(node);
            if (owner != null && profile.parts.containsKey(owner)) {
                part = owner;
                faces = profile.parts.get(owner);
            }
        }
        Vector3f axis = new Vector3f();
        Vector3f faceNormal = new Vector3f();
        if (part != null) {
            axis = HullFace.aspectAxis(mesh.restDirection(node, shotDir.reverse().toVector3f()));
            faceNormal = mesh.worldDirection(node, axis);
        } else if (!entryFace(body.mainBox(), struck, shotDir, axis, faceNormal)) {
            axis = HullFace.aspectAxis(HullFace.toHullFrame(body.rotYXZ(), shotDir.reverse().toVector3f()));
            faceNormal = body.rotYXZ().transform(new Vector3f(axis));
        }
        HullFace face = HullFace.of(axis);
        PlateSpec spec = null;
        if (plateId != null) {
            if (node < 0) {
                throw new IllegalStateException("plate '" + plateId + "' bound without a mesh node");
            }
            spec = profile.plates.get(plateId);
            if (spec == null) {
                throw new IllegalStateException("mesh binds plate '" + plateId + "' missing from the hull profile");
            }
        }
        Vector3f rest = null;
        HullProfile.Zone zone = null;
        if (node >= 0 && (spec != null || !profile.zones.isEmpty())) {
            rest = mesh.restPoint(node, struck);
            if (spec == null) {
                for (HullProfile.Zone z : profile.zones) {
                    if (z.matches(part, face, rest.x, rest.y, rest.z)) {
                        zone = z;
                        break;
                    }
                }
            }
        }
        float nominal = spec != null ? spec.nominal(shapedCharge)
                : zone != null ? zone.nominal(shapedCharge) : faces.nominal(face, shapedCharge);
        if (nominal <= 0) {
            return null;
        }
        float wear = body.hullWear().thicknessScale(HullWear.region(body.mainBox(), struck, faceNormal),
                profile.wearFloor);
        float meshCos = Math.max((float) Math.abs(shotDir.dot(new Vec3(worldNormal))), MIN_COS);
        float faceCos = Math.max((float) -shotDir.dot(new Vec3(faceNormal)), MIN_COS);
        Vector3f normal = worldNormal;
        float cos = faceCos, surfaceCos = meshCos;
        if (spec != null) {
            Vector3f declared = spec.restNormal(rest.x);
            if (declared != null) {
                normal = mesh.worldDirection(node, declared);
                float d = (float) shotDir.dot(new Vec3(normal));
                if (d > 0) {
                    normal.negate();
                }
                surfaceCos = Math.max(Math.abs(d), MIN_COS);
            }
            cos = surfaceCos;
        }
        return new Plate(struck, normal, face, faceNormal, part, zone == null ? null : zone.name(), plateId, nominal,
                wear, cos, surfaceCos, meshCos);
    }

    /**
     * Main box face the shot line entered through before {@code struck}; {@code struck} outside the box grown by
     * {@link #ENTRY_MARGIN} (turret, fittings above it) => false.
     *
     * @param axis       out: outward face axis, box frame (= hull frame)
     * @param faceNormal out: same, world frame
     */
    private static boolean entryFace(@Nullable OBB box, Vec3 struck, Vec3 shotDir, Vector3f axis,
                                     Vector3f faceNormal) {
        if (box == null) {
            return false;
        }
        Vector3f[] axes = box.getAxes();
        Vector3f ext = box.extents();
        double rx = struck.x - box.centerX(), ry = struck.y - box.centerY(), rz = struck.z - box.centerZ();
        int best = -1;
        double bestT = Double.NEGATIVE_INFINITY;
        float sign = 0;
        for (int i = 0; i < 3; i++) {
            Vector3f a = axes[i];
            double p = rx * a.x + ry * a.y + rz * a.z;
            double d = shotDir.x * a.x + shotDir.y * a.y + shotDir.z * a.z;
            double e = ext.get(i) + ENTRY_MARGIN;
            if (Math.abs(p) > e) {
                return false;
            }
            if (Math.abs(d) < 1.0e-6) {
                continue;
            }
            double t = ((d > 0 ? -e : e) - p) / d;
            if (t > bestT) {
                bestT = t;
                best = i;
                sign = d > 0 ? -1 : 1;
            }
        }
        if (best < 0) {
            return false;
        }
        axis.setComponent(best, sign);
        faceNormal.set(axes[best]).mul(sign);
        return true;
    }
}
