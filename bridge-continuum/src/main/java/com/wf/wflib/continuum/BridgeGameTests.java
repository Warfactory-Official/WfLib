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
import net.minecraft.world.level.Level;
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
    // ---------------------------------------------------------------- radiation and hazards

    /** WFLib reads the field Continuum's code writes, and what WFLib feeds lands in Continuum's field. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void wflibReadsAndFeedsContinuumsRadiationField(GameTestHelper h) {
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        net.minecraft.core.BlockPos a = h.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        net.minecraft.core.BlockPos b = a.offset(4, 0, 0);
        com.hbm.handler.radiation.ChunkRadiationManager.proxy.setRadiation(level, a, 40.0D);
        h.assertTrue(com.wf.wflib.radiation.api.RadiationField.at(level, a) >= 40.0D, "WFLib must read Continuum's field: " + com.wf.wflib.radiation.api.RadiationField.at(level, a));
        h.assertTrue(com.wf.wflib.radiation.api.RadiationField.dose(level, a) >= 40.0D, "the dose rate comes from the same field");
        com.wf.wflib.radiation.api.RadiationField.source(level, b, 10.0D, 500.0D);
        h.assertTrue(com.hbm.handler.radiation.ChunkRadiationManager.proxy.getRadiation(level, b) > 0.0D, "what WFLib feeds must land in Continuum's field");
        com.hbm.handler.radiation.ChunkRadiationManager.proxy.setRadiation(level, a, 0.0D);
        com.hbm.handler.radiation.ChunkRadiationManager.proxy.setRadiation(level, b, 0.0D);
        h.succeed();
    }

    /** A player's radiation is WFLib's alone: Continuum's contamination gate refuses players, and still takes other creatures. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void playersTakeWflibsDoseNotContinuums(GameTestHelper h) {
        // A fake player, not a mock one: a mock player is on the server's list and the next tick sends it packets it has no connection for.
        net.minecraft.world.entity.player.Player player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        net.minecraft.world.entity.animal.Pig pig = EntityType.PIG.create(h.getLevel());
        h.assertTrue(com.hbm.util.ContaminationUtil.isRadImmune(player), "Continuum must see a player as immune to its radiation");
        h.assertTrue(!com.hbm.util.ContaminationUtil.isRadImmune(pig), "a pig stays Continuum's");
        h.succeed();
    }

    /** Every id-keyed Continuum hazard row has a WFLib row with the same types and levels (the types WFLib names). */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void hazardRowsMatchContinuumsData(GameTestHelper h) {
        int checked = 0;
        for (Map.Entry<String, com.hbm.hazard.HazardSpec> e : com.hbm.api.data.HazardDataMaps.HAZARDS.byId().entrySet()) {
            if (e.getValue().immune()) continue;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.getKey()));
            if (item == net.minecraft.world.item.Items.AIR) continue;
            var wf = com.wf.wflib.hazard.api.HazardRules.itemData().get(item);
            boolean mappable = e.getValue().entries().stream().anyMatch(en -> List.of("radiation", "contaminating", "digamma", "hot", "blinding", "asbestos", "coal", "hydroactive", "explosive", "toxic", "cold").contains(en.type().replace("hbm:", "")));
            if (!mappable) continue;
            h.assertTrue(wf != null, "no WFLib hazard row for " + e.getKey());
            for (var en : e.getValue().entries()) {
                String name = en.type().replace("hbm:", "");
                var match = wf.entries.stream().filter(x -> com.wf.wflib.hazard.api.Hazards.idOf(x.type).getPath().equals(name)).findFirst();
                if (match.isEmpty()) continue;                       // a type WFLib has no name for
                h.assertTrue(Math.abs(match.get().baseLevel - en.level()) < 1e-6, e.getKey() + " " + name + ": " + match.get().baseLevel + " vs " + en.level());
            }
            checked++;
        }
        h.assertTrue(checked > 20, "a useful number of rows compared: " + checked);
        h.succeed();
    }

    /** The pieces of a Continuum set that passes a protection check are in the WFLib tag that check reads. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void protectionTagsFollowContinuumsSets(GameTestHelper h) {
        int hazmat = 0, advanced = 0;
        for (Map.Entry<String, com.hbm.api.data.ArmorDataMaps.ArmorSet> e : ArmorDataMaps.ARMOR_SETS.byId().entrySet()) {
            List<String> pieces = new java.util.ArrayList<>(List.of(e.getKey()));
            e.getValue().helmet().ifPresent(pieces::add);
            e.getValue().legs().ifPresent(pieces::add);
            e.getValue().boots().ifPresent(pieces::add);
            for (String check : e.getValue().checks()) {
                var tag = switch (check) {
                    case "hazmat" -> com.wf.wflib.hazard.api.HazardContent.HAZMAT;
                    case "hazmat2" -> com.wf.wflib.hazard.api.HazardContent.HAZMAT_ADVANCED;
                    case "digamma", "digamma2" -> com.wf.wflib.hazard.api.HazardContent.DIGAMMA_PROTECTION;
                    default -> null;
                };
                if (tag == null) continue;
                for (String piece : pieces) {
                    h.assertTrue(new ItemStack(item(piece)).is(tag), piece + " passes '" + check + "' in Continuum but is not in " + tag.location());
                }
                if (check.equals("hazmat")) hazmat++;
                if (check.equals("hazmat2")) advanced++;
            }
        }
        h.assertTrue(hazmat > 0 && advanced > 0, "both kinds of set seen: " + hazmat + " " + advanced);
        h.succeed();
    }
    // ---------------------------------------------------------------- nukes

    private static final int PARITY_STRENGTH = 48, PARITY_RADIUS = 24;

    private static int cube(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos centre, int half, net.minecraft.world.level.block.Block block) {
        int n = 0;
        for (int x = -half; x <= half; x++) for (int y = -half; y <= half; y++) for (int z = -half; z <= half; z++) {
            level.setBlock(centre.offset(x, y, z), block.defaultBlockState(), 2);
            n++;
        }
        return n;
    }

    private static int remaining(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos centre, int half) {
        int n = 0;
        for (int x = -half; x <= half; x++) for (int y = -half; y <= half; y++) for (int z = -half; z <= half; z++) {
            if (!level.getBlockState(centre.offset(x, y, z)).isAir()) n++;
        }
        return n;
    }

    /**
     * Same bomb, two rays: Continuum's own batched ray and WFLib's, each into its own solid block of {@code block}, then the blocks left are compared. The
     * bridge's {@code nukes} switch stays off until the two agree. <b>They do not today</b>: in stone WFLib's ray removes about 2.3 times the blocks Continuum's
     * does for the same strength and radius, so these two tests are optional ({@code required = false}) and report the numbers rather than fail the run. Which of
     * the two is NTM's is the question; see the bridge doc.
     */
    private static void parity(GameTestHelper h, net.minecraft.world.level.block.Block block, double tolerance, int y) {
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        int half = PARITY_RADIUS + 3;
        // a height of its own: neighbouring tests' cubes and craters must not overlap this one
        net.minecraft.core.BlockPos a = new net.minecraft.core.BlockPos(h.absolutePos(net.minecraft.core.BlockPos.ZERO).getX(), y, h.absolutePos(net.minecraft.core.BlockPos.ZERO).getZ());
        net.minecraft.core.BlockPos b = a.offset(0, 0, 90);
        int total = cube(level, a, half, block);
        cube(level, b, half, block);
        com.hbm.interfaces.IExplosionRay theirs = new com.hbm.explosion.ExplosionNukeRayBatched(level, a.getX(), a.getY(), a.getZ(), PARITY_STRENGTH, PARITY_RADIUS);
        com.hbm.interfaces.IExplosionRay ours = new Mk5Bridge.WflibRay(level, b.getX(), b.getY(), b.getZ(), PARITY_STRENGTH, PARITY_RADIUS);
        int[] state = {0, -1, -1, 0};   // ticks since both rays finished, last counts, consecutive unchanged samples
        h.onEachTick(() -> {
            if (!theirs.isComplete()) theirs.update(40);
            if (!ours.isComplete()) ours.update(40);
            // "complete" is when a ray finishes, not when its world writes have landed (Continuum's batched writes run on for hundreds of ticks); count every ten ticks and
            // take the result when neither count has moved for three samples in a row
            if (!(theirs.isComplete() && ours.isComplete()) || ++state[0] % 10 != 0) return;
            int gone1 = total - remaining(level, a, half), gone2 = total - remaining(level, b, half);
            state[3] = gone1 == state[1] && gone2 == state[2] ? state[3] + 1 : 0;
            state[1] = gone1;
            state[2] = gone2;
            if (state[3] < 3) return;
            h.assertTrue(gone1 > 500 && gone2 > 500, "both craters are real: " + gone1 + " and " + gone2);
            double ratio = Math.abs(gone1 - gone2) / (double) Math.max(gone1, gone2);
            h.assertTrue(ratio < tolerance, block.getName().getString() + ": the craters differ by " + Math.round(ratio * 1000) / 10.0 + "% (Continuum " + gone1 + " blocks, WFLib " + gone2 + ")");
            h.succeed();
        });
    }

    /**
     * Optional: in stone the two do not agree and Continuum's own number is not stable. Over seven runs Continuum's ray removed 185, 1255, 1255, 1255, about 2900,
     * about 2900 and 53931 blocks from the same cube, while WFLib's removed 2838 to 2907 every time, so the bridge's {@code nukes} switch is off by default until that is
     * understood.
     */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty", timeoutTicks = 4000, required = false)
    public static void mk5RaysAgreeInStone(GameTestHelper h) {
        parity(h, net.minecraft.world.level.block.Blocks.STONE, 0.10D, 0);
    }

    @GameTest(templateNamespace = "wflib_continuum", template = "empty", timeoutTicks = 4000)
    public static void mk5RaysAgreeInDirt(GameTestHelper h) {
        parity(h, net.minecraft.world.level.block.Blocks.DIRT, 0.10D, 120);
    }
    // ---------------------------------------------------------------- creative tabs

    /** No creative tab lists a Continuum door; the WFLib doors are the ones on offer. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void continuumDoorsAreOffTheCreativeTabs(GameTestHelper h) {
        var params = new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess());
        int tabs = 0;
        for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (!key.getNamespace().equals("hbm")) continue;
            tab.buildContents(params);
            tabs++;
            for (ItemStack stack : tab.getDisplayItems()) {
                h.assertTrue(!DoorRecipes.isContinuumDoor(stack), "tab " + key + " still lists " + stack.getItem());
            }
        }
        h.assertTrue(tabs > 0, "Continuum's tabs were built");
        h.succeed();
    }
    // ---------------------------------------------------------------- switchboard

    /** The bridge's group is on WFLib's switchboard screen with a box per bridge, and each box reads and writes its config. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void theBridgeIsOnTheSwitchboard(GameTestHelper h) {
        var source = com.wf.wflib.core.api.Features.sources().stream().filter(x -> x.id().equals("ntm_continuum")).findFirst().orElse(null);
        h.assertTrue(source != null, "the bridge's group is registered");
        h.assertTrue(source.boxes().stream().map(com.wf.wflib.core.api.FeatureSource.Box::id).toList().equals(List.of("armour", "doors", "glyphids", "radiation", "nukes", "masses", "probe", "wiki", "soldiers", "warheads")), "one box per bridge");
        boolean before = source.get("nukes");
        source.set("nukes", !before);
        h.assertTrue(source.get("nukes") == !before, "a box writes through to the config");
        source.set("nukes", before);
        h.assertTrue(source.startup("armour") == BridgeConfig.armour() || !source.startupMaster(), "startup state matches what is running");
        h.succeed();
    }
    // ---------------------------------------------------------------- masses

    private static Item firstOf(net.minecraft.tags.TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag).flatMap(set -> set.stream().findFirst()).map(net.minecraft.core.Holder::value).orElse(null);
    }

    /** A steel ingot is about a kilogram, a plate the same, a block nine of it; a gun has no mass here. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void materialItemsGetAMassFromTheirDensity(GameTestHelper h) {
        if (!ModList.get().isLoaded("wfweight")) {
            h.succeed();
            return;
        }
        ContinuumMasses masses = new ContinuumMasses();
        Item ingot = firstOf(com.hbm.inventory.material.Mats.MAT_STEEL.tag(com.hbm.inventory.material.MaterialShapes.INGOT));
        Item plate = firstOf(com.hbm.inventory.material.Mats.MAT_STEEL.tag(com.hbm.inventory.material.MaterialShapes.PLATE));
        Item block = firstOf(com.hbm.inventory.material.Mats.MAT_STEEL.tag(com.hbm.inventory.material.MaterialShapes.BLOCK));
        h.assertTrue(ingot != null && plate != null && block != null, "steel has an ingot, a plate and a block item: " + ingot + " " + plate + " " + block + " tags " + com.hbm.inventory.material.Mats.MAT_STEEL.tag(com.hbm.inventory.material.MaterialShapes.INGOT).location() + " " + BuiltInRegistries.ITEM.getTagNames().filter(t -> t.location().getPath().contains("steel")).limit(6).toList());
        double kg = masses.kg(ingot);
        h.assertTrue(Math.abs(kg - 0.98125D) < 0.01D, "a steel ingot is about 0.98 kg: " + kg);
        h.assertTrue(Math.abs(masses.kg(plate) - kg) < 0.01D, "a plate is an ingot (72 units): " + masses.kg(plate));
        h.assertTrue(Math.abs(masses.kg(block) - kg * 9) < 0.05D, "a block is nine ingots: " + masses.kg(block));
        Item lead = firstOf(com.hbm.inventory.material.Mats.MAT_LEAD.tag(com.hbm.inventory.material.MaterialShapes.INGOT));
        h.assertTrue(masses.kg(lead) > kg, "lead is heavier than steel");
        Item gun = item("hbm:gun_am180");
        h.assertTrue(masses.kg(gun) == 0.0D, "a gun is not a material item");
        h.succeed();
    }
    // ---------------------------------------------------------------- look-at

    /** A Continuum readout becomes probe lines: the title, and each line with its legacy colour prefix honoured. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void continuumReadoutsBecomeProbeLines(GameTestHelper h) {
        var info = new com.wf.wflib.probe.api.ProbeInfo();
        LookOverlayBridge.fill(info, new com.hbm.interfaces.ILookOverlay.Data("Reactor", 0xFF8800, 0, List.of("&[65280&]Core 400 C", "plain line", "&[bad&]odd")));
        h.assertTrue(info.titleText() != null && info.titleText().getString().equals("Reactor"), "the title");
        h.assertTrue(info.titleColour() == (0xFF000000 | 0xFF8800), "the title colour");
        var texts = info.elements().stream().filter(e -> e instanceof com.wf.wflib.probe.api.ProbeElement.Text).map(e -> (com.wf.wflib.probe.api.ProbeElement.Text) e).toList();
        h.assertTrue(texts.size() == 3, "a line each: " + texts.size());
        h.assertTrue(texts.get(0).text().getString().equals("Core 400 C") && texts.get(0).colour() == (0xFF000000 | 65280), "a coloured line");
        h.assertTrue(texts.get(1).text().getString().equals("plain line") && texts.get(1).colour() == 0xFFFFFFFF, "a plain line is white");
        h.assertTrue(texts.get(2).text().getString().equals("odd"), "a bad colour prefix is dropped, the text kept: " + texts.get(2).text().getString());
        h.succeed();
    }
    // ---------------------------------------------------------------- wiki

    /** The converter on a made-up page: links in both orders, a break, trigger items in the shapes Continuum uses, a link to nowhere. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void manualPagesBecomeWikiArticles(GameTestHelper h) {
        Map<String, JsonObject> pages = new java.util.LinkedHashMap<>();
        pages.put("rbmk/absorber", JsonParser.parseString("""
                {"name": "RBMK Absorber", "icon": ["hbm:rbmk_absorber", 1, 0], "trigger": [["hbm:rbmk_absorber"], "hbm:rbmk_absorber_alt", ["*wild"]],
                 "title": {"en_us": "RBMK \\"Boron\\" Absorber"},
                 "content": {"en_us": "Blocks neutrons.<br>See the [[reflector|RBMK Reflector]], the [[RBMK Reflector|mirror]] and [[nothing here|ghost]]."}}""").getAsJsonObject());
        pages.put("rbmk/reflector", JsonParser.parseString("{\"name\": \"RBMK Reflector\", \"content\": {\"en_us\": \"Bounces them back.\"}, \"icon\": [[\"hbm:rbmk_reflector\"]]}").getAsJsonObject());
        Map<String, String> out = WikiConverter.convert(pages);
        String a = out.get("rbmk/absorber");
        h.assertTrue(a != null && out.size() == 2, "both pages convert");
        h.assertTrue(a.contains("icon: hbm:rbmk_absorber"), "the icon is the item id");
        h.assertTrue(a.contains("items: [hbm:rbmk_absorber, hbm:rbmk_absorber_alt]"), "triggers become bound items, wildcards dropped: " + a);
        h.assertTrue(a.contains("[reflector](wflib_continuum:rbmk/reflector)"), "display|page links to the page");
        h.assertTrue(a.contains("[mirror](wflib_continuum:rbmk/reflector)"), "page|display links to the page");
        h.assertTrue(a.contains("ghost") && !a.contains("[ghost]"), "a link to no page is its text");
        h.assertTrue(a.contains("neutrons.  \n"), "<br> is a hard break");
        h.assertTrue(a.contains("title: \"RBMK \\\"Boron\\\" Absorber\""), "the title is quoted for yaml");
        h.assertTrue(out.get("rbmk/reflector").contains("icon: hbm:rbmk_reflector"), "a nested icon array");
        h.succeed();
    }

    /** All of Continuum's real pages convert, and almost every link in them lands on a page. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty")
    public static void everyContinuumManualPageConverts(GameTestHelper h) throws IOException {
        Map<String, JsonObject> pages = WikiPack.read();
        Map<String, String> out = WikiConverter.convert(pages);
        h.assertTrue(pages.size() > 200, "Continuum's manual was read: " + pages.size());
        h.assertTrue(out.size() >= pages.size() - 5, "nearly every page converts: " + out.size() + " of " + pages.size());
        int links = 0, plain = 0;
        for (String md : out.values()) {
            var m = java.util.regex.Pattern.compile("\\]\\(wflib_continuum:([^)]+)\\)").matcher(md);
            while (m.find()) {
                links++;
                h.assertTrue(out.containsKey(m.group(1)), "a link to a page that is not there: " + m.group(1));
            }
            if (md.contains("[[")) plain++;
        }
        h.assertTrue(links > 100, "links were made: " + links);
        h.assertTrue(plain == 0, plain + " page(s) still carry raw [[ ]] markup");
        h.succeed();
    }
    // ---------------------------------------------------------------- soldiers and guns

    private static net.minecraft.server.level.ServerPlayer armed(GameTestHelper h, SednaWeapon weapon) {
        // a fake player is a ServerPlayer without a connection: the same body a soldier has
        net.minecraft.server.level.ServerPlayer p = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        StringBuilder why = new StringBuilder();
        int n = 0;
        for (ResourceLocation id : weapon.guns()) {
            String arm = weapon.arm(p, id, 2, List.of());
            var ammo = weapon.ammo(p);
            if (ammo != null && ammo.left() > 0 && ammo.spare() > 0) return p;
            if (n++ < 4) why.append(" [").append(id).append(": ").append(arm).append(" -> ").append(ammo).append("]");
        }
        throw new IllegalStateException("no Continuum gun could be armed;" + why);
    }

    private static void tickGun(GameTestHelper h, net.minecraft.server.level.ServerPlayer p) {
        ItemStack gun = p.getMainHandItem();
        gun.getItem().inventoryTick(gun, h.getLevel(), p, p.getInventory().selected, true);
    }

    /** A soldier is issued a Continuum gun, reads its ammunition, and fires it: the magazine goes down. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty", timeoutTicks = 200)
    public static void aSoldierFiresAContinuumGun(GameTestHelper h) {
        SednaWeapon weapon = new SednaWeapon();
        h.assertTrue(weapon.guns().size() > 3, "Continuum has several magazine-fed guns a soldier can use: " + weapon.guns().size());
        net.minecraft.server.level.ServerPlayer p = armed(h, weapon);
        int start = weapon.ammo(p).left();
        h.assertTrue(weapon.ammo(p).capacity() >= start && start > 0, "loaded: " + weapon.ammo(p));
        h.onEachTick(() -> {
            tickGun(h, p);
            if (weapon.ammo(p).left() < start) h.succeed();
            else weapon.fire(p);
        });
    }

    /** An empty gun is reloaded from a spare magazine: the reload starts, the gun is busy, and then it is loaded again. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty", timeoutTicks = 600)
    public static void aSoldierReloadsAContinuumGun(GameTestHelper h) {
        SednaWeapon weapon = new SednaWeapon();
        net.minecraft.server.level.ServerPlayer p = armed(h, weapon);
        ItemStack gun = p.getMainHandItem();
        @SuppressWarnings({"unchecked", "rawtypes"})
        com.hbm.items.weapon.sedna.mags.IMagazine<?> mag = SednaWeapon.magazine((com.hbm.items.weapon.sedna.ItemGunBaseNT) gun.getItem(), gun);
        mag.setAmount(gun, 0);
        h.assertTrue(weapon.ammo(p).left() == 0, "emptied");
        boolean[] started = {false};
        h.onEachTick(() -> {
            tickGun(h, p);
            if (!started[0]) {
                // the gun is drawing for the first ticks; ask until it takes the reload
                if (weapon.reload(p)) started[0] = true;
                return;
            }
            if (!weapon.busy(p) && weapon.ammo(p).left() > 0) h.succeed();
        });
    }
    // ---------------------------------------------------------------- warheads

    private static com.wf.wflib.warhead.api.WarheadCarrier carrier(GameTestHelper h) {
        return new com.wf.wflib.warhead.api.WarheadCarrier() {
            @Override
            public Level level() {
                return h.getLevel();
            }

            @Override
            public int getFragmentCount() {
                return 0;
            }

            @Override
            public Vec3 angle() {
                return new Vec3(0, -1, 0);
            }
        };
    }

    /** Continuum's nuclear warhead on a WFLib carrier raises Continuum's MK5 blast entity at the point, and the high-explosive one blows a crater. */
    @GameTest(templateNamespace = "wflib_continuum", template = "empty", timeoutTicks = 60)
    public static void continuumWarheadsDetonateOnAWflibCarrier(GameTestHelper h) {
        Vec3 at = h.absoluteVec(new Vec3(2.5, 3.0, 2.5));
        net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(at).below(2);
        h.getLevel().setBlock(below, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
        com.wf.wflib.warhead.api.Warheads.get(WarheadBridge.HE).detonate(carrier(h), at);
        h.assertTrue(h.getLevel().getBlockState(below).isAir(), "the high-explosive warhead blew the block under it");
        com.wf.wflib.warhead.api.Warheads.get(WarheadBridge.NUCLEAR_SMALL).detonate(carrier(h), at.add(0, 10, 0));
        var blasts = h.getLevel().getEntitiesOfClass(com.hbm.entity.logic.EntityNukeExplosionMK5.class, new AABB(at.add(-5, 0, -5), at.add(5, 20, 5)));
        h.assertTrue(blasts.size() == 1, "one MK5 blast was raised: " + blasts.size());
        blasts.get(0).discard();
        h.succeed();
    }
}
