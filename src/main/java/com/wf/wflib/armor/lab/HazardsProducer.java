package com.wf.wflib.armor.lab;

import com.wf.wflib.armor.ArmorConfig;
import com.wf.wflib.armor.ArmorResolver;
import com.wf.wflib.armor.api.ArmorLayer;
import com.wf.wflib.armor.api.ArmorResolution;
import com.wf.wflib.armor.api.ArmorResult;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.armor.api.ArmorStacks;
import com.wf.wflib.armor.api.ProtectionType;
import com.wf.wflib.armor.api.WornArmor;
import com.wf.wflib.hazard.api.ContaminationUtil;
import com.wf.wflib.hazard.api.HazardContent;
import com.wf.wflib.hazard.api.LivingHazardProps;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabArmors;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabHazard;
import com.wf.wflib.labs.api.LabHazards;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.labs.api.LabShelter;
import com.wf.wflib.labs.api.LabShelters;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.radiation.api.RadiationConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hazard x intensity x armour x shelter x duration on a player: damage hazards through the whole-entity armour door
 * ({@link ArmorResolution#resolveWhole}); radiation through {@link ContaminationUtil#contaminate} (armour, Rad-X, dose
 * shields) once per second; armour wear = acute splash + sustained charge per exposure interval
 * ({@code ArmorResolver}). Params: {@code armor=} glob, {@code durations=} s (default 10,60,300).
 */
public final class HazardsProducer implements LabProducer {
    public static final String ID = "wflib:hazards";

    private static final LabTable TABLE = LabTable.of("hazards")
            .dimStr("hazard").dimStr("kind").dimStr("content_origin").dimNum("intensity", "x")
            .dimStr("armor").dimStr("modifier").dimStr("shelter").dimNum("duration_s", "s")
            .measure("amount_in", "hp|RAD/s").measure("amount_after_shelter", "hp|RAD/s")
            .measureStr("protection_type").measureStr("outcome")
            .measure("through_hit", "hp").measure("absorbed_hit", "hp").measure("hits", "")
            .measure("through_total", "hp").measure("dose_total_rad", "RAD").measure("dose_mult", "")
            .measure("wear_first_contact_pts", "pt").measure("wear_per_interval_pts", "pt")
            .measure("armor_life_s", "s")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    private record Cell(LabHazard hazard, double intensity) {
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        List<ArmorSetups.Setup> sets = LabArmors.sets(ctx);
        double[] durations = java.util.Arrays.stream(ctx.param("durations", "10,60,300").split(","))
                .mapToDouble(Double::parseDouble).toArray();
        List<LabShelter> shelters = LabShelters.all();
        List<Cell> cells = new ArrayList<>();
        for (LabHazard h : LabHazards.REGISTRY.values()) {
            for (double i : h.intensities()) {
                cells.add(new Cell(h, i));
            }
        }
        ServerLevel level = ctx.level();
        double[][] sheltered = LabShelters.shield(level, shelters, cells, c -> c.hazard().kind(), HazardsProducer::in);
        List<Integer> ix = java.util.stream.IntStream.range(0, cells.size()).boxed().toList();
        LabRows.eachWithVictim(ctx.executor(), level, ix,
                (i, victim) -> cell(level, cells.get(i), sets, durations, shelters, sheltered[i], sink, victim));
    }

    private static double in(Cell c) {
        LabHazard h = c.hazard();
        return h.doseRate() > 0.0 ? h.doseRate() * c.intensity() : h.damage() * c.intensity();
    }

    private static DamageSource source(ServerLevel level, String type) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(type));
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
    }

    private static void cell(ServerLevel level, Cell c, List<ArmorSetups.Setup> sets, double[] durations,
                             List<LabShelter> shelters, double[] sheltered, LabSink sink, FakePlayer victim) {
        LabHazard h = c.hazard();
        DamageSource src = h.damageType() == null ? null : source(level, h.damageType());
        victim.tickCount = 400;
        double in = in(c);
        for (int j = -1; j < shelters.size(); j++) {
            LabShelter shelter = j < 0 ? null : shelters.get(j);
            double after = j < 0 ? in : sheltered[j];
            row(sink, level, h, c.intensity(), "none", "none", shelter, in, after, src, null, durations, victim);
            if (h.doseRate() > 0.0) {
                row(sink, level, h, c.intensity(), "none", "rad_x", shelter, in, after, src, null, durations, victim);
            }
            for (ArmorSetups.Setup set : sets) {
                row(sink, level, h, c.intensity(), set.label(), "none", shelter, in, after, src, set, durations,
                        victim);
                if (h.doseRate() > 0.0) {
                    row(sink, level, h, c.intensity(), set.label(), "rad_x", shelter, in, after, src, set, durations,
                            victim);
                }
            }
        }
    }

    private static void row(LabSink sink, ServerLevel level, LabHazard h, double intensity, String armor,
                            String modifier, LabShelter shelter, double in, double after, DamageSource src,
                            ArmorSetups.Setup set, double[] durations, FakePlayer victim) {
        for (EquipmentSlot z : ArmorSetups.worn()) {
            victim.setItemSlot(z, net.minecraft.world.item.ItemStack.EMPTY);
        }
        victim.removeAllEffects();
        if (set != null) {
            victim.setItemSlot(set.slot(), set.stack().copy());
        }
        if (modifier.equals("rad_x")) {
            victim.addEffect(new MobEffectInstance(HazardContent.radx(), 1_000_000));
        }
        ProtectionType type = h.exposureType() == null ? null : ProtectionType.valueOf(h.exposureType());
        String protection = null;
        String outcome = null;
        Double through = null;
        Double absorbed = null;
        int interval = h.hitIntervalTicks();
        double mult = Double.NaN;
        if (src != null) {
            ArmorResult r = ArmorResolution.resolveWhole(victim, src, (float) after, false);
            protection = r.type().name().toLowerCase(Locale.ROOT);
            outcome = r.outcome().name().toLowerCase(Locale.ROOT);
            through = r.through();
            absorbed = r.absorbed();
        } else if (h.doseRate() > 0.0) {
            protection = "radiation";
            mult = ContaminationUtil.calculateRadiationMod(victim);
        }
        double tier = h.exposureTier() >= 0.0 ? h.exposureTier() * intensity
                : type == ProtectionType.RADIATION ? in * RadiationConfig.get().armorWearPerRad()
                : ArmorHazards.fireTier();
        long first = 0;
        long sustained = 0;
        double life = Double.POSITIVE_INFINITY;
        double intervalS = Math.max(1, ArmorConfig.get().exposureIntervalTicks()) / 20.0;
        if (type != null && tier > 0.0) {
            for (EquipmentSlot slot : ArmorResolution.SLOTS) {
                WornArmor piece = ArmorStacks.worn(victim, slot, 1.0);
                if (piece == null) {
                    continue;
                }
                for (ArmorLayer layer : piece.layers()) {
                    first += ArmorResolver.acuteWear(layer, type, ArmorConfig.get().acuteFraction());
                    int pts = ArmorResolver.sustainedWear(layer, type, tier);
                    sustained += pts;
                    if (pts > 0) {
                        life = Math.min(life, (double) layer.condition() / pts * intervalS);
                    }
                }
            }
        }
        String origin = set != null && set.dev() ? LabContents.DEV_ORIGIN : LabContents.SHIPPED;
        for (double d : durations) {
            double dose = Double.NaN;
            if (h.doseRate() > 0.0) {
                LivingHazardProps.setRadiation(victim, 0.0);
                for (int s = 0; s < (int) d; s++) {
                    ContaminationUtil.contaminate(victim, ContaminationUtil.HazardType.RADIATION,
                            ContaminationUtil.ContaminationType.CREATIVE, after);
                }
                dose = LivingHazardProps.getRadiation(victim);
            }
            long hits = src == null ? 0 : (long) (d * 20.0 / interval);
            sink.row(h.id(), h.kind(), origin, intensity, armor, modifier, shelter == null ? "none" : shelter.id(), d,
                    in, after, protection, outcome, through, absorbed, hits, through == null ? null : through * hits,
                    Double.isNaN(dose) ? null : dose, Double.isNaN(mult) ? null : mult, first, sustained,
                    Double.isInfinite(life) ? null : life);
        }
    }
}
