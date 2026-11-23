package com.wf.wflib.continuum;

import com.hbm.client.render.armor.ArmorRenderer;
import com.norwood.ahf.body.BodyMesh;
import com.norwood.ahf.client.body.BodyRenderer;
import dev.golem.core.render.HumanoidPose;
import net.minecraft.client.model.PlayerModel;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Continuum's armour bends at the elbows and knees with the wearer's: AHF draws a player's limbs in two halves and keeps the frames it drew them in
 * ({@link BodyRenderer#frame}); the forearm's frame against the upper arm's is the bend Continuum's arm and leg pieces take ({@link ArmorRenderer#LIMB_BENDS}).
 * MineAssault puts its solved arms into those frames before the armour draws. Client only, and only with AHF present.
 */
final class ArmBends {
    private ArmBends() {
    }

    /** Below this (radians) an arm counts as straight and is drawn in one piece's pose. */
    private static final float STRAIGHT = 0.01F;

    static void install() {
        ArmorRenderer.LIMB_BENDS = ArmBends::lower;
        // AHF poses a player's whole skeleton (its stance blends crouching in over several frames): Continuum must not add its own snapped crouch offsets
        dev.golem.core.render.BodyAnimations.install(e -> e instanceof net.minecraft.world.entity.player.Player && BodyRenderer.enabled && !e.isBaby());
        ContinuumBridge.LOG.info("Armour: Continuum's armour bends at the elbows and knees with AHF's body");
    }

    static Matrix4fc lower(HumanoidPose wearer, ArmorRenderer.Limb limb) {
        if (!(wearer.model() instanceof PlayerModel<?> model) || BodyRenderer.mesh(model) == null) return null;
        Matrix4f upper = BodyRenderer.frame(switch (limb) {
            case RIGHT_ARM -> BodyMesh.Joint.RIGHT_ARM;
            case LEFT_ARM -> BodyMesh.Joint.LEFT_ARM;
            case RIGHT_LEG -> BodyMesh.Joint.RIGHT_LEG;
            case LEFT_LEG -> BodyMesh.Joint.LEFT_LEG;
            case TORSO -> BodyMesh.Joint.BODY;
        });
        Matrix4f lower = BodyRenderer.frame(switch (limb) {
            case RIGHT_ARM -> BodyMesh.Joint.RIGHT_FOREARM;
            case LEFT_ARM -> BodyMesh.Joint.LEFT_FOREARM;
            case RIGHT_LEG -> BodyMesh.Joint.RIGHT_SHIN;
            case LEFT_LEG -> BodyMesh.Joint.LEFT_SHIN;
            case TORSO -> BodyMesh.Joint.CHEST;
        });
        Matrix4f bend = new Matrix4f(upper).invertAffine().mul(lower);
        float trace = bend.m00() + bend.m11() + bend.m22();
        return Math.acos(Math.max(-1F, Math.min(1F, (trace - 1F) / 2F))) < STRAIGHT ? null : bend;
    }
}
