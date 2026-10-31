package com.wf.wflib.artifact.client;

import com.wf.wflib.view.api.ArmViewPose;
import com.wf.wflib.view.api.PlayerArms;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Where a held artefact and the right hand under it are, view space (m, +X right, +Y up, -Z ahead), for a lift
 * from 0 (arm hanging, out of view: just switched to / about to be put away) to 1 (held out). The arm stays straight
 * throughout, swinging up from the shoulder; the palm faces up under the artefact. Pure maths: tested on its own
 * ({@code HeldArtifactPoseTest}).
 */
public final class HeldArtifactPose {
    /** The arm's line held out: straight ahead of the right shoulder, a touch up. */
    static final Vector3fc HELD = new Vector3f(0.0F, 0.06F, -1.0F).normalize();
    /**
     * The arm's line put away: the held line dropped just far enough that the artefact's top is under the view's
     * edge (y/-z -0.85 against -0.70), so all of the rise is seen; lower, most of it happened out of sight.
     */
    static final Vector3fc LOWERED = new Vector3f(0.04F, -0.6F, -1.0F).normalize();
    /** Share of full reach: past {@link ArmViewPose#MAX_REACH} the solver keeps the arm straight. */
    static final float STRAIGHT = 1.0F;
    /** Palm centre back from the fist end, m. */
    static final float PALM_BACK = 0.06F;
    /** Top of a sleeved hand above the forearm axis, m (the armour box is 0.105 across). */
    static final float HAND_TOP = 0.0525F;
    /** Half the artefact as drawn (model 0.28 m tall at {@link #SCALE}), m. */
    static final float HALF_HEIGHT = 0.056F;
    /** Air between the hand and the artefact's underside when held, m; it bobs this much either way too. */
    static final float GAP = 0.04F;
    public static final float BOB = 0.012F;
    /**
     * Forearm corkscrew allowed, rad: none. Palm up is a half turn from the arm's rest; spread down the forearm's
     * twist joints that warps the skin box, so the whole straight arm turns instead.
     */
    public static final float TWIST_LIMIT = 0.0F;
    /** Drawn size over the model's metres. */
    public static final float SCALE = 0.4F;

    /** Outputs. */
    public final Vector3f shoulder = new Vector3f();
    public final Vector3f fist = new Vector3f();
    public final Vector3f palm = new Vector3f();
    public final Vector3f artifact = new Vector3f();
    public final Vector3f along = new Vector3f();
    /** Out of the palm: up when held, tilting forward as the arm drops. */
    public final Vector3f palmNormal = new Vector3f();
    public final Quaternionf hand = new Quaternionf();

    /**
     * @param lift    0 put away .. 1 held (eased here)
     * @param seconds a running clock, for the bob
     */
    public HeldArtifactPose pose(float lift, float seconds, PlayerArms.Lengths lengths) {
        float e = Mth.clamp(lift, 0.0F, 1.0F);
        e = e * e * (3.0F - 2.0F * e);
        ArmViewPose.shoulder(false, shoulder);
        along.set(LOWERED).lerp(HELD, e).normalize();
        fist.set(along).mul((lengths.upper() + lengths.forearm()) * STRAIGHT).add(shoulder);
        palm.set(along).mul(-PALM_BACK).add(fist);
        // Palm up (+X of the right fist), square to the forearm.
        ArmViewPose.handFrame(along, new Vector3f(0.0F, 1.0F, 0.0F), hand);
        hand.transform(palmNormal.set(1.0F, 0.0F, 0.0F));
        // Off the palm, never into the forearm: settled on it as the arm goes down, lifting off as it comes up.
        float rise = HAND_TOP + HALF_HEIGHT + e * (GAP + BOB * Mth.sin(seconds * 2.1F));
        artifact.set(palmNormal).mul(rise).add(palm);
        return this;
    }
}
