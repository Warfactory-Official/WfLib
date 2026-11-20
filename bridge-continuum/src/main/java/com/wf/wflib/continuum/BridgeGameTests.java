package com.wf.wflib.continuum;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.api.data.ArmorDataMaps;
import com.hbm.config.MobConfig;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.util.DamageResistanceHandler;
import com.mojang.serialization.JsonOps;
import com.wf.wflib.armor.api.ArmorAssignment;
import com.wf.wflib.armor.api.ArmorProfiles;
import com.wf.wflib.armor.api.ProtectionType;
import com.wf.wflib.colony.api.EntityGlyphid;
import dev.golem.core.world.Spawns;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * What the bridge claims, as tests. They run in a dev server that has wflib, Golem Core, Continuum and this mod
 * ({@code ./gradlew runGameTestServer -PwithContinuum} in the wflib repository). Each test states the claim in its name.
 */
@PrefixGameTestTemplate(false)
public final class BridgeGameTests {
    private BridgeGameTests() {
    }

    private static EntityType<?> entity(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) throw new IllegalStateException("no entity type " + id);
        return BuiltInRegistries.ENTITY_TYPE.get(rl);
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == net.minecraft.world.item.Items.AIR) throw new IllegalStateException("no item " + id);
        return item;
    }

    // ---------------------------------------------------------------- armour

    /** Continuum's data no longer carries a damage resistance for any item or set: its own reduction is the identity. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void continuumArmourDataIsStoodDown(GameTestHelper h) {
        long sets = ArmorDataMaps.ARMOR_SETS.byId().values().stream().filter(s -> s.resistance().isPresent()).count();
        h.assertTrue(sets == 0, sets + " Continuum armour set(s) still carry a damage resistance");
        h.assertTrue(ArmorDataMaps.ARMOR_SETS.byId().size() > 20, "the set definitions themselves (hazmat and digamma checks) must survive: " + ArmorDataMaps.ARMOR_SETS.byId().size());
        h.assertTrue(ArmorDataMaps.DAMAGE_RESISTANCE.byId().isEmpty() && ArmorDataMaps.DAMAGE_RESISTANCE.byTag().isEmpty(), "per-item resistance map is not empty");
        h.succeed();
    }

    /** A full Continuum set takes one reduction: Continuum's own function leaves the hit alone, WFLib's resolver reduces it. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void aHitOnContinuumArmourIsReducedOnce(GameTestHelper h) {
        // A mob, not a mock player: a player's hit goes out over a connection a test does not have.
        net.minecraft.world.entity.LivingEntity player = EntityType.HUSK.create(h.getLevel());
        player.moveTo(h.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        h.getLevel().addFreshEntity(player);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item("hbm:ajr_helmet")));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(item("hbm:ajr_plate")));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(item("hbm:ajr_legs")));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(item("hbm:ajr_boots")));
        Arrow arrow = EntityType.ARROW.create(h.getLevel());
        DamageSource source = player.damageSources().arrow(arrow, null);

        float continuum = DamageResistanceHandler.calculateDamage(player, source, 10.0F, 0.0F, 0.0F);
        h.assertTrue(continuum == 10.0F, "Continuum's own reduction must be the identity with the bridge on, was " + continuum);

        player.setHealth(player.getMaxHealth());
        float before = player.getHealth();
        player.hurt(source, 10.0F);
        float lost = before - player.getHealth();
        h.assertTrue(lost > 0.0F && lost < 10.0F, "WFLib must reduce the hit: lost " + lost + " of 10");
        // Continuum's set rates this at (10-4)*(1-0.15) = 5.1; its numbers seed WFLib's table. Both reductions applied would leave under a point.
        h.assertTrue(lost > 2.0F, "the hit was reduced far below its seeded value, which is what a second reduction looks like: lost " + lost);
        h.succeed();
    }

    /** The seeded profile exists per piece and splits the set: thresholds add up, resistances combine to the set's. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void seededProfilesAddUpToTheSet(GameTestHelper h) {
        double dt = 0.0D;
        double through = 1.0D;
        for (String piece : List.of("ajr_helmet", "ajr_plate", "ajr_legs", "ajr_boots")) {
            var spec = ArmorProfiles.specFor(new ItemStack(item("hbm:" + piece)));
            h.assertTrue(spec != null, piece + " has no WFLib profile");
            var row = spec.row(ProtectionType.KINETIC);
            dt += row.dt();
            through *= 1.0D - row.dr();
        }
        h.assertTrue(Math.abs(dt - 4.0D) < 0.01D, "kinetic thresholds add up to the set's 4.0, got " + dt);
        h.assertTrue(Math.abs(through - 0.85D) < 0.01D, "kinetic resistances combine to the set's 0.15, pass-through " + through);
        h.succeed();
    }

    /** The converter's maths on its own, with a made-up set: category mapping, slot split, radiation as 1 - 10^-sum. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void converterSplitsASetBySlotWeight(GameTestHelper h) {
        JsonObject sets = JsonParser.parseString("""
                {"values": {"t:plate": {"helmet": "t:helmet", "resistance": {"category": {"PHYS": {"threshold": 4.0, "resistance": 0.5}}, "other": {"resistance": 0.2}}}}}""").getAsJsonObject();
        JsonObject radiation = JsonParser.parseString("{\"values\": {\"t:plate\": 1.0}}").getAsJsonObject();
        Map<String, JsonObject> out = ArmourConverter.convert(sets, radiation);
        h.assertTrue(out.size() == 2, "a helmet and a chest piece, got " + out.keySet());
        JsonObject chest = out.get("t_plate").getAsJsonObject("rows");
        JsonObject helmet = out.get("t_helmet").getAsJsonObject("rows");
        double dtSum = chest.getAsJsonObject("kinetic").get("dt").getAsDouble() + helmet.getAsJsonObject("kinetic").get("dt").getAsDouble();
        h.assertTrue(Math.abs(dtSum - 4.0D) < 0.001D, "two pieces share the threshold 0.4/0.6 and 0.2/0.6: " + dtSum);
        double through = (1 - chest.getAsJsonObject("kinetic").get("dr").getAsDouble()) * (1 - helmet.getAsJsonObject("kinetic").get("dr").getAsDouble());
        h.assertTrue(Math.abs(through - 0.5D) < 0.001D, "resistances combine to 0.5: " + through);
        h.assertTrue(chest.has("thermal") && Math.abs(chest.getAsJsonObject("thermal").get("dr").getAsDouble() - (1 - Math.pow(0.8D, 0.4D / 0.6D))) < 0.001D, "the catch-all fills the other types");
        h.assertTrue(Math.abs(chest.getAsJsonObject("radiation").get("dr").getAsDouble() - 0.9D) < 0.001D, "radiation resistance 1.0 lets 10% through");
        h.succeed();
    }

    /** WFLib's profile codec reads a bridge-shaped element and refuses a bad one with an error, not an exception. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void armourProfileCodecReadsAndRefuses(GameTestHelper h) {
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        var ok = ArmorAssignment.CODEC.parse(ops, JsonParser.parseString("""
                {"targets": "hbm:ajr_plate", "rows": {"kinetic": {"dt": 3.0, "dr": 0.3}, "radiation": {"dr": 0.5, "tier": 4.0}},
                 "worn_floor": 0.1, "inserts": {"slots": 2, "accepts": ["kinetic"]}, "priority": 5}"""));
        h.assertTrue(ok.result().isPresent(), "valid element: " + ok.error().map(e -> e.message()).orElse("?"));
        h.assertTrue(ok.result().get().insertSlots() == 2 && ok.result().get().priority() == 5, "fields read back");
        var badType = ArmorAssignment.CODEC.parse(ops, JsonParser.parseString("{\"targets\": \"hbm:ajr_plate\", \"rows\": {\"plasma\": {\"dt\": 1}}}"));
        h.assertTrue(badType.result().isEmpty(), "an unknown protection type must be an error");
        var negative = ArmorAssignment.CODEC.parse(ops, JsonParser.parseString("{\"targets\": \"hbm:ajr_plate\", \"rows\": {\"kinetic\": {\"dt\": -1}}}"));
        h.assertTrue(negative.result().isEmpty(), "a negative row must be an error");
        h.succeed();
    }

    // ---------------------------------------------------------------- doors

    /** Every Continuum assembly recipe that made one of its doors now makes the WFLib door of that name. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void continuumDoorRecipesMakeWflibDoors(GameTestHelper h) throws IOException {
        var info = ModList.get().getModFileById("hbm");
        Path dir = info.getFile().findResource("data/hbm/recipe/assembly_machine");
        int checked = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                JsonObject json;
                try (Reader in = Files.newBufferedReader(file)) {
                    json = JsonParser.parseReader(in).getAsJsonObject();
                }
                if (!json.has("output_items") || json.getAsJsonArray("output_items").isEmpty()) continue;
                String out = json.getAsJsonArray("output_items").get(0).isJsonPrimitive() ? json.getAsJsonArray("output_items").get(0).getAsString() : "";
                if (!out.startsWith("hbm:") || !isDoor(out.substring(4))) continue;
                GenericRecipe recipe = AssemblyMachineRecipes.INSTANCE.getRecipe(json.get("name").getAsString());
                h.assertTrue(recipe != null, "no recipe " + json.get("name"));
                String made = BuiltInRegistries.ITEM.getKey(recipe.outputItem[0].getItem()).toString();
                h.assertTrue(made.equals("wflib:" + out.substring(4)), json.get("name") + " makes " + made + ", wanted wflib:" + out.substring(4));
                checked++;
            }
        }
        h.assertTrue(checked == 15, "all fifteen door recipes, saw " + checked);
        h.succeed();
    }

    private static boolean isDoor(String name) {
        return List.of("transition_seal", "vault_door", "fire_door", "sliding_blast_door", "sliding_seal_door", "secure_access_door", "round_airlock_door",
                "qe_sliding_door", "qe_containment", "water_door", "silo_hatch", "silo_hatch_large", "large_vehicle_door", "cargo_door", "blast_door").contains(name);
    }

    // ---------------------------------------------------------------- glyphids

    /** Continuum's natural glyphid spawns are vetoed; a vanilla mob and a WFLib glyphid are not. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void continuumGlyphidsDoNotSpawnNaturally(GameTestHelper h) {
        Mob continuumGlyphid = (Mob) entity("hbm:entity_glyphid").create(h.getLevel());
        Mob zombie = EntityType.ZOMBIE.create(h.getLevel());
        Mob ours = (Mob) entity("wflib:glyphid").create(h.getLevel());
        boolean[] denied = new boolean[3];
        for (Spawns.SpawnCheck check : Spawns.CHECKS) {
            denied[0] |= check.deny(continuumGlyphid, h.getLevel(), Spawns.Reason.NATURAL);
            denied[1] |= check.deny(zombie, h.getLevel(), Spawns.Reason.NATURAL);
            denied[2] |= check.deny(ours, h.getLevel(), Spawns.Reason.NATURAL);
        }
        h.assertTrue(denied[0], "Continuum's glyphid must be refused");
        h.assertTrue(!denied[1] && !denied[2], "a zombie and a WFLib glyphid must not be: " + denied[1] + " " + denied[2]);
        h.succeed();
    }

    /** The hive switch-off took: the override beat the file and reached the field the world generation reads. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void continuumHivesAreOff(GameTestHelper h) {
        h.assertTrue(!MobConfig.enableHives, "MobConfig.enableHives is still on");
        h.succeed();
    }

    /** A dying WFLib glyphid drops Continuum's meat, at its odds (half the time), and only that. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty", timeoutTicks = 100)
    public static void wflibGlyphidsDropContinuumMeat(GameTestHelper h) {
        Item meat = item("hbm:glyphid_meat");
        int total = 0;
        for (int i = 0; i < 60; i++) {
            EntityGlyphid g = (EntityGlyphid) entity("wflib:glyphid").create(h.getLevel());
            Vec3 at = h.absoluteVec(new Vec3(2.5, 2.0, 2.5));
            g.moveTo(at.x, at.y, at.z);
            h.getLevel().addFreshEntity(g);
            g.hurt(g.damageSources().genericKill(), 1000.0F);
        }
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absoluteVec(new Vec3(0, 0, 0)), h.absoluteVec(new Vec3(8, 6, 8))))) {
            h.assertTrue(e.getItem().is(meat), "only meat drops, got " + e.getItem());
            total += e.getItem().getCount();
        }
        h.assertTrue(total > 20, "about half of 60 glyphids drop at least 2 meat: " + total);
        h.succeed();
    }
}
