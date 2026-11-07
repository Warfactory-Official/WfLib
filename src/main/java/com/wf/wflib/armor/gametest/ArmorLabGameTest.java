package com.wf.wflib.armor.gametest;

import com.norwood.ahf.part.HitboxPart;
import com.wf.wflib.armor.api.ArmorResolution;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.armor.lab.ExplosiveProducer;
import com.wf.wflib.armor.lab.PersonalArmorProducer;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.explosion.api.EntityProcessorCross;
import com.wf.wflib.explosion.api.ExplosionAEF;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.RoundDamageSource;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** WFLabs rows == live armour door and live blast falloff. */
@PrefixGameTestTemplate(false)
public class ArmorLabGameTest {
    private static final String TEMPLATE = "empty";

    private static List<Object[]> rows(ServerLevel level, com.wf.wflib.labs.api.LabProducer producer, double[] ranges,
                                       Map<String, String> params, String round) {
        List<Object[]> out = new ArrayList<>();
        LabContext ctx = LabContext.of(level, ranges, id -> id.equals(round), params);
        producer.produce(ctx, row -> {
            synchronized (out) {
                out.add(row);
            }
        });
        return out;
    }

    /** {@code personal_armor} through/outcome == {@code ArmorResolution.applyToEvent} on a real entity. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = "wflib_labs")
    public static void personalArmorRowsEqualTheLiveDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String round = "wflib:7.62x51/ball";
        KineticPreset p = KineticPresets.get(ResourceLocation.parse(round));
        List<Object[]> rows = rows(level, new PersonalArmorProducer(), new double[]{0.0, 300.0},
                Map.of("armor", "wflib:*"), round);
        helper.assertTrue(!rows.isEmpty(), "no rows");
        int checked = 0;
        for (Object[] r : rows) {
            String armor = (String) r[4];
            String zone = (String) r[5];
            if (armor.equals("none") || (!zone.equals("chest") && !zone.equals("head"))) {
                continue;
            }
            ArmorSetups.Setup set = ArmorSetups.all().stream().filter(s -> s.label().equals(armor)).findFirst()
                    .orElseThrow();
            if (!set.covers().contains(zone.equals("head") ? EquipmentSlot.HEAD : EquipmentSlot.CHEST)) {
                continue;
            }
            double speed = (Double) r[6];
            Vec3 vel = new Vec3(speed / 20.0, 0.0, 0.0);
            Threat threat = Rounds.threat(p, vel);
            RoundDamageSource src = Rounds.source(level, p, null, null, threat, Vec3.ZERO, vel, 0L, vel,
                    RoundDamageSource.LIVE, List.of(zone.equals("head") ? HitboxPart.HEAD : HitboxPart.TORSO));
            net.minecraft.world.entity.LivingEntity v = com.wf.wflib.labs.api.LabRows.victim(level);
            v.setItemSlot(set.slot(), set.stack().copy());
            float in = ((Number) r[9]).floatValue();
            LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(v, new net.neoforged.neoforge.common.damagesource.DamageContainer(src, in));
            ArmorResolution.applyToEvent(event);
            double live = event.isCanceled() ? 0.0 : event.getAmount();
            double lab = ((Number) r[12]).doubleValue();
            helper.assertTrue(Math.abs(live - lab) <= 1.0e-4 * Math.max(1.0, lab),
                    armor + " " + zone + " @" + speed + ": live " + live + ", lab " + lab);
            checked++;
        }
        helper.assertTrue(checked >= 6, "checked " + checked);
        helper.succeed();
    }

    /** {@code explosive} unarmoured damage == the amount a real blast hands a real entity at that distance. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = "wflib_labs")
    public static void explosiveRowsEqualLiveBlasts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String round = "wflib:155mm/he";
        KineticPreset p = KineticPresets.get(ResourceLocation.parse(round));
        float size = p.blastSize() > 0.0f ? p.blastSize() : Warheads.blastSize(p.warheadId());
        List<Object[]> rows = rows(level, new ExplosiveProducer(), new double[0],
                Map.of("distances", "3,8", "armor", "none"), round);
        for (double d : new double[]{3.0, 8.0}) {
            double lab = rows.stream().filter(r -> r[4].equals("none") && ((Number) r[3]).doubleValue() == d)
                    .mapToDouble(r -> ((Number) r[8]).doubleValue()).findFirst().orElseThrow();
            BlockPos o = helper.absolutePos(BlockPos.ZERO);
            Villager v = new Villager(EntityType.VILLAGER, level);
            v.setNoAi(true);
            v.setNoGravity(true);
            v.setInvulnerable(false);
            v.moveTo(o.getX() + 0.5 + d + v.getBbWidth() / 2.0, o.getY() + 40, o.getZ() + 0.5);
            level.addFreshEntity(v);
            float[] seen = {-1.0f};
            Consumer<LivingIncomingDamageEvent> probe = e -> {
                if (e.getEntity() == v) {
                    seen[0] = e.getAmount();
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, probe);
            try {
                ExplosionAEF blast = new ExplosionAEF(level, o.getX() + 0.5, v.getY() + v.getBbHeight() / 2.0,
                        o.getZ() + 0.5, size, null);
                blast.setEntityProcessor(new EntityProcessorCross());
                blast.setPlayerProcessor(new com.wf.wflib.explosion.api.PlayerProcessorStandard());
                blast.explode();
            } finally {
                NeoForge.EVENT_BUS.unregister(probe);
                v.discard();
            }
            helper.assertTrue(Math.abs(seen[0] - lab) <= 1.0e-4, "d=" + d + ": live " + seen[0] + ", lab " + lab);
        }
        helper.succeed();
    }

    /** {@code hazards} gas rows == the armour door a real gas hit meets on a player. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = "wflib_labs")
    public static void hazardRowsEqualTheLiveDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Object[]> rows = new ArrayList<>();
        LabContext ctx = LabContext.of(level, new double[0], id -> true, Map.of("armor", "wflib:*",
                "durations", "10"));
        new com.wf.wflib.armor.lab.HazardsProducer().produce(ctx, row -> {
            synchronized (rows) {
                rows.add(row);
            }
        });
        int checked = 0;
        for (Object[] r : rows) {
            if (!r[0].equals("wflib:mustard_gas") || ((Number) r[3]).doubleValue() != 1.0 || !r[6].equals("none")
                    || r[4].equals("none") || !r[5].equals("none")) {
                continue;
            }
            ArmorSetups.Setup set = ArmorSetups.all().stream().filter(s -> s.label().equals(r[4])).findFirst()
                    .orElseThrow();
            net.minecraft.world.entity.LivingEntity v = com.wf.wflib.labs.api.LabRows.victim(level);
            v.setItemSlot(set.slot(), set.stack().copy());
            net.minecraft.world.damagesource.DamageSource src = new net.minecraft.world.damagesource.DamageSource(
                    level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(com.wf.wflib.damage.api.WFDamageTypes.GAS));
            float in = ((Number) r[9]).floatValue();
            LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(v,
                    new net.neoforged.neoforge.common.damagesource.DamageContainer(src, in));
            ArmorResolution.applyToEvent(event);
            double live = event.isCanceled() ? 0.0 : event.getAmount();
            double lab = ((Number) r[12]).doubleValue();
            helper.assertTrue(Math.abs(live - lab) <= 1.0e-4 * Math.max(1.0, lab), r[4] + ": live " + live + ", lab " + lab);
            checked++;
        }
        helper.assertTrue(checked >= 3, "checked " + checked);
        helper.succeed();
    }
}
