package com.wf.wflib.round.gametest;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.damage.api.event.ProjectileStrikeEvent;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.RoundPacket;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.RoundEnd;
import com.wf.wflib.round.api.RoundEnded;
import com.wf.wflib.round.api.RoundSpawn;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * What a round does after the armour owner answers its strike. The victim is a cart: a body with volume that a slow
 * round spends several ticks inside.
 */
@PrefixGameTestTemplate(false)
public class RoundThroughGameTest {

    private static final String TEMPLATE = "empty";
    private static final String BATCH = "wflib_through";
    private static final double HEIGHT = 10.0;
    private static final double SPEED = 0.4;

    private static final ResourceLocation RECORDING = Warheads.rl("test_through_recording_warhead");
    /** No drag or gravity, passes through bodies, detonates the recording warhead only where it ends in a block. */
    private static final ResourceLocation SLOW = KineticPresets.rl("test_through_slow_round");
    /** {@link #SLOW} that detonates on bodies. */
    private static final ResourceLocation BURST = KineticPresets.rl("test_through_burst_round");
    /** {@link #BURST} with the {@code post_pen} fuze. */
    private static final ResourceLocation POST = KineticPresets.rl("test_through_post_pen_round");
    private static final List<Vec3> DETONATIONS = new CopyOnWriteArrayList<>();

    public static void register() {
        Warheads.register(RECORDING, (source, pos) -> DETONATIONS.add(pos));
        KineticPresets.registerItemless(KineticPreset.builder(SLOW, null, RECORDING)
                .speed(SPEED).drag(0.0).gravity(0.0).life(200).impactDamage(1.0).caliber(5.56).noChunkLoading()
                .passesThroughEntities().build());
        KineticPresets.registerItemless(KineticPreset.builder(BURST, null, RECORDING)
                .speed(SPEED).drag(0.0).gravity(0.0).life(200).impactDamage(1.0).caliber(5.56).noChunkLoading()
                .build());
        KineticPresets.registerItemless(KineticPreset.builder(POST, null, RECORDING)
                .speed(SPEED).drag(0.0).gravity(0.0).life(200).impactDamage(1.0).caliber(5.56).noChunkLoading()
                .postPen().build());
    }

    private record Seen(Vec3 location, Vec3 velocity) {
    }

    /**
     * Baseline (measured 2026-11-13, before STOP / THROUGH): a non-detonating round whose strike {@code PROCEED}s
     * keeps its velocity bit for bit, never goes off, and flies on; every tick whose segment starts inside the body
     * it strikes it again (5 strikes at 0.4 b/tick through a cart).
     */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void aProceedingNonDetonatingRoundFliesOnUnchangedStrikingEachTickInside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = spot(helper);
        Minecart cart = EntityType.MINECART.create(level);
        cart.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        cart.setNoGravity(true);
        level.addFreshEntity(cart);
        List<Seen> strikes = new ArrayList<>();
        Consumer<ProjectileStrikeEvent> listener = e -> {
            if (e.target() == cart) {
                strikes.add(new Seen(e.location(), e.velocity()));
            }
        };
        NeoForge.EVENT_BUS.addListener(ProjectileStrikeEvent.class, listener);
        DETONATIONS.clear();
        List<String> log = new ArrayList<>();
        long key = Rounds.launch(level, KineticPresets.get(SLOW), at.add(0.0, 0.0, 3.0), new Vec3(0.0, 0.0, -SPEED),
                null, null);
        try {
            for (int tick = 0; tick < 16; tick++) {
                boolean alive = Rounds.step(level, key);
                Vec3 p = Rounds.position(level, key), v = Rounds.velocity(level, key);
                log.add("t" + tick + " alive=" + alive + " strikes=" + strikes.size() + " z="
                        + (p == null ? "-" : String.format("%.2f", p.z - at.z)) + " vz="
                        + (v == null ? "-" : String.format("%.3f", v.z)));
                if (!alive) {
                    break;
                }
            }
            Vec3 p = Rounds.position(level, key), v = Rounds.velocity(level, key);
            helper.assertTrue(p != null && v != null, "round ended: " + log);
            helper.assertTrue(v.equals(new Vec3(0.0, 0.0, -SPEED)), "velocity changed: " + log);
            helper.assertTrue(p.z < at.z - 1.5, "round did not clear the cart: " + log);
            helper.assertTrue(DETONATIONS.isEmpty(), "detonated: " + DETONATIONS + " " + log);
            helper.assertTrue(strikes.size() > 1, "struck " + strikes.size() + " times: " + log);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            Rounds.destroy(level, key);
            cart.discard();
        }
        helper.succeed();
    }


    /** A husk at the spot, round 3 blocks in front along -z at torso height. */
    private static Husk husk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = spot(helper);
        Husk husk = new Husk(EntityType.HUSK, level);
        husk.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        husk.setNoAi(true);
        husk.setNoGravity(true);
        husk.setPersistenceRequired();
        level.addFreshEntity(husk);
        return husk;
    }

    private static long fire(ServerLevel level, ResourceLocation preset, Vec3 target) {
        return Rounds.launch(level, KineticPresets.get(preset), target.add(0.0, 1.0, 3.0),
                new Vec3(0.0, 0.0, -SPEED), null, null);
    }

    /** Answers every strike of {@code key} on {@code target} with {@code answer}; counts them. */
    private static Consumer<ProjectileStrikeEvent> answering(long key, LivingEntity target,
                                                           Consumer<ProjectileStrikeEvent> answer,
                                                           List<Vec3> struckAt) {
        Consumer<ProjectileStrikeEvent> listener = e -> {
            if (Long.valueOf(key).equals(e.key()) && e.target() == target) {
                struckAt.add(e.location());
                answer.accept(e);
            }
        };
        NeoForge.EVENT_BUS.addListener(ProjectileStrikeEvent.class, listener);
        return listener;
    }

    /** STOP: the damage lands, the round ends at the strike, a round that does not detonate on bodies does not. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void aStoppedRoundEndsAtTheStrikeAndHurts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk husk = husk(helper);
        List<Vec3> struckAt = new ArrayList<>();
        DETONATIONS.clear();
        long key = fire(level, SLOW, husk.position());
        Consumer<ProjectileStrikeEvent> listener = answering(key, husk,
                e -> e.setOutcome(ProjectileStrikeEvent.Outcome.STOP), struckAt);
        float health = husk.getHealth();
        try {
            boolean alive = true;
            for (int tick = 0; tick < 16 && alive; tick++) {
                alive = Rounds.step(level, key);
            }
            helper.assertTrue(!alive && Rounds.position(level, key) == null, "round flew on after STOP");
            helper.assertTrue(struckAt.size() == 1, "struck " + struckAt);
            helper.assertTrue(DETONATIONS.isEmpty(), "a non-detonating round went off: " + DETONATIONS);
            helper.assertTrue(husk.getHealth() < health, "STOP landed no damage: " + husk.getHealth());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            Rounds.destroy(level, key);
            husk.discard();
        }
        helper.succeed();
    }

    /** STOP on a round that goes off on bodies: it goes off at the strike, like PROCEED. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void aStoppedDetonatingRoundGoesOffAtTheStrike(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk husk = husk(helper);
        List<Vec3> struckAt = new ArrayList<>();
        DETONATIONS.clear();
        long key = fire(level, BURST, husk.position());
        Consumer<ProjectileStrikeEvent> listener = answering(key, husk,
                e -> e.setOutcome(ProjectileStrikeEvent.Outcome.STOP), struckAt);
        try {
            boolean alive = true;
            for (int tick = 0; tick < 16 && alive; tick++) {
                alive = Rounds.step(level, key);
            }
            helper.assertTrue(!alive, "round flew on after STOP");
            helper.assertTrue(struckAt.size() == 1 && DETONATIONS.size() == 1
                            && DETONATIONS.get(0).distanceTo(struckAt.get(0)) < 1.0e-6,
                    "strike " + struckAt + ", detonations " + DETONATIONS);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            Rounds.destroy(level, key);
            husk.discard();
        }
        helper.succeed();
    }

    /**
     * THROUGH: damage lands once, the round leaves at the answered velocity and keeps it, and is not struck again
     * while it is still inside the body.
     */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void aThroughRoundFliesOnAtTheAnsweredVelocityStruckOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk husk = husk(helper);
        Vec3 out = new Vec3(0.0, 0.0, -0.15);
        List<Vec3> struckAt = new ArrayList<>();
        DETONATIONS.clear();
        long key = fire(level, SLOW, husk.position());
        Consumer<ProjectileStrikeEvent> listener = answering(key, husk, e -> e.through(out), struckAt);
        float health = husk.getHealth();
        List<String> log = new ArrayList<>();
        try {
            for (int tick = 0; tick < 40; tick++) {
                boolean alive = Rounds.step(level, key);
                Vec3 v = Rounds.velocity(level, key), p = Rounds.position(level, key);
                log.add("t" + tick + " " + (alive ? "z=" + String.format("%.2f", p.z - husk.getZ()) + " vz="
                        + String.format("%.2f", v.z) : "ended") + " n=" + struckAt.size());
                helper.assertTrue(alive, "round ended: " + log);
                if (!struckAt.isEmpty()) {
                    helper.assertTrue(v.equals(out), "velocity " + v + ": " + log);
                }
            }
            Vec3 p = Rounds.position(level, key);
            helper.assertTrue(p.z < husk.getZ() - 1.0, "round never left the body: " + log);
            helper.assertTrue(struckAt.size() == 1, "struck " + struckAt.size() + " times: " + log);
            helper.assertTrue(DETONATIONS.isEmpty(), "went off: " + DETONATIONS);
            helper.assertTrue(husk.getHealth() < health && health - husk.getHealth() < 2.5f,
                    "damage once, not per tick: " + health + " -> " + husk.getHealth());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            Rounds.destroy(level, key);
            husk.discard();
        }
        helper.succeed();
    }

    /**
     * {@code post_pen}: THROUGH goes off at the burst point the answer names, else a metre along the line; STOP is
     * lost without going off; a strike nobody answers still goes off on contact.
     */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void aPostPenFuzeGoesOffInsideAfterAThroughAndNowhereElse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk husk = husk(helper);
        try {
            Vec3 inside = husk.position().add(0.0, 1.0, -0.4);
            List<Vec3> at = new ArrayList<>();
            DETONATIONS.clear();
            long named = fire(level, POST, husk.position());
            Consumer<ProjectileStrikeEvent> l1 = answering(named, husk, e -> {
                e.through(new Vec3(0.0, 0.0, -0.2));
                e.setDetonation(inside);
            }, at);
            run(level, named);
            NeoForge.EVENT_BUS.unregister(l1);
            helper.assertTrue(at.size() == 1 && DETONATIONS.size() == 1 && DETONATIONS.get(0).equals(inside),
                    "named burst point " + inside + ": " + DETONATIONS);

            DETONATIONS.clear();
            at.clear();
            long bare = fire(level, POST, husk.position());
            Consumer<ProjectileStrikeEvent> l2 = answering(bare, husk,
                    e -> e.through(new Vec3(0.0, 0.0, -0.2)), at);
            run(level, bare);
            NeoForge.EVENT_BUS.unregister(l2);
            helper.assertTrue(at.size() == 1 && DETONATIONS.size() == 1
                            && Math.abs(DETONATIONS.get(0).z - (at.get(0).z - 1.0)) < 1.0e-6
                            && Math.abs(DETONATIONS.get(0).x - at.get(0).x) < 1.0e-6,
                    "a metre along the line from " + at + ": " + DETONATIONS);

            DETONATIONS.clear();
            at.clear();
            long stopped = fire(level, POST, husk.position());
            Consumer<ProjectileStrikeEvent> l3 = answering(stopped, husk,
                    e -> e.setOutcome(ProjectileStrikeEvent.Outcome.STOP), at);
            run(level, stopped);
            NeoForge.EVENT_BUS.unregister(l3);
            helper.assertTrue(at.size() == 1 && DETONATIONS.isEmpty(), "STOP: " + at + ", " + DETONATIONS);
            helper.assertTrue(Rounds.position(level, stopped) == null, "stopped round lives");

            DETONATIONS.clear();
            at.clear();
            long plain = fire(level, POST, husk.position());
            Consumer<ProjectileStrikeEvent> l4 = answering(plain, husk, e -> { }, at);
            run(level, plain);
            NeoForge.EVENT_BUS.unregister(l4);
            helper.assertTrue(at.size() == 1 && DETONATIONS.size() == 1
                            && DETONATIONS.get(0).distanceTo(at.get(0)) < 1.0e-6,
                    "unanswered strike goes off on contact: " + at + ", " + DETONATIONS);
        } finally {
            husk.discard();
        }
        helper.succeed();
    }

    /** Steps until the round has ended or 30 ticks passed, then destroys it. */
    private static void run(ServerLevel level, long key) {
        for (int tick = 0; tick < 30 && Rounds.step(level, key); tick++) {
            // flying
        }
        Rounds.destroy(level, key);
    }

    /** Client trail: STOP ends it at the strike; THROUGH resyncs it at the answered velocity. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void theClientTrailEndsOnStopAndContinuesOnThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer watcher = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "wflib_test"),
                ClientInformation.createDefault());
        List<RoundPacket> packets = capture(level, watcher);
        Vec3 c = spot(helper);
        watcher.moveTo(c.x + 4.0, c.y, c.z, 0.0F, 0.0F);
        level.addFreshEntity(watcher);
        Husk stopHusk = husk(helper);
        Husk throughHusk = husk(helper);
        throughHusk.moveTo(c.x + 2.0, c.y, c.z);
        Vec3 out = new Vec3(0.0, 0.0, -0.15);
        long stop = fire(level, SLOW, stopHusk.position());
        long pass = fire(level, SLOW, throughHusk.position());
        List<Vec3> a = new ArrayList<>(), b = new ArrayList<>();
        Consumer<ProjectileStrikeEvent> l1 = answering(stop, stopHusk,
                e -> e.setOutcome(ProjectileStrikeEvent.Outcome.STOP), a);
        Consumer<ProjectileStrikeEvent> l2 = answering(pass, throughHusk, e -> e.through(out), b);
        helper.runAfterDelay(40, () -> {
            NeoForge.EVENT_BUS.unregister(l1);
            NeoForge.EVENT_BUS.unregister(l2);
            Rounds.destroy(level, pass);
            stopHusk.discard();
            throughHusk.discard();
            watcher.discard();
            helper.assertTrue(a.size() == 1 && b.size() == 1, "strikes " + a + " " + b);
            RoundEnded end = null;
            RoundSpawn resync = null;
            for (RoundPacket p : packets) {
                for (RoundEnded e : p.ends()) {
                    if (e.key() == stop) {
                        end = e;
                    }
                }
                for (RoundSpawn s : p.spawns()) {
                    if (s.key() == pass && s.vz() == out.z) {
                        resync = s;
                    }
                }
            }
            helper.assertTrue(end != null && end.reason() == RoundEnd.ENTITY
                            && Math.abs(end.x() - a.get(0).x) < 1.0e-9 && Math.abs(end.z() - a.get(0).z) < 1.0e-9,
                    "STOP end " + end + " at strike " + a);
            helper.assertTrue(resync != null, "THROUGH never resynced the client at " + out);
            helper.succeed();
        });
    }

    /** Swaps in a listener that has the rounds channel and keeps what is sent. */
    private static List<RoundPacket> capture(ServerLevel level, ServerPlayer player) {
        List<RoundPacket> out = new CopyOnWriteArrayList<>();
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof RoundPacket rounds) {
                    out.add(rounds);
                }
            }

            @Override
            public boolean hasChannel(ResourceLocation payloadId) {
                return RoundPacket.TYPE.id().equals(payloadId);
            }
        };
        return out;
    }

    /** Middle of the 3x3 footprint, {@link #HEIGHT} up. */
    private static Vec3 spot(GameTestHelper helper) {
        net.minecraft.core.BlockPos origin = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        return new Vec3(origin.getX() + 1.5, origin.getY() + HEIGHT, origin.getZ() + 1.5);
    }
}
