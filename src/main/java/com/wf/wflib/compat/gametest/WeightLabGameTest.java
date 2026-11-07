package com.wf.wflib.compat.gametest;

import com.warfactory.ultimateweight.UltimateWeightCommon;
import com.warfactory.ultimateweight.config.WeightConfig;
import com.warfactory.ultimateweight.core.StaminaMath;
import com.warfactory.ultimateweight.neoforge.attachment.WeightAttachments;
import com.warfactory.ultimateweight.v1211.UltimateWeight1211;
import com.warfactory.ultimateweight.v1211.WeightViews1211;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.compat.wfweight.WeightLoadoutProducer;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.radiation.gametest.NeutronActivationGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@code wfweight:loadout} rows == the live wfweight runtime on a real server player. Needs wfweight. */
@PrefixGameTestTemplate(false)
public class WeightLabGameTest {
    private static final String DRUM = "wflib:drum_20x102";

    private static List<Object[]> rows(GameTestHelper helper, Map<String, String> params) {
        WeightLoadoutProducer producer = new WeightLoadoutProducer();
        List<Object[]> out = new ArrayList<>();
        producer.produce(LabContext.of(helper.getLevel(), new double[0], id -> true, params), row -> {
            synchronized (out) {
                out.add(row);
            }
        });
        return out;
    }

    private static int col(String name) {
        List<LabTable.Column> cols = new WeightLoadoutProducer().table().columns();
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static double num(Object[] row, String name) {
        Object v = row[col(name)];
        if (v instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        return v == null ? Double.NaN : ((Number) v).doubleValue();
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer p = NeutronActivationGameTest.mockServerPlayer(helper);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    private static void fill(ServerPlayer p, String armorLabel, String kit) {
        p.getInventory().clearContent();
        if (!armorLabel.equals("none")) {
            ArmorSetups.Setup set = ArmorSetups.all().stream().filter(s -> s.label().equals(armorLabel)).findFirst()
                    .orElseThrow();
            p.setItemSlot(set.slot(), set.stack().copy());
        }
        if (!kit.equals("none")) {
            int star = kit.lastIndexOf('*');
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(kit.substring(0, star)));
            for (int i = Integer.parseInt(kit.substring(star + 1)); i > 0; i--) {
                p.getInventory().add(new ItemStack(item));
            }
        }
    }

    private static void near(GameTestHelper h, double live, double lab, String what) {
        h.assertTrue(Math.abs(live - lab) <= 1.0e-9 * Math.max(1.0, Math.abs(lab)), what + ": live " + live + ", lab " + lab);
    }

    /** total kg, speed / jump multiplier, locks, sprint and recovery ticks, one-jump drain: lab == live. */
    @GameTest(templateNamespace = WFLib.MODID, template = "empty", batch = "wflib_labs")
    public static void loadoutRowsEqualTheLiveRuntime(GameTestHelper helper) {
        ArmorSetups.Setup set = ArmorSetups.all().stream().filter(s -> s.inserts().isEmpty() && !s.dev()).findFirst()
                .orElseThrow();
        List<Object[]> rows = rows(helper, Map.of("armor", set.garment(), "kits", DRUM + "*3," + DRUM + "*6," + DRUM
                + "*9"));
        helper.assertTrue(rows.size() == 6, "rows " + rows.size());
        MinecraftServer server = helper.getLevel().getServer();
        WeightConfig.Stamina st = UltimateWeightCommon.bootstrap().config().stamina();
        double speeds = 0;
        double last = Double.NaN;
        for (Object[] row : rows) {
            String what = row[0] + " " + row[3];
            ServerPlayer p = survivalPlayer(helper);
            try {
                fill(p, (String) row[0], (String) row[3]);
                UltimateWeight1211.onPlayerJoin(p);
                near(helper, WeightViews1211.totalWeight(p), num(row, "total_kg"), what + " total_kg");
                double base = p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
                near(helper, p.getAttributeValue(Attributes.MOVEMENT_SPEED) / base, num(row, "speed_mult"),
                        what + " speed_mult");
                if (num(row, "speed_mult") != last) {
                    speeds++;
                    last = num(row, "speed_mult");
                }
                near(helper, p.getData(WeightAttachments.STAMINA.get()).maxStamina(), num(row, "max_stamina"),
                        what + " max_stamina");
                int sprintTicks = 0;
                while (!UltimateWeight1211.isSprintBlocked(p) && sprintTicks < 20 * 3600) {
                    p.setSprinting(true);
                    UltimateWeight1211.onServerTick(server);
                    sprintTicks++;
                }
                p.setSprinting(false);
                int regenTicks = 0;
                while (UltimateWeight1211.isSprintBlocked(p) && regenTicks < 20 * 3600) {
                    UltimateWeight1211.onServerTick(server);
                    regenTicks++;
                }
                near(helper, sprintTicks / 20.0, num(row, "sprint_s"), what + " sprint_s");
                near(helper, regenTicks / 20.0, num(row, "regen_s"), what + " regen_s");
            } finally {
                NeutronActivationGameTest.remove(p);
            }
            ServerPlayer j = survivalPlayer(helper);
            try {
                fill(j, (String) row[0], (String) row[3]);
                UltimateWeight1211.onPlayerJoin(j);
                double before = j.getData(WeightAttachments.STAMINA.get()).currentStamina();
                near(helper, UltimateWeight1211.jumpMultiplier(j), num(row, "jump_mult"), what + " jump_mult");
                UltimateWeight1211.onServerTick(server);
                double drain = before - j.getData(WeightAttachments.STAMINA.get()).currentStamina()
                        + st.staminaGainRate();
                near(helper, drain, before - StaminaMath.drained(st, before, before, st.jumpStaminaLoss(),
                        num(row, "total_kg"), num(row, "carry_capacity_kg")), what + " one-jump drain");
                helper.assertTrue(num(row, "jumps_to_empty") == Math.ceil((before - WeightLoadoutProducer.EPSILON) / drain),
                        what + " jumps_to_empty " + num(row, "jumps_to_empty") + " vs drain " + drain);
            } finally {
                NeutronActivationGameTest.remove(j);
            }
        }
        helper.assertTrue(speeds >= 2, "loadouts must cross a threshold, distinct speeds " + speeds);
        helper.succeed();
    }
}
