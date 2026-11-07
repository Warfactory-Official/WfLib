package com.wf.wflib.labs;

import com.wf.wflib.damage.api.StrikeContext;
import com.wf.wflib.damage.api.event.ProjectileStrikeEvent;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabRecorder;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.round.api.event.RoundImpactEvent;
import com.wf.wflib.round.api.event.RoundPierceEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Every real round strike (entity, damage after armour, block pierce, block stop) as rows of {@code shots}. */
final class RecorderEvents {
    static final String TABLE = "shots";
    static final LabTable SCHEMA = LabTable.of(TABLE)
            .measure("tick", "tick")
            .dimStr("kind")
            .dimStr("round")
            .dimStr("content_origin")
            .dimStr("shooter")
            .dimStr("target")
            .measure("x", "m").measure("y", "m").measure("z", "m")
            .measure("speed", "m/s")
            .dimStr("threat_kind")
            .measure("caliber", "mm")
            .measure("pen", "mm_rha")
            .dimStr("part")
            .dimStr("outcome")
            .measure("damage_in", "hp")
            .measure("damage_out", "hp")
            .measure("exit_speed", "m/s")
            .measure("spent", "")
            .build();

    private final Map<LivingIncomingDamageEvent, Float> pending = new IdentityHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void strike(ProjectileStrikeEvent e) {
        String round = roundOf(e.target().level() instanceof ServerLevel l ? l : null, e.key());
        var t = e.threat();
        LabRecorder.record(TABLE, SCHEMA, e.target().level().getGameTime(), "entity", round, origin(round),
                name(e.shooter()), name(e.target()), e.location().x, e.location().y, e.location().z,
                e.velocity().length() * 20.0, t.kind().name(), t.caliberMm(), t.penetrationMm(),
                e.parts() == null || e.parts().isEmpty() ? null : e.parts().get(0).name(), e.outcome().name(),
                null, null, null, null);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void damageIn(LivingIncomingDamageEvent e) {
        if (StrikeContext.of(e.getSource()) != null) {
            pending.put(e, e.getAmount());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void damageOut(LivingIncomingDamageEvent e) {
        Float in = pending.remove(e);
        StrikeContext ctx = StrikeContext.of(e.getSource());
        if (in == null || ctx == null) {
            return;
        }
        Object key = ctx.key();
        String round = key instanceof Long k && e.getEntity().level() instanceof ServerLevel l ? roundOf(l, k) : null;
        Vec3 p = ctx.position();
        var t = ctx.threat();
        LabRecorder.record(TABLE, SCHEMA, e.getEntity().level().getGameTime(), "damage", round, origin(round),
                name(ctx.shooter()), name(e.getEntity()), p.x, p.y, p.z, ctx.velocity().length() * 20.0,
                t.kind().name(), t.caliberMm(), t.penetrationMm(),
                ctx.parts() == null || ctx.parts().isEmpty() ? null : ctx.parts().get(0).name(),
                e.isCanceled() ? "CANCELED" : "APPLIED", in, e.isCanceled() ? 0.0f : e.getAmount(), null, null);
    }

    @SubscribeEvent
    public void pierce(RoundPierceEvent e) {
        String round = e.preset().id().toString();
        Vec3 at = e.entry().getLocation();
        var th = Rounds.threat(e.preset(), e.velocity());
        LabRecorder.record(TABLE, SCHEMA, e.level().getGameTime(), "block_pierce", round, origin(round), null,
                BuiltInRegistries.BLOCK.getKey(e.level().getBlockState(e.entry().getBlockPos()).getBlock()).toString(),
                at.x, at.y, at.z, e.velocity().length() * 20.0, th.kind().name(), th.caliberMm(), th.penetrationMm(),
                null, e.pass().exited() ? "EXITED" : "STOPPED", null, null,
                e.pass().exited() ? e.pass().velocity().length() * 20.0 : 0.0, e.pass().spent());
    }

    @SubscribeEvent
    public void impact(RoundImpactEvent e) {
        String round = e.preset().id().toString();
        Vec3 at = e.hit().getLocation();
        var th = Rounds.threat(e.preset(), e.velocity());
        LabRecorder.record(TABLE, SCHEMA, e.level().getGameTime(), "block_stop", round, origin(round), null,
                BuiltInRegistries.BLOCK.getKey(e.level().getBlockState(e.hit().getBlockPos()).getBlock()).toString(),
                at.x, at.y, at.z, e.velocity().length() * 20.0, th.kind().name(), th.caliberMm(), th.penetrationMm(),
                null, "STOPPED", null, null, 0.0, null);
    }

    private static String roundOf(ServerLevel level, Object key) {
        if (level != null && key instanceof Long k) {
            KineticPreset p = Rounds.preset(level, k);
            if (p != null) {
                return p.id().toString();
            }
        }
        return null;
    }

    private static String origin(String round) {
        return round == null ? LabContents.SHIPPED : LabContents.origin(round);
    }

    private static String name(Entity e) {
        return e == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }
}
