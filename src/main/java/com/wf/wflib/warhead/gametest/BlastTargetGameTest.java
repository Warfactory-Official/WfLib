package com.wf.wflib.warhead.gametest;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.explosion.api.BlastPayload;
import com.wf.wflib.explosion.api.BlastTarget;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.RoundDamageSource;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.explosion.api.ExplosionAEF;
import com.wf.wflib.warhead.api.WarheadCarrier;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/** A {@link BlastTarget} answers its own blast: handed the payload once, spared generic damage, still pushed. */
@PrefixGameTestTemplate(false)
public class BlastTargetGameTest {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = "wflib_blasttarget";
    private static final float SIZE = 4.0f;
    private static final Vec3 X = new Vec3(1.0, 0.0, 0.0);

    /** A cow that is a blast target. */
    private static final class TargetCow extends Cow implements BlastTarget {
        int calls;
        @Nullable
        ExplosionAEF explosion;
        @Nullable
        BlastPayload payload;
        @Nullable
        Vec3 centre;
        /** Penetration of the last round-sourced hit; -1 = none. */
        float hitPen = -1.0f;

        TargetCow(Level level) {
            super(EntityType.COW, level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (source instanceof RoundDamageSource round) {
                this.hitPen = round.threat().penetrationMm();
            }
            return super.hurt(source, amount);
        }

        @Override
        public void blast(ExplosionAEF explosion, BlastPayload payload, Vec3 centre) {
            this.calls++;
            this.explosion = explosion;
            this.payload = payload;
            this.centre = centre;
        }
    }

    private static <T extends Cow> T place(GameTestHelper helper, T cow, Vec3 at) {
        cow.setNoAi(true);
        cow.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static Vec3 centre(GameTestHelper helper) {
        BlockPos p = helper.absolutePos(new BlockPos(4, 4, 4));
        return new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    private static WarheadCarrier carrier(Level level, @Nullable BlastPayload payload) {
        return new WarheadCarrier() {
            @Override
            public Level level() {
                return level;
            }

            @Override
            public int getFragmentCount() {
                return 0;
            }

            @Override
            public Vec3 angle() {
                return X;
            }

            @Override
            public float blastSize() {
                return SIZE;
            }

            @Override
            public boolean breaksBlocks() {
                return false;
            }

            @Nullable
            @Override
            public BlastPayload payload() {
                return payload;
            }
        };
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void aTargetIsHandedTheCarriersPayloadOnceAndNotDamaged(GameTestHelper helper) {
        Vec3 c = centre(helper);
        TargetCow target = place(helper, new TargetCow(helper.getLevel()), c.add(3.0, 0.0, 0.0));
        Cow plain = place(helper, EntityType.COW.create(helper.getLevel()), c.add(-3.0, 0.0, 0.0));
        float full = target.getHealth();
        BlastPayload payload = BlastPayload.ofSize(SIZE).withFragments(100, 2f, 1500f);
        Warheads.STANDARD.detonate(carrier(helper.getLevel(), payload), c);
        helper.assertTrue(target.calls == 1, "blast() calls " + target.calls);
        helper.assertTrue(target.payload == payload, "payload is not the carrier's");
        helper.assertTrue(target.centre.distanceTo(c) < 1e-9, "centre " + target.centre);
        helper.assertTrue(target.getHealth() == full, "generic damage hurt the target, hp " + target.getHealth());
        helper.assertTrue(!plain.isAlive() || plain.getHealth() < plain.getMaxHealth(),
                "an ordinary cow at the same range was not hurt");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void aTargetIsStillPushedAwayFromTheBlast(GameTestHelper helper) {
        Vec3 c = centre(helper);
        TargetCow target = place(helper, new TargetCow(helper.getLevel()), c.add(2.0, 0.0, 0.0));
        target.setDeltaMovement(Vec3.ZERO);
        Warheads.STANDARD.detonate(carrier(helper.getLevel(), null), c);
        helper.assertTrue(target.calls == 1, "blast() calls " + target.calls);
        helper.assertTrue(target.getDeltaMovement().x > 0.05, "no knockback, v " + target.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void aSizeOnlyBlastSynthesisesTheBlastComponent(GameTestHelper helper) {
        Vec3 c = centre(helper);
        TargetCow target = place(helper, new TargetCow(helper.getLevel()), c.add(3.0, 0.0, 0.0));
        Warheads.STANDARD.detonate(carrier(helper.getLevel(), null), c);
        BlastPayload p = target.payload;
        helper.assertTrue(p != null && p.blast() != null, "no blast component");
        helper.assertTrue(Math.abs(p.blast().tntKg() - BlastPayload.kgForSize(SIZE)) < 1e-6,
                "kg " + p.blast().tntKg());
        helper.assertTrue(p.fragments() == null && p.jet() == null && p.hesh() == null && p.efp() == null
                && p.thermal() == null, "synthesis added a component");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void aShapedChargeResolvesATargetInsideTheConeOnly(GameTestHelper helper) {
        Vec3 c = centre(helper);
        TargetCow onAxis = place(helper, new TargetCow(helper.getLevel()), c.add(4.0, 0.0, 0.0));
        TargetCow beside = place(helper, new TargetCow(helper.getLevel()), c.add(-3.0, 0.0, 3.5));
        BlastPayload payload = BlastPayload.ofSize(SIZE).withJet(300f, 22f);
        Warheads.SHAPED_CHARGE.detonate(carrier(helper.getLevel(), payload), c);
        helper.assertTrue(onAxis.calls == 1, "on-axis target calls " + onAxis.calls);
        helper.assertTrue(onAxis.payload == payload, "payload is not the carrier's");
        helper.assertTrue(onAxis.explosion.jetAxis() != null && onAxis.explosion.jetAxis().distanceTo(X) < 1e-9,
                "jet axis " + onAxis.explosion.jetAxis());
        helper.assertTrue(beside.calls == 0, "target behind the charge was resolved " + beside.calls);
        helper.succeed();
    }

    /** Ruling 2: the jet owns the armour of a blast target; the round's own hit on it carries no penetration. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aShapedChargeRoundHitsABlastTargetWithoutPenetration(GameTestHelper helper) {
        Vec3 c = centre(helper);
        TargetCow target = place(helper, new TargetCow(helper.getLevel()), c.add(4.0, 0.0, 0.0));
        var preset = KineticPresets.get(net.minecraft.resources.ResourceLocation.parse("wflib:rpg7/heat"));
        helper.assertTrue(preset.payload() != null && preset.payload().jet() != null, "PG-7V carries no jet payload");
        Rounds.launch((ServerLevel) helper.getLevel(), preset, c, new Vec3(1.0, 0.0, 0.0).scale(1.5), null, null);
        helper.startSequence().thenIdle(12).thenExecute(() -> {
            helper.assertTrue(target.hitPen == 0.0f, "direct hit penetration " + target.hitPen + " (-1 = no round hit)");
            helper.assertTrue(target.calls >= 1, "jet payload never reached the target");
            helper.assertTrue(target.payload != null && target.payload.jet() != null, "no jet component on the blast");
        }).thenSucceed();
    }
}
