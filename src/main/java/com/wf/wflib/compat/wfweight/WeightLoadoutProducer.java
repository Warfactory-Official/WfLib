package com.wf.wflib.compat.wfweight;

import com.warfactory.ultimateweight.UltimateWeightCommon;
import com.warfactory.ultimateweight.api.StaminaModifiers;
import com.warfactory.ultimateweight.api.WeightPlayerView;
import com.warfactory.ultimateweight.config.WeightConfig;
import com.warfactory.ultimateweight.core.InventoryConstraintEvaluator;
import com.warfactory.ultimateweight.core.OverweightDamageMath;
import com.warfactory.ultimateweight.core.PlayerWeightTracker;
import com.warfactory.ultimateweight.core.StaminaMath;
import com.warfactory.ultimateweight.core.WeightSnapshot;
import com.warfactory.ultimateweight.runtime.UltimateWeightServices;
import com.warfactory.ultimateweight.v1211.WeightViews1211;
import com.wf.wflib.ammo.api.Ammo;
import com.wf.wflib.ammo.api.AmmoItems;
import com.wf.wflib.armor.api.ArmorResolution;
import com.wf.wflib.armor.api.ArmorResult;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.armor.api.ArmorStacks;
import com.wf.wflib.armor.api.ProtectionType;
import com.wf.wflib.armor.api.WornArmor;
import com.wf.wflib.labs.api.LabArmors;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Armour piece x weapon x ammo load x kit on a real player inventory: wfweight's own stack weights, carry capacity,
 * threshold resolution ({@code PlayerWeightTracker.refresh}), group limits and stamina arithmetic
 * ({@code StaminaMath}); protection from the real armour door. Params: {@code armor=} glob, {@code inserts=1} adds
 * plate-filled sets, {@code weapons=} item id globs (default none), {@code ammo=} / {@code kits=} comma list of loads
 * {@code id*count[+id*count]} ({@code id} an ammo variant or an item; default none), {@code ref_hp=} (default 20).
 * Not mapped: fall damage multiplier, backpack contents, creative immunity.
 */
public final class WeightLoadoutProducer implements LabProducer {
    public static final String ID = "wfweight:loadout";
    public static final double EPSILON = 0.000001D;
    static final int SPRINT_CAP_TICKS = 20 * 3600;

    private static final LabTable TABLE = LabTable.of("wfweight:loadout")
            .dimStr("armor").dimStr("weapon").dimStr("ammo").dimStr("kit").dimStr("content_origin")
            .measure("total_kg", "kg").measure("armor_kg", "kg").measure("weapon_kg", "kg").measure("ammo_kg", "kg")
            .measure("kit_kg", "kg").measure("unplaced", "").measure("carry_capacity_kg", "kg")
            .measure("load_frac", "").measure("threshold_index", "").measure("speed_mult", "")
            .measure("jump_mult", "").measure("speed_locked", "").measure("jump_locked", "")
            .measure("hard_locked", "").measure("overweight_hp_s", "hp/s")
            .measure("max_stamina", "").measure("stamina_use_mult", "").measure("sprint_s", "s")
            .measure("regen_s", "s").measure("jumps_to_empty", "")
            .measure("groups_over", "").measureStr("group_over")
            .measure("kinetic_through_frac", "").measure("blast_through_frac", "")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    private record Load(String label, List<ItemStack> stacks) {
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        boolean inserts = "1".equals(ctx.param("inserts", "0"));
        List<ArmorSetups.Setup> sets = LabArmors.sets(ctx).stream().filter(s -> inserts || s.inserts().isEmpty())
                .toList();
        List<ArmorSetups.Setup> armors = new ArrayList<>();
        armors.add(null);
        armors.addAll(sets);
        List<Load> weapons = weapons(ctx.param("weapons", "none"));
        List<Load> ammo = loads(ctx.param("ammo", "none"));
        List<Load> kits = loads(ctx.param("kits", "none"));
        float ref = (float) ctx.paramNum("ref_hp", 20.0);
        LabRows.eachWithVictim(ctx.executor(), ctx.level(), armors, (set, victim) ->
                armor(set, victim, weapons, ammo, kits, ref, sink));
    }

    private static void armor(ArmorSetups.Setup set, FakePlayer victim, List<Load> weapons, List<Load> ammo,
                              List<Load> kits, float ref, LabSink sink) {
        for (EquipmentSlot z : ArmorSetups.worn()) {
            victim.setItemSlot(z, ItemStack.EMPTY);
        }
        double armorKg = 0.0;
        double kinetic = 1.0;
        double blast = 1.0;
        if (set != null) {
            ItemStack worn = set.stack().copy();
            victim.setItemSlot(set.slot(), worn);
            armorKg = WeightViews1211.weightOf(worn);
            kinetic = through(victim, set.slot(), ProtectionType.KINETIC, ref);
            blast = through(victim, set.slot(), ProtectionType.BLAST, ref);
        }
        String origin = set != null && set.dev() ? LabContents.DEV_ORIGIN : LabContents.SHIPPED;
        for (Load w : weapons) {
            for (Load a : ammo) {
                for (Load k : kits) {
                    Inventory inv = victim.getInventory();
                    inv.clearContent();
                    if (set != null) {
                        victim.setItemSlot(set.slot(), set.stack().copy());
                    }
                    double[] kg = new double[3];
                    int unplaced = place(inv, w, kg, 0) + place(inv, a, kg, 1) + place(inv, k, kg, 2);
                    row(sink, victim, set == null ? "none" : set.label(), w.label(), a.label(), k.label(),
                            origin(origin, w), armorKg, kg, unplaced, kinetic, blast);
                }
            }
        }
    }

    private static String origin(String armorOrigin, Load weapon) {
        if (LabContents.DEV_ORIGIN.equals(armorOrigin) || weapon.stacks().isEmpty()) {
            return armorOrigin;
        }
        return LabContents.origin(BuiltInRegistries.ITEM.getKey(weapon.stacks().get(0).getItem()).toString());
    }

    private static double through(FakePlayer victim, EquipmentSlot slot, ProtectionType type, float ref) {
        WornArmor worn = ArmorStacks.worn(victim, slot, 1.0);
        ArmorResult r = ArmorResolution.resolve(victim, type, ref, worn == null ? List.of() : List.of(worn), null,
                false);
        return r.through() / ref;
    }

    /** Stacks into {@code inv}; kg of what fitted added to {@code kg[bucket]}; returns the count that did not fit. */
    private static int place(Inventory inv, Load load, double[] kg, int bucket) {
        int unplaced = 0;
        for (ItemStack proto : load.stacks()) {
            ItemStack rest = proto.copy();
            inv.add(rest);
            int placed = proto.getCount() - rest.getCount();
            unplaced += rest.getCount();
            if (placed > 0) {
                ItemStack fitted = proto.copy();
                fitted.setCount(placed);
                kg[bucket] += WeightViews1211.weightOf(fitted);
            }
        }
        return unplaced;
    }

    private static void row(LabSink sink, FakePlayer victim, String armor, String weapon, String ammo, String kit,
                            String origin, double armorKg, double[] kg, int unplaced, double kinetic, double blast) {
        UltimateWeightServices svc = UltimateWeightCommon.bootstrap();
        WeightConfig config = svc.config();
        WeightPlayerView view = WeightViews1211.player(victim);
        PlayerWeightTracker tracker = svc.playerWeightTracker();
        String id = victim.getStringUUID();
        tracker.markDirty(id);
        WeightSnapshot snap = tracker.refresh(view, 0L).snapshot();
        tracker.clear(id);
        var effect = snap.thresholdEffect();
        double total = snap.totalWeightKg();
        double cap = snap.carryCapacityKg();
        InventoryConstraintEvaluator evaluator = svc.constraintEvaluator();
        double max = evaluator.resolveMaxStamina(view);
        WeightConfig.Stamina st = config.stamina();
        boolean staminaOn = StaminaMath.isEnabled(st, max);
        double recovery = StaminaModifiers.recoveryPerTick(victim) * max;
        double[] sprint = staminaOn && st.drainWhileRunning() ? sprint(st, max, total, cap, recovery) : null;
        var over = new StringBuilder();
        int overCount = 0;
        InventoryConstraintEvaluator.GroupLimitState groups = evaluator.resolveGroupLimitState(view);
        for (String g : groups.groupIds()) {
            if (groups.count(g) > groups.limit(g)) {
                overCount++;
                over.append(over.length() == 0 ? "" : ";").append(g).append(':').append(groups.count(g)).append('/')
                        .append(groups.limit(g));
            }
        }
        WeightConfig.OverweightDamage ow = config.overweightDamage();
        double hpS = OverweightDamageMath.resolveDamage(ow, total, cap, snap.hardLocked()) * 20.0
                / Math.max(1, ow.intervalTicks());
        sink.row(armor, weapon, ammo, kit, origin, total, armorKg, kg[0], kg[1], kg[2], unplaced, cap,
                cap <= EPSILON ? 0.0 : total / cap, effect.thresholdIndex(), effect.speedMultiplier(),
                effect.jumpMultiplier(), effect.speedMultiplier() <= EPSILON, effect.jumpMultiplier() <= EPSILON,
                snap.hardLocked(), hpS, max, staminaOn ? StaminaMath.resolveUseMultiplier(st, total, cap) : null,
                sprint == null ? null : sprint[0], sprint == null ? null : sprint[1],
                staminaOn && st.drainOnJump() ? jumps(st, max, total, cap) : null, overCount,
                overCount == 0 ? null : over.toString(), kinetic, blast);
    }

    /** {full-to-exhausted s, exhausted-to-recovered s}; null entries when not reached within the cap. */
    private static double[] sprint(WeightConfig.Stamina st, double max, double total, double cap, double recovery) {
        double cur = max;
        int t = 0;
        boolean exhausted = false;
        while (!exhausted && t < SPRINT_CAP_TICKS) {
            cur = StaminaMath.drained(st, cur, max, st.sprintStaminaLossRate(), total, cap);
            cur = StaminaMath.clamp(cur + recovery, max);
            t++;
            exhausted = StaminaMath.resolveExhausted(st, cur, max, true, false);
        }
        if (!exhausted) {
            return new double[]{Double.NaN, Double.NaN};
        }
        int sprintTicks = t;
        t = 0;
        while (exhausted && t < SPRINT_CAP_TICKS) {
            cur = StaminaMath.clamp(cur + st.staminaGainRate(), max);
            cur = StaminaMath.clamp(cur + recovery, max);
            t++;
            exhausted = StaminaMath.resolveExhausted(st, cur, max, true, true);
        }
        return new double[]{sprintTicks / 20.0, exhausted ? Double.NaN : t / 20.0};
    }

    private static Integer jumps(WeightConfig.Stamina st, double max, double total, double cap) {
        double cur = max;
        int n = 0;
        while (cur > EPSILON && n < 100000) {
            cur = StaminaMath.drained(st, cur, max, st.jumpStaminaLoss(), total, cap);
            n++;
        }
        return cur > EPSILON ? null : n;
    }

    private static List<Load> weapons(String param) {
        List<Load> out = new ArrayList<>();
        for (String g : param.split(",")) {
            String glob = g.trim();
            if (glob.equals("none")) {
                out.add(new Load("none", List.of()));
                continue;
            }
            int before = out.size();
            for (Item item : BuiltInRegistries.ITEM) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                if (LabArmors.glob(glob, id)) {
                    out.add(new Load(id, List.of(new ItemStack(item))));
                }
            }
            if (out.size() == before) {
                throw new IllegalArgumentException("weapons glob matches no item: " + glob);
            }
        }
        return out;
    }

    private static List<Load> loads(String param) {
        List<Load> out = new ArrayList<>();
        for (String spec : param.split(",")) {
            String label = spec.trim();
            if (label.equals("none")) {
                out.add(new Load("none", List.of()));
                continue;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (String part : label.split("\\+")) {
                int star = part.lastIndexOf('*');
                if (star < 0) {
                    throw new IllegalArgumentException("load part needs id*count: " + part);
                }
                ResourceLocation id = ResourceLocation.parse(part.substring(0, star));
                int count = Integer.parseInt(part.substring(star + 1));
                stack(id, count, stacks);
            }
            out.add(new Load(label, stacks));
        }
        return out;
    }

    private static void stack(ResourceLocation id, int count, List<ItemStack> out) {
        ItemStack one = Ammo.variant(id) != null ? AmmoItems.stack(id, 1) : new ItemStack(
                BuiltInRegistries.ITEM.getOptional(id).orElseThrow(
                        () -> new IllegalArgumentException("no ammo variant or item " + id)));
        int per = one.getMaxStackSize();
        for (int left = count; left > 0; left -= per) {
            ItemStack s = one.copy();
            s.setCount(Math.min(per, left));
            out.add(s);
        }
    }
}
