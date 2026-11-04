package com.wf.wflib.armor.lab;

import com.wf.wflib.armor.api.ArmorResolution;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.armor.api.ArmorResult;
import com.wf.wflib.explosion.api.EntityProcessorCross;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabArmors;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Round / warhead x distance x chest armour: standard blast falloff ({@link EntityProcessorCross#calculateDamage},
 * clear line of sight, density 1) through the real per-limb armour door as BLAST. Deterministic. Fragmentation
 * bomblets and shaped-charge cones are not evaluated: {@code frag_count} reports the preset's fragment budget.
 * Params: {@code distances=}, {@code armor=}.
 */
public final class ExplosiveProducer implements LabProducer {
    public static final String ID = "wflib:explosive";
    public static final String DEFAULT_DISTANCES = "0,1,2,3,5,8,12,20,30,50,80,120";

    private static final LabTable TABLE = LabTable.of("explosive")
            .dimStr("source").dimStr("source_kind").dimStr("content_origin").dimNum("distance_m", "m")
            .dimStr("armor")
            .measure("blast_size", "").measure("radius_m", "m").measure("frag_count", "")
            .measure("damage_unarmored", "hp").measureStr("protection_type").measureStr("outcome")
            .measure("through", "hp").measure("absorbed", "hp").measure("wear_points", "pt")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    /** A blast: a round with a payload, or a warhead no round references. */
    public record Source(String id, String kind, String origin, float size, int frags) {
    }

    /** Every blast source passing {@code rounds=} and the dev filter. */
    public static List<Source> sources(LabContext ctx) {
        List<Source> sources = new ArrayList<>();
        java.util.Set<ResourceLocation> referenced = new java.util.HashSet<>();
        for (KineticPreset p : KineticPresets.all()) {
            referenced.add(p.warheadId());
            float size = p.blastSize() > 0.0f ? p.blastSize() : Warheads.blastSize(p.warheadId());
            if (size > 0.0f && ctx.rounds().test(p.id().toString())
                    && (ctx.includeDev() || !LabContents.DEV_ORIGIN.equals(LabContents.origin(p.id().toString())))) {
                sources.add(new Source(p.id().toString(), "round", LabContents.origin(p.id().toString()), size,
                        p.fragmentCount()));
            }
        }
        for (ResourceLocation w : Warheads.ids()) {
            float size = Warheads.blastSize(w);
            if (size > 0.0f && !referenced.contains(w) && ctx.rounds().test(w.toString())) {
                sources.add(new Source(w.toString(), "warhead", LabContents.origin(w.toString()), size, 0));
            }
        }
        return sources;
    }

    /** {@link EntityProcessorCross} damage at nearest-surface {@code distance} in the open (density 1); null = out of range. */
    public static Float damage(EntityProcessorCross processor, float size, double distance) {
        float radius = processor.blastRadius(null, size);
        if (distance > radius) {
            return null;
        }
        double scaled = distance / radius;
        return processor.calculateDamage(scaled, 1.0, 1.0 - scaled, radius);
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        double[] distances = Arrays.stream(ctx.param("distances", DEFAULT_DISTANCES).split(","))
                .mapToDouble(Double::parseDouble).toArray();
        List<ArmorSetups.Setup> sets = LabArmors.sets(ctx).stream()
                .filter(s -> s.covers().contains(EquipmentSlot.CHEST)).toList();
        List<Source> sources = sources(ctx);
        ServerLevel level = ctx.level();
        LabRows.eachWithVictim(ctx.executor(), level, sources, (s, victim) -> source(level, s, distances, sets, sink, victim));
    }

    private static void source(ServerLevel level, Source s, double[] distances, List<ArmorSetups.Setup> sets,
                               LabSink sink, FakePlayer stand) {
        EntityProcessorCross processor = new EntityProcessorCross();
        float radius = processor.blastRadius(null, s.size());
        DamageSource src = level.damageSources().explosion(null);
        for (double d : distances) {
            Float blast = damage(processor, s.size(), d);
            if (blast == null) {
                continue;
            }
            float dmg = blast;
            row(sink, s, d, radius, dmg, "none", null, src, stand);
            for (ArmorSetups.Setup set : sets) {
                row(sink, s, d, radius, dmg, set.label(), set, src, stand);
            }
        }
    }

    private static void row(LabSink sink, Source s, double d, float radius, float dmg, String armor,
                            ArmorSetups.Setup set, DamageSource src, FakePlayer stand) {
        for (EquipmentSlot z : ArmorSetups.worn()) {
            stand.setItemSlot(z, net.minecraft.world.item.ItemStack.EMPTY);
        }
        if (set != null) {
            stand.setItemSlot(set.slot(), set.stack());
        }
        ArmorResult r = ArmorResolution.resolveSlot(stand, EquipmentSlot.CHEST, "torso", src, dmg, false);
        long wear = 0;
        for (ArmorResult.LayerWear w : r.wear()) {
            wear += w.points();
        }
        String origin = set != null && LabContents.DEV_ORIGIN.equals((set.dev() ? LabContents.DEV_ORIGIN : LabContents.SHIPPED)) ? LabContents.DEV_ORIGIN
                : s.origin();
        sink.row(s.id(), s.kind(), origin, d, armor, s.size(), radius, s.frags(), dmg,
                r.type().name().toLowerCase(Locale.ROOT), r.outcome().name().toLowerCase(Locale.ROOT), r.through(),
                r.absorbed(), wear);
    }
}
