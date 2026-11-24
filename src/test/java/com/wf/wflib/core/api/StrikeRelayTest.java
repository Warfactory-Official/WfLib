package com.wf.wflib.core.api;

import com.wf.wflib.round.api.Ricochet;
import com.wf.wflib.round.api.StrikeRelay;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.damage.api.ThreatKind;
import com.wf.wflib.damage.api.event.ProjectileStrikeEvent;
import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.RegionHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrikeRelayTest {

    /** Caller region owns x < 100. */
    private static final class Fake implements RegionHost {
        final List<Runnable> work = new ArrayList<>();

        public int currentRegion() {
            return 1;
        }

        public boolean owns(PhysicsBody body) {
            return false;
        }

        public boolean owns(ServerLevel level, BlockPos pos) {
            return pos.getX() < 100;
        }

        public void runOnOwner(ServerLevel level, BlockPos pos, Runnable w) {
            work.add(w);
        }
    }

    private static final Vec3 AT = new Vec3(500, 64, 0);
    private static final Vec3 V = new Vec3(20, 0, 0);

    private static ProjectileStrikeEvent event() {
        return new ProjectileStrikeEvent(null, 1L, null, AT, V, new Threat(ThreatKind.KINETIC, 120.0f, 300.0f))
                .allowRicochet();
    }

    /** What the owner's closure does: armour listener answers, damage lands once. */
    private static final class Owner {
        int hurt;
        ProjectileStrikeEvent answer;
        ProjectileStrikeEvent compute(java.util.function.Consumer<ProjectileStrikeEvent> armour) {
            ProjectileStrikeEvent e = event();
            armour.accept(e);
            hurt++;
            return e;
        }
    }

    @Test
    void foreignPierceContinuesReducedAndDamageLandsOnce() {
        Fake host = new Fake();
        Owner owner = new Owner();
        List<ProjectileStrikeEvent> delivered = new ArrayList<>();
        assertTrue(HitRouter.route(host, null, new BlockPos(500, 64, 0),
                StrikeRelay.work(() -> owner.compute(e -> e.through(V.scale(0.4))), delivered::add)));
        assertEquals(0, owner.hurt);
        assertTrue(delivered.isEmpty());
        host.work.get(0).run();
        assertEquals(1, owner.hurt);
        assertEquals(1, delivered.size());
        StrikeRelay.Next next = StrikeRelay.next(delivered.get(0), AT, V);
        assertFalse(next.glance());
        assertEquals(AT, next.at());
        assertEquals(V.length() * 0.4, next.velocity().length(), 1.0e-9);
        assertTrue(next.velocity().length() < V.length());
    }

    @Test
    void foreignRicochetReflectsAndLiftsOffTheSurface() {
        Vec3 out = new Vec3(-5, 0, 15);
        Fake host = new Fake();
        List<ProjectileStrikeEvent> delivered = new ArrayList<>();
        HitRouter.route(host, null, new BlockPos(500, 64, 0),
                StrikeRelay.work(() -> new Owner().compute(e -> e.ricochet(out)), delivered::add));
        host.work.get(0).run();
        StrikeRelay.Next next = StrikeRelay.next(delivered.get(0), AT, V);
        assertTrue(next.glance());
        assertEquals(out, next.velocity());
        Vec3 n = next.normal();
        assertEquals(1.0, n.length(), 1.0e-9);
        assertTrue(n.dot(V) < 0.0 && n.dot(out) > 0.0);
        assertEquals(Ricochet.OFF, next.at().subtract(AT).length(), 1.0e-9);
    }

    @Test
    void proceedAndPassKeepVelocityAtTheHit() {
        ProjectileStrikeEvent pass = event();
        pass.setOutcome(ProjectileStrikeEvent.Outcome.PASS);
        StrikeRelay.Next next = StrikeRelay.next(pass, AT, V);
        assertFalse(next.glance());
        assertEquals(V, next.velocity());
        assertEquals(AT, next.at());
        assertNull(next.normal());
    }

    @Test
    void sameRegionIsNotQueued() {
        Fake host = new Fake();
        assertFalse(HitRouter.route(host, null, new BlockPos(5, 64, 0), () -> {
        }));
        assertTrue(host.work.isEmpty());
    }
}
