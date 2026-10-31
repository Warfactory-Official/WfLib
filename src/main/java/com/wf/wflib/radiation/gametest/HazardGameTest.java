package com.wf.wflib.radiation.gametest;

import com.wf.wflib.hazard.api.ContaminationEffect;
import com.wf.wflib.hazard.api.HazardContent;
import com.wf.wflib.hazard.api.HazardEntry;
import com.wf.wflib.hazard.api.Hazards;
import com.wf.wflib.hazard.api.HazardRules;
import com.wf.wflib.hazard.api.LivingHazardProps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import com.wf.wflib.core.api.WFLib;

/**
 * NTM NEXT Radiation / HazardEffect / DigammaHealth / ContaminationDose / HazardResolution / MobHazard server
 * tests. No world field: arena-local; spawned mobs discarded. wflib ships no hazard rows => resolution rows
 * register their own and remove them. AreaRadiation rows: {@code RadiationFieldGameTest} lanes.
 */
@PrefixGameTestTemplate(false)
public class HazardGameTest {

    private static final String TEMPLATE = "empty";
    /** Not defaultBatch: extra rows reshuffle its grid, and several lane rows there are packing-sensitive. */
    static final String BATCH = "wflib_hazard";
    private static final BlockPos FLOOR = new BlockPos(1, 1, 1);
    private static final float EPSILON = 0.01F;

    private static Pig pig(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        return helper.spawn(EntityType.PIG, 1, 2, 1);
    }

    /** Leftover mobs => despawn checks, targeting and packing for later batches. */
    private static void done(GameTestHelper helper, Entity... spawned) {
        for (Entity e : spawned) e.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ radiation sickness

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void radiationKills(GameTestHelper helper) {
        Pig pig = pig(helper);
        LivingHazardProps.setRadiation(pig, 1000D);
        helper.succeedWhen(() -> {
            helper.assertTrue(!pig.isAlive(), "1000 rads must kill the entity");
            pig.discard();
        });
    }

    /** NTM also zeroes the field under the pig each tick; the arena grid carries no field here. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void radiationSublethalSpares(GameTestHelper helper) {
        Pig pig = pig(helper);
        helper.runAfterDelay(2, () -> {
            LivingHazardProps.setRadiation(pig, 500D);
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(pig.isAlive(), "500 rads applies only non-damaging effects, so the entity must survive;"
                        + " its dose is " + LivingHazardProps.getRadiation(pig));
                done(helper, pig);
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void radiationTransformsCow(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        Cow cow = helper.spawn(EntityType.COW, 1, 2, 1);
        LivingHazardProps.setRadiation(cow, 50D);
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.isRemoved(), "a cow at 50 rads should transform (be discarded)");
            List<MushroomCow> mooshrooms = helper.getLevel()
                    .getEntitiesOfClass(MushroomCow.class, cow.getBoundingBox().inflate(2D));
            helper.assertTrue(!mooshrooms.isEmpty(), "the cow should be replaced by a mooshroom");
            mooshrooms.forEach(Entity::discard);
        });
    }

    // ------------------------------------------------------------------ hazard effects

    /** Zombie: rad-immune, so the field pass rejects it before it could mint a record. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void hazardEffectUntouchedEntityCarriesNoProps(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        Zombie clean = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        Pig dosed = helper.spawn(EntityType.PIG, 1, 2, 1);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(LivingHazardProps.peek(clean) == null,
                    "an entity that has met no hazard must carry no props record after ticking");
            LivingHazardProps.incrementRadiation(dosed, 5D);
            LivingHazardProps props = LivingHazardProps.peek(dosed);
            helper.assertTrue(props != null, "a dosed entity must carry a props record");
            helper.assertTrue(!props.isDefault(), "a dosed entity's record must not read as default");
            done(helper, clean, dosed);
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void hazardEffectAsbestosKills(GameTestHelper helper) {
        Pig pig = pig(helper);
        LivingHazardProps.setAsbestos(pig, LivingHazardProps.maxAsbestos);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!pig.isAlive(), "asbestos at the dose ceiling must kill the entity");
            done(helper, pig);
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void hazardEffectBlacklungKills(GameTestHelper helper) {
        Pig pig = pig(helper);
        LivingHazardProps.setBlackLung(pig, LivingHazardProps.maxBlacklung);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!pig.isAlive(), "black-lung at the dose ceiling must kill the entity");
            done(helper, pig);
        });
    }

    // ------------------------------------------------------------------ digamma

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void digammaHalvesMaxHealth(GameTestHelper helper) {
        Pig pig = pig(helper);
        float baseMax = pig.getMaxHealth();
        LivingHazardProps.setDigamma(pig, 1D);
        helper.runAfterDelay(3, () -> {
            float expected = baseMax * 0.5F;
            helper.assertTrue(Math.abs(pig.getMaxHealth() - expected) < EPSILON,
                    "1 DRX must halve max health (expected ~" + expected + ", got " + pig.getMaxHealth() + ")");
            done(helper, pig);
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void digammaZeroRestoresMaxHealth(GameTestHelper helper) {
        Pig pig = pig(helper);
        float baseMax = pig.getMaxHealth();
        LivingHazardProps.setDigamma(pig, 1D);
        LivingHazardProps.setDigamma(pig, 0D);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(Math.abs(pig.getMaxHealth() - baseMax) < EPSILON,
                    "digamma 0 must restore full max health (expected ~" + baseMax + ", got " + pig.getMaxHealth() + ")");
            done(helper, pig);
        });
    }

    /** Also NTM hazard_effect_digamma_kills: same body. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void digammaTenKills(GameTestHelper helper) {
        Pig pig = pig(helper);
        LivingHazardProps.setDigamma(pig, 10D);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!pig.isAlive(), "digamma 10 must kill the entity");
            done(helper, pig);
        });
    }

    // ------------------------------------------------------------------ contamination

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void contaminationRaisesRadiation(GameTestHelper helper) {
        Pig pig = pig(helper);
        LivingHazardProps.addCont(pig, new ContaminationEffect(20D, 100, false));
        helper.succeedWhen(() -> {
            double rad = LivingHazardProps.getRadiation(pig);
            helper.assertTrue(rad > 0D, "contamination must raise accumulated radiation, got " + rad);
            pig.discard();
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void contaminationEffectIsConsumedAfterMaxTime(GameTestHelper helper) {
        Pig pig = pig(helper);
        LivingHazardProps.addCont(pig, new ContaminationEffect(1D, 5, false));
        helper.succeedWhen(() -> {
            helper.assertTrue(LivingHazardProps.getCont(pig).isEmpty(),
                    "ContaminationEffect must be removed once time reaches zero");
            pig.discard();
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void contaminationGetRadScalesLinearlyWithRemainingTime(GameTestHelper helper) {
        ContaminationEffect e = new ContaminationEffect(10D, 100, false);
        helper.assertTrue(Math.abs(e.getRad() - 10D) < 0.001D, "getRad at full time must equal maxRad (10), got " + e.getRad());
        e.time = 50;
        helper.assertTrue(Math.abs(e.getRad() - 5D) < 0.001D, "getRad at half time must equal maxRad/2 (5), got " + e.getRad());
        e.time = 1;
        helper.assertTrue(Math.abs(e.getRad() - 0.1D) < 0.001D,
                "getRad at time=1 must equal maxRad/maxTime (0.1), got " + e.getRad());
        helper.succeed();
    }

    // ------------------------------------------------------------------ resolution

    private static final Item ITEM_ROW = Items.NAUTILUS_SHELL;
    private static final TagKey<Item> TAG_ROW = ItemTags.CREEPER_DROP_MUSIC_DISCS;
    private static final Item TAG_MEMBER = Items.MUSIC_DISC_CAT;
    private static final Item BLACKLIST_ROW = Items.HEART_OF_THE_SEA;

    /** Code registration + cache drop; the drop lands at the next server tick's scan dispatch. */
    private static void afterCacheDrop(GameTestHelper helper, Runnable body, Runnable cleanup) {
        HazardRules.clearCaches();
        helper.runAfterDelay(2, () -> {
            try {
                body.run();
            } finally {
                cleanup.run();
                HazardRules.clearCaches();
            }
            helper.succeed();
        });
    }

    /** NTM gunpowder/TNT rows -> one code-registered item row at TNT's EXPLOSIVE 4. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void hazardResolutionItemTierRegistrationResolves(GameTestHelper helper) {
        HazardRules.register(ITEM_ROW, Hazards.makeData(Hazards.EXPLOSIVE, 4D));
        afterCacheDrop(helper, () -> {
            helper.assertTrue(HazardRules.itemData().containsKey(ITEM_ROW), "the row must be present in the item table");
            HazardEntry found = null;
            for (HazardEntry e : HazardRules.getHazardsFromStack(new ItemStack(ITEM_ROW))) {
                if (e.type == Hazards.EXPLOSIVE) found = e;
            }
            helper.assertTrue(found != null, "item-tier registration must yield an EXPLOSIVE entry at resolution");
            helper.assertTrue(found.baseLevel == 4D, "EXPLOSIVE level must be 4, got " + found.baseLevel);
        }, () -> HazardRules.unregister(ITEM_ROW));
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void hazardResolutionTagTierRegistrationResolves(GameTestHelper helper) {
        HazardRules.register(TAG_ROW, Hazards.makeData(Hazards.EXPLOSIVE, 2D));
        afterCacheDrop(helper, () -> {
            helper.assertTrue(HazardRules.tagData().containsKey(TAG_ROW), "the tag table must hold the tag-tier registration");
            helper.assertTrue(HazardRules.getHazardLevelFromStack(new ItemStack(TAG_MEMBER), Hazards.EXPLOSIVE) == 2D,
                    "a tag member must resolve the tag's row");
        }, () -> HazardRules.unregister(TAG_ROW));
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void hazardResolutionBlacklistedItemResolvesToEmpty(GameTestHelper helper) {
        HazardRules.register(BLACKLIST_ROW, Hazards.makeData(Hazards.EXPLOSIVE, 1D));
        HazardRules.blacklist(BLACKLIST_ROW);
        afterCacheDrop(helper, () -> {
            List<HazardEntry> entries = HazardRules.getHazardsFromStack(new ItemStack(BLACKLIST_ROW));
            helper.assertTrue(entries.isEmpty(), "a blacklisted item must resolve to empty, got " + entries.size() + " entries");
            helper.assertTrue(!HazardRules.isStackHazardous(new ItemStack(BLACKLIST_ROW)),
                    "isStackHazardous must be false for a blacklisted item");
        }, () -> {
            HazardRules.unblacklist(BLACKLIST_ROW);
            HazardRules.unregister(BLACKLIST_ROW);
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void hazardResolutionHazRadiationComponentSurfaces(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(HazardContent.HAZ_RADIATION.get(), 42.5F);
        double rads = HazardRules.getRawRadsFromStack(stack);
        helper.assertTrue(Math.abs(rads - 42.5D) < 0.001D, "wflib:haz_radiation must surface as ~42.5 rad/s, got " + rads);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void hazardResolutionNonHazardousItemIsClean(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STICK);
        List<HazardEntry> entries = HazardRules.getHazardsFromStack(stack);
        helper.assertTrue(entries.isEmpty(), "a plain stick must resolve to empty, got " + entries.size() + " entries");
        helper.assertTrue(!HazardRules.isStackHazardous(stack), "isStackHazardous must be false for a plain stick");
        helper.succeed();
    }

    // ------------------------------------------------------------------ mob equipment

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void mobHazardEquipmentRadiates(GameTestHelper helper) {
        Pig pig = pig(helper);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        helmet.set(HazardContent.HAZ_RADIATION.get(), 10F);
        pig.setItemSlot(EquipmentSlot.HEAD, helmet);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.isAlive(), "the pig must survive the sub-lethal equipment dose");
            helper.assertTrue(LivingHazardProps.getRadiation(pig) > 0D,
                    "a radioactive worn item must raise the mob's radiation above 0");
            pig.discard();
        });
    }

    // ------------------------------------------------------------------ containers

    private static ItemStack hot(int count) {
        ItemStack stack = new ItemStack(Items.STICK, count);
        stack.set(HazardContent.HAZ_RADIATION.get(), 2F);
        return stack;
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void containerSumsHeldRadiation(GameTestHelper helper) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(hot(3), ItemStack.EMPTY, hot(1))));
        helper.assertTrue(Math.abs(HazardRules.getHazardLevelFromStack(box, Hazards.RADIATION) - 8D) < EPSILON,
                "shulker box must carry 2 RAD x 4 sticks");
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(box)));
        helper.assertTrue(Math.abs(HazardRules.getHazardLevelFromStack(bundle, Hazards.RADIATION) - 8D) < EPSILON,
                "bundle must carry its nested box");
        helper.assertTrue(!HazardRules.isStackHazardous(new ItemStack(Items.SHULKER_BOX)),
                "empty box must carry nothing");
        helper.succeed();
    }
}
