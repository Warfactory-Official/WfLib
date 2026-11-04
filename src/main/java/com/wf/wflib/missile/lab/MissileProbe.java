package com.wf.wflib.missile.lab;

import com.wf.wflib.missile.api.MissileBuilder;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.missile.api.MissilePreset;
import com.wf.wflib.missile.api.WFEventType;
import com.wf.wflib.missile.api.WFTelemetry;
import com.wf.wflib.missile.api.WFTelemetryEvent;
import com.wf.wflib.missile.api.WFTelemetryService;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * One lab missile: built from its preset (warhead inert, world untouched), watched by UUID each tick (an offloaded
 * missile comes back as a new entity) and through its telemetry.
 */
final class MissileProbe {
    private final UUID id;
    private MissileEntity missile;
    private final long t0;
    private final WFTelemetry telemetry;
    private double apex = Double.NEGATIVE_INFINITY;
    private Vec3 last;
    private double lastSpeed;
    private Vec3 end;
    private int endTick = -1;
    private int fuelOutTick = -1;
    private boolean detonated;
    private boolean dud;
    private boolean downed;
    private boolean offloaded;
    private boolean timedOut;

    MissileProbe(MissileEntity missile, long t0) {
        this.missile = missile;
        this.id = missile.getUUID();
        this.t0 = t0;
        this.telemetry = WFTelemetryService.open(missile.getUUID(), t0);
        missile.attachTelemetry(this.telemetry);
        this.last = missile.position();
    }

    /** Preset launch, inert warhead, fixed UUID and entity seed. Not added to the level. */
    static MissileEntity build(ServerLevel level, MissilePreset preset, Vec3 target, Vec3 launch, @Nullable UUID team,
                               long seed) {
        MissileBuilder b = preset.builder(level, target).detonation(Warheads.rl("inert"));
        if (team != null) {
            b.teamId(team);
        }
        MissileEntity m = b.build();
        m.setUUID(UUID.nameUUIDFromBytes(("wflabs-missile-" + seed).getBytes(StandardCharsets.UTF_8)));
        m.getRandom().setSeed(seed);
        m.moveTo(launch.x, launch.y, launch.z, 0.0f, 0.0f);
        return m;
    }

    /** Telemetry as {@code TYPE@tick(x,y,z)}, for traces. */
    String trace() {
        StringBuilder sb = new StringBuilder();
        for (WFTelemetryEvent e : telemetry.events()) {
            sb.append(e.type()).append('@').append(e.gameTime() - t0)
                    .append(String.format(java.util.Locale.ROOT, "(%.0f,%.0f,%.0f) ", e.x(), e.y(), e.z()));
        }
        return sb.toString();
    }

    UUID id() {
        return id;
    }

    MissileEntity missile() {
        return missile;
    }

    /** @return true once it detonated / fizzled or lies as a dud */
    boolean step(ServerLevel level) {
        if (level.getEntity(id) instanceof MissileEntity m) {
            missile = m;
            if (m.isDud()) {
                return true;
            }
            if (!m.isRemoved()) {
                apex = Math.max(apex, m.getY());
                last = m.position();
                lastSpeed = m.getDeltaMovement().length();
            }
        }
        WFTelemetryEvent e = telemetry.latest();
        return e != null && (e.type() == WFEventType.DETONATED || e.type() == WFEventType.INTERCEPTED);
    }

    void finish(boolean timedOut) {
        this.timedOut = timedOut;
        dud = missile.isDud();
        downed = missile.damage().isDowned();
        for (WFTelemetryEvent e : telemetry.events()) {
            int t = (int) (e.gameTime() - t0);
            switch (e.type()) {
                case FUEL_OUT -> fuelOutTick = fuelOutTick < 0 ? t : fuelOutTick;
                case OFFLOAD -> offloaded = true;
                case DESTROYED -> downed = true;
                case DETONATED, INTERCEPTED -> {
                    detonated = e.type() == WFEventType.DETONATED;
                    end = e.pos();
                    endTick = t;
                }
                default -> {
                }
            }
        }
        if (dud) {
            end = missile.position();
            endTick = (int) (missile.level().getGameTime() - t0);
        }
        WFTelemetryService.close(id);
    }

    double apex() {
        return apex;
    }

    /** Detonation / fizzle / dud position; last tracked position otherwise. */
    Vec3 end() {
        return end != null ? end : last;
    }

    /** Ticks from launch to detonation, fizzle or dud; -1 = none. */
    int endTick() {
        return endTick;
    }

    /** Game time of {@link #endTick()}. */
    long endGameTime() {
        return t0 + endTick;
    }

    int fuelOutTick() {
        return fuelOutTick;
    }

    /** Blocks/tick on the last tick before it ended. */
    double lastSpeed() {
        return lastSpeed;
    }

    boolean detonated() {
        return detonated;
    }

    boolean dud() {
        return dud;
    }

    boolean downed() {
        return downed;
    }

    boolean offloaded() {
        return offloaded;
    }

    boolean timedOut() {
        return timedOut;
    }
}
