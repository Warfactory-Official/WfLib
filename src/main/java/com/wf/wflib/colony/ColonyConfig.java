package com.wf.wflib.colony;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Colony and glyphid tunables; immutable snapshot swapped on load, safe to read from the sim thread. */
public final class ColonyConfig {

    public static final ModConfigSpec SPEC;

    private static final int GARRISON_PER_CHAMBER = 6;

    private static final ModConfigSpec.IntValue COLONY_SAFE_RADIUS;
    private static final ModConfigSpec.IntValue COLONY_FULL_STRENGTH_DISTANCE;
    private static final ModConfigSpec.IntValue COLONY_MAX_TIER;
    private static final ModConfigSpec.DoubleValue COLONY_GROWTH_PER_SECOND;
    private static final ModConfigSpec.IntValue COLONY_POPULATION_CAP;
    private static final ModConfigSpec.DoubleValue COLONY_AGGRESSION_PER_PRESSURE;
    private static final ModConfigSpec.DoubleValue COLONY_STRIKE_THRESHOLD;
    private static final ModConfigSpec.IntValue COLONY_WARBAND_SIZE;
    private static final ModConfigSpec.IntValue COLONY_EXPANSION_POPULATION;
    private static final ModConfigSpec.IntValue COLONY_EXPANSION_COOLDOWN;
    private static final ModConfigSpec.IntValue COLONY_EXPANSION_RANGE;
    private static final ModConfigSpec.DoubleValue COLONY_BUD_CHANCE;
    private static final ModConfigSpec.IntValue COLONY_BUD_POPULATION;
    private static final ModConfigSpec.IntValue COLONY_BUD_COOLDOWN;
    private static final ModConfigSpec.IntValue COLONY_BUD_CAP_BASE;
    private static final ModConfigSpec.IntValue COLONY_BUD_CAP_PER_TIER;
    private static final ModConfigSpec.DoubleValue COLONY_REINFORCED_EVOLUTION;
    private static final ModConfigSpec.IntValue COLONY_TICK_INTERVAL;
    private static final ModConfigSpec.DoubleValue COLONY_WARBAND_SPEED;
    private static final ModConfigSpec.IntValue COLONY_MAX_COLONIES;
    private static final ModConfigSpec.IntValue COLONY_FRONTIER_DISTANCE;
    private static final ModConfigSpec.IntValue COLONY_PROVOCATION_RADIUS;
    private static final ModConfigSpec.IntValue COLONY_CROWDING_RADIUS;
    private static final ModConfigSpec.IntValue COLONY_CROWDING_LIMIT;
    private static final ModConfigSpec.IntValue COLONY_NATURAL_SPACING;
    private static final ModConfigSpec.DoubleValue COLONY_NATURAL_CHANCE;
    private static final ModConfigSpec.IntValue COLONY_MATERIALISE_RANGE;
    private static final ModConfigSpec.IntValue COLONY_MATERIALISE_PER_TICK;
    private static final ModConfigSpec.DoubleValue COLONY_FLYING_CHANCE_PER_TIER;
    private static final ModConfigSpec.DoubleValue COLONY_FLYING_SPEED_FACTOR;
    private static final ModConfigSpec.DoubleValue COLONY_EVOLUTION_TIME_FACTOR;
    private static final ModConfigSpec.DoubleValue COLONY_EVOLUTION_PRESSURE_FACTOR;
    private static final ModConfigSpec.DoubleValue COLONY_EVOLUTION_STRENGTH_BONUS;
    private static final ModConfigSpec.DoubleValue GLYPHID_PACE;
    private static final ModConfigSpec.BooleanValue GLYPHID_EXTENDED_TARGETING;
    private static final ModConfigSpec.BooleanValue GLYPHID_DIG;
    private static final ModConfigSpec.BooleanValue GLYPHID_DIG_OVERLAY;
    private static final ModConfigSpec.BooleanValue GLYPHID_WAYPOINT_DEBUG;
    private static final ModConfigSpec.BooleanValue GLYPHID_BOMBING;
    private static final ModConfigSpec.IntValue GLYPHID_BOMB_LOAD;
    private static final ModConfigSpec.IntValue GLYPHID_BOMB_INTERVAL;
    private static final ModConfigSpec.DoubleValue GLYPHID_ACID_DAMAGE;
    private static final ModConfigSpec.DoubleValue GLYPHID_ACID_RADIUS;
    private static final ModConfigSpec.BooleanValue GLYPHID_ACID_CORRODES;
    private static final ModConfigSpec.IntValue GLYPHID_BOMB_BLAST;

    private record Values(int safeRadius,
                         int fullStrengthDistance,
                         int maxTier,
                         double growthPerSecond,
                         int basePopulationCap,
                         double aggressionPerPressure,
                         double strikeThreshold,
                         int baseWarbandSize,
                         int expansionPopulation,
                         int expansionCooldownTicks,
                         int expansionRange,
                         double budChance,
                         int budPopulation,
                         int budCooldownTicks,
                         int budCapBase,
                         int budCapPerTier,
                         double reinforcedEvolution,
                         int colonyTickInterval,
                         double warbandSpeed,
                         int maxColonies,
                         int frontierDistance,
                         int provocationRadius,
                         int crowdingRadius,
                         int crowdingLimit,
                         int naturalSpacing,
                         double naturalChance,
                         int materialiseRange,
                         int materialisePerTick,
                         double flyingChancePerTier,
                         double flyingSpeedFactor,
                         double evolutionTimeFactor,
                         double evolutionPressureFactor,
                         double evolutionStrengthBonus,
                         double glyphidPace,
                         boolean extendedTargeting,
                         boolean digging,
                         boolean diggingOverlay,
                         boolean waypointDebug,
                         boolean bombing,
                         int bombLoad,
                         int bombIntervalTicks,
                         double acidDamage,
                         double acidRadius,
                         boolean acidCorrodesBlocks,
                         int bombBlastRadius) {
    }

    private static volatile Values values = new Values(512, 12_000, 4, 0.35, 20, 0.02, 100.0, 8, 25, 12_000, 900, 0.55, 12, 4_800, 1, 1, 0.35, 20, 0.35, 250, 24_000, 1_500, 1_800, 4, 512, 0.7, 128, 4, 0.2, 3.0, 4.0E-5, 2.0E-8, 0.5, 1.25, false, true, true, false, true, 4, 30, 3.0, 3.0, true, 3);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("On/off: wflib-systems.toml, key 'colony'.");
        b.comment("Colony simulation: nests grow, take offence and march whether or not chunks are loaded.",
                        "Strength scales with distance from world spawn, so the frontier is the dangerous part.")
                .push("colonies");
        COLONY_SAFE_RADIUS = b
                .comment("No colony may exist within this many blocks of world spawn.")
                .defineInRange("safeRadius", 512, 0, 1_000_000);
        COLONY_FULL_STRENGTH_DISTANCE = b
                .comment("Distance from spawn at which colonies reach the maximum tier. Strength ramps",
                        "linearly between the safe radius and here.")
                .defineInRange("fullStrengthDistance", 12_000, 1, 30_000_000);
        COLONY_MAX_TIER = b
                .comment("Highest colony tier. Each tier multiplies population cap, growth and warband size.")
                .defineInRange("maxTier", 4, 0, 32);
        COLONY_GROWTH_PER_SECOND = b
                .comment("Population a tier-0 colony gains per second.")
                .defineInRange("growthPerSecond", 0.35, 0.0, 1000.0);
        COLONY_POPULATION_CAP = b
                .comment("Population cap at tier 0; each tier adds another multiple of this.")
                .defineInRange("populationCap", 20, 1, 100_000);
        COLONY_AGGRESSION_PER_PRESSURE = b
                .comment("Aggression gained per point of nearby industry provocation, per second.",
                        "This is the link between what the player runs and when they get attacked.")
                .defineInRange("aggressionPerPressure", 0.02, 0.0, 1000.0);
        COLONY_STRIKE_THRESHOLD = b
                .comment("Aggression at which a colony with the numbers to spare sends a warband.")
                .defineInRange("strikeThreshold", 100.0, 1.0, 1_000_000.0);
        COLONY_WARBAND_SIZE = b
                .comment("Warband size at tier 0; each tier adds another multiple.")
                .defineInRange("warbandSize", 8, 1, 10_000);
        COLONY_EXPANSION_POPULATION = b
                .comment("Population a colony must hold before it can afford to found another.")
                .defineInRange("expansionPopulation", 25, 1, 100_000);
        COLONY_EXPANSION_COOLDOWN = b
                .comment("Ticks before a colony may expand again (12000 = 10 minutes).")
                .defineInRange("expansionCooldownTicks", 12_000, 1, 10_000_000);
        COLONY_EXPANSION_RANGE = b
                .comment("How far from its parent a new colony is founded, outward from spawn.")
                .defineInRange("expansionRange", 900, 16, 100_000);
        COLONY_BUD_CHANCE = b
                .comment("Expansion comes in two kinds. An outpost is a whole new colony expansionRange blocks",
                        "further out; a bud is another mound welded onto this colony's own cluster, on a hex",
                        "lattice, sharing its population and its anger. This is the share that buds when the",
                        "colony could afford either. It is not a hard split: an outpost with nowhere to go",
                        "falls back to budding, so a crowded region thickens instead of stalling.")
                .defineInRange("budChance", 0.55, 0.0, 1.0);
        COLONY_BUD_POPULATION = b
                .comment("Population a colony must hold to bud. Cheaper than expansionPopulation because a bud",
                        "is the same colony growing rather than a second one starting from nothing.")
                .defineInRange("budPopulation", 12, 1, 100_000);
        COLONY_BUD_COOLDOWN = b
                .comment("Ticks before a colony may expand again after budding (4800 = 4 minutes). Shorter than",
                        "the outpost cooldown, so a cluster fills in faster than the frontier moves.")
                .defineInRange("budCooldownTicks", 4_800, 1, 10_000_000);
        COLONY_BUD_CAP_BASE = b
                .comment("Mounds a tier-0 colony may bud, beyond the one it starts as.")
                .defineInRange("budCapBase", 1, 0, 1_000);
        COLONY_BUD_CAP_PER_TIER = b
                .comment("How many more each tier adds. At the defaults a tier-0 colony tops out at two mounds",
                        "and a tier-4 one at six, so how sprawling a hive is says how far from spawn it is.")
                .defineInRange("budCapPerTier", 1, 0, 1_000);
        COLONY_REINFORCED_EVOLUTION = b
                .comment("Evolution at which colonies start laying reinforced flesh: the same nest block with",
                        "a brown cast, 60x the blast resistance and a hardness that wants a pickaxe. Above this",
                        "the hardened crust deepens with evolution until a fully evolved world builds mounds",
                        "reinforced all the way through. Baked in at the moment each mound is laid, so an old",
                        "hive keeps its soft shell and only the cells it grows later come up brown.")
                .defineInRange("reinforcedEvolution", 0.35, 0.0, 1.0);
        COLONY_TICK_INTERVAL = b
                .comment("Ticks between colony updates. Colonies are staggered across this window, so the",
                        "per-tick cost is the colony count divided by this.")
                .defineInRange("tickIntervalTicks", 20, 1, 1200);
        COLONY_WARBAND_SPEED = b
                .comment("Blocks per tick a travelling warband covers while off-world.")
                .defineInRange("warbandSpeed", 0.35, 0.01, 64.0);
        COLONY_MAX_COLONIES = b
                .comment("Hard ceiling on colonies per dimension. Expansion is exponential (every colony",
                        "founded can found more), so it needs a stop, not just a cooldown.")
                .defineInRange("maxColonies", 250, 0, 100_000);
        COLONY_FRONTIER_DISTANCE = b
                .comment("No colony may be founded beyond this distance from spawn.")
                .defineInRange("frontierDistance", 24_000, 1, 30_000_000);
        COLONY_PROVOCATION_RADIUS = b
                .comment("How far a colony can smell industry. Independent of the industry cell size on",
                        "purpose, so tuning cells for performance does not silently change gameplay.")
                .defineInRange("provocationRadius", 1_500, 16, 100_000);
        COLONY_CROWDING_RADIUS = b
                .comment("Radius the crowding limit below is measured over.")
                .defineInRange("crowdingRadius", 1_800, 16, 100_000);
        COLONY_CROWDING_LIMIT = b
                .comment("Colonies already within the crowding radius of a proposed site that will block it.",
                        "This is what makes expansion fill in territory instead of fleeing outward forever.")
                .defineInRange("crowdingLimit", 4, 1, 1_000);
        COLONY_NATURAL_SPACING = b
                .comment("Side of the grid cell natural colonies are seeded on, in blocks. One cell holds at",
                        "most one nest, at a position drawn from the world seed, so placement is reproducible",
                        "and does not depend on which chunk a player walks into first. This is the setting that",
                        "decides how far apart hives are; 0 turns natural seeding off entirely and leaves",
                        "colonies to expansion and the 'colony found' command.")
                .defineInRange("naturalSpacing", 512, 0, 100_000);
        COLONY_NATURAL_CHANCE = b
                .comment("Chance that a cell has a nest at all. Below 1 the frontier is patchy rather than a",
                        "lattice, which is what stops the spacing from reading as a grid on a map.")
                .defineInRange("naturalChance", 0.7, 0.0, 1.0);
        COLONY_MATERIALISE_RANGE = b
                .comment("How close a player has to be before a warband stops being one record and becomes",
                        "glyphids. Nothing is ever placed in an unloaded chunk regardless of this.")
                .defineInRange("materialiseRange", 128, 16, 100_000);
        COLONY_MATERIALISE_PER_TICK = b
                .comment("Glyphids placed per dimension per tick. A large warband streams in over several",
                        "ticks rather than spawning hundreds of entities inside one. 0 disables spawning.")
                .defineInRange("materialisePerTick", 4, 0, 1_000);
        COLONY_FLYING_CHANCE_PER_TIER = b
                .comment("Chance per colony tier that a warband flies instead of walking. At the default a",
                        "tier-0 nest never fields wings and a tier-4 one usually does, so flight is something",
                        "the frontier has. Flying glyphids do no pathfinding, so they are also cheaper.")
                .defineInRange("flyingChancePerTier", 0.2, 0.0, 1.0);
        COLONY_FLYING_SPEED_FACTOR = b
                .comment("How much faster a flying warband crosses the map than a walking one.")
                .defineInRange("flyingSpeedFactor", 3.0, 1.0, 64.0);
        COLONY_EVOLUTION_TIME_FACTOR = b
                .comment("Share of the remaining gap to full evolution closed every 200 ticks by time alone.",
                        "Evolution gates which castes a colony may field and adds a little to how fast it",
                        "grows. At the default an untouched world is roughly half evolved after fifty hours.",
                        "0 pins evolution to industry alone.")
                .defineInRange("evolutionTimeFactor", 4.0E-5, 0.0, 1.0);
        COLONY_EVOLUTION_PRESSURE_FACTOR = b
                .comment("The same, per point of standing industry across the world. This is the term that",
                        "makes evolution a consequence of what was built rather than of how long the game",
                        "was left running. 0 makes evolution purely a matter of time.")
                .defineInRange("evolutionPressureFactor", 2.0E-8, 0.0, 1.0);
        COLONY_EVOLUTION_STRENGTH_BONUS = b
                .comment("How much a fully evolved world adds to colony growth and warband size. At the",
                        "default a late colony musters half again what an early one of the same tier does.")
                .defineInRange("evolutionStrengthBonus", 0.5, 0.0, 16.0);
        b.pop();

        b.comment("Glyphid swarm behaviour.").push("glyphids");
        GLYPHID_PACE = b
                .comment("Multiplier on every caste's movement speed. The caste table is relative (a behemoth",
                        "is 0.8 of a grunt, a scout 1.5), and this sets the swarm's absolute pace without",
                        "disturbing those ratios. A glyphid that cannot keep up with what it is chasing is",
                        "scenery, so this is the lever for how far a player can outrun one.")
                .defineInRange("pace", 1.25, 0.05, 8.0);
        GLYPHID_EXTENDED_TARGETING = b
                .comment("Glyphids hunt players across 128 blocks instead of only the 16 around them.",
                        "This is what turns a nest into a base attack, and it is the single biggest lever on",
                        "how much work a swarm does per tick.")
                .define("extendedTargeting", false);
        GLYPHID_DIG = b
                .comment("Glyphids chew through terrain and buildings to reach a waypoint. With this off they",
                        "path around obstacles instead, which is cheaper but lets walls stop them entirely.")
                .define("digging", true);
        GLYPHID_DIG_OVERLAY = b
                .comment("Show the block-breaking cracks on whatever a glyphid is chewing. Purely visual, but",
                        "it is a packet per crack stage to every player in range, so a swarm eating a wall in",
                        "front of a crowded base is where it would cost anything.")
                .define("diggingOverlay", true);
        GLYPHID_WAYPOINT_DEBUG = b
                .comment("Render colony waypoints as coloured dust particles (client-side).")
                .define("waypointDebug", false);
        GLYPHID_BOMBING = b
                .comment("Flying glyphids drop acid on what they were sent to attack. This is what makes wings",
                        "a threat to a base rather than just a faster way of arriving at one.")
                .define("bombing", true);
        GLYPHID_BOMB_LOAD = b
                .comment("Bombs each flying glyphid carries. Finite, so a raid is something a base survives and",
                        "rebuilds from rather than a bug parked overhead dissolving it forever.")
                .defineInRange("bombLoad", 4, 0, 512);
        GLYPHID_BOMB_INTERVAL = b
                .comment("Ticks between bomb releases from one glyphid.")
                .defineInRange("bombIntervalTicks", 30, 1, 1200);
        GLYPHID_ACID_DAMAGE = b
                .comment("Damage per second to anything standing in a fresh acid pool. Tapers to nothing as",
                        "the pool dries out.")
                .defineInRange("acidDamage", 3.0, 0.0, 1000.0);
        GLYPHID_ACID_RADIUS = b
                .comment("Horizontal radius of the acid pool a bomb leaves.")
                .defineInRange("acidRadius", 3.0, 0.5, 32.0);
        GLYPHID_ACID_CORRODES = b
                .comment("Acid pools dissolve the blocks under them. Obsidian-grade blast resistance holds;",
                        "most machine casings and building blocks do not.")
                .define("acidCorrodesBlocks", true);
        GLYPHID_BOMB_BLAST = b
                .comment("Blast radius for the explosive payload, used by castes that drop it instead of acid.")
                .defineInRange("bombBlastRadius", 3, 1, 64);
        b.pop();

        SPEC = b.build();
    }

    private ColonyConfig() {
    }

    /**
     * No colony may exist within this many blocks of world spawn: the starting area stays quiet, which is what
     * makes moving outward a decision rather than a formality.
     */
    public static int safeRadius() {
        return values.safeRadius();
    }

    /** Distance from spawn at which colonies reach {@link #maxTier()}. */
    public static int fullStrengthDistance() {
        return values.fullStrengthDistance();
    }

    public static int maxTier() {
        return values.maxTier();
    }

    /** Population a colony adds per second at tier 0, before the tier multiplier. */
    public static double growthPerSecond() {
        return values.growthPerSecond();
    }

    /** Population cap at tier 0. Each tier adds another multiple of this. */
    public static int basePopulationCap() {
        return values.basePopulationCap();
    }

    /** Aggression gained per point of nearby industry provocation, per second. */
    public static double aggressionPerPressure() {
        return values.aggressionPerPressure();
    }

    /** Aggression at which a colony with the numbers to spare sends a warband. */
    public static double strikeThreshold() {
        return values.strikeThreshold();
    }

    /** Warband size at tier 0; each tier adds another multiple. */
    public static int baseWarbandSize() {
        return values.baseWarbandSize();
    }

    /** Population a colony must hold before it can afford to found another. */
    public static int expansionPopulation() {
        return values.expansionPopulation();
    }

    public static int expansionCooldownTicks() {
        return values.expansionCooldownTicks();
    }

    /** How far a new colony is founded from its parent. */
    public static int expansionRange() {
        return values.expansionRange();
    }

    /**
     * Share of expansions that grow the colony in place rather than found one further out, when it can afford
     * either.
     */
    public static double budChance() {
        return values.budChance();
    }

    /** Population a colony must hold to bud. Cheaper than {@link #expansionPopulation()}: same colony. */
    public static int budPopulation() {
        return values.budPopulation();
    }

    public static int budCooldownTicks() {
        return values.budCooldownTicks();
    }

    /** Mounds a tier-0 colony may bud, beyond the one it starts as. */
    public static int budCapBase() {
        return values.budCapBase();
    }

    /** How many more each tier adds. At the defaults a tier-0 colony reaches two mounds and a tier-4 one six. */
    public static int budCapPerTier() {
        return values.budCapPerTier();
    }

    /** Evolution at which colonies start laying reinforced flesh. */
    public static double reinforcedEvolution() {
        return values.reinforcedEvolution();
    }

    /** Ticks between colony updates. Colonies are staggered across the window, so cost is count/interval. */
    public static int colonyTickInterval() {
        return values.colonyTickInterval();
    }

    /** Blocks per tick a travelling warband covers. */
    public static double warbandSpeed() {
        return values.warbandSpeed();
    }

    /** Hard ceiling per level. Expansion is exponential, so it needs a stop and not just a cooldown. */
    public static int maxColonies() {
        return values.maxColonies();
    }

    /** No colony beyond this from spawn, or an expanding lineage walks to the world border and keeps going. */
    public static int frontierDistance() {
        return values.frontierDistance();
    }

    /** How far a colony can smell industry. */
    public static int provocationRadius() {
        return values.provocationRadius();
    }

    /**
     * Radius within which {@link #crowdingLimit()} applies.
     */
    public static int crowdingRadius() {
        return values.crowdingRadius();
    }

    /** Colonies within {@link #crowdingRadius()} that block a site, so expansion fills in rather than flees. */
    public static int crowdingLimit() {
        return values.crowdingLimit();
    }

    /** Defenders one egg chamber keeps standing, so a nest with its chambers dug out holds none. */
    public static int garrisonPerChamber() {
        return GARRISON_PER_CHAMBER;
    }

    /** Side of the grid cell {@link ColonySeeder} places natural nests on, and so how far apart hives are. */
    public static int naturalSpacing() {
        return values.naturalSpacing();
    }

    /** Chance a cell has a nest at all, so the frontier is patchy rather than a lattice. */
    public static double naturalChance() {
        return values.naturalChance();
    }

    /** How close a player has to be before a warband stops being a record and becomes glyphids. */
    public static int materialiseRange() {
        return values.materialiseRange();
    }

    /** Bodies placed per level tick, so a large warband streams in rather than spawning inside one tick. */
    public static int materialisePerTick() {
        return values.materialisePerTick();
    }

    /**
     * Chance per tier that a warband is on the wing, so wings are a frontier thing: at the default a tier-0 nest
     * never fields them and a tier-4 one usually does.
     */
    public static double flyingChance(int tier) {
        return Math.min(1.0, values.flyingChancePerTier() * tier);
    }

    /** How much faster a flying warband crosses the map than a walking one. */
    public static double flyingSpeedFactor() {
        return values.flyingSpeedFactor();
    }

    /** Share of the remaining gap closed per {@link Evolution#INTERVAL} by time alone. */
    public static double evolutionTimeFactor() {
        return values.evolutionTimeFactor();
    }

    /** The same, per point of standing industry: the term that ties evolution to what was built. */
    public static double evolutionPressureFactor() {
        return values.evolutionPressureFactor();
    }

    /** What a fully evolved world adds to growth and warband size, as a fraction of the base. */
    public static double evolutionStrengthBonus() {
        return values.evolutionStrengthBonus();
    }


    /** Multiplier every caste's speed is scaled by, on top of the stock movement scale. */
    public static double glyphidPace() {
        return values.glyphidPace();
    }

    public static boolean extendedTargeting() {
        return values.extendedTargeting();
    }

    public static boolean digging() {
        return values.digging();
    }

    public static boolean diggingOverlay() {
        return values.diggingOverlay();
    }

    public static boolean waypointDebug() {
        return values.waypointDebug();
    }

    public static boolean bombing() {
        return values.bombing();
    }

    public static int bombLoad() {
        return values.bombLoad();
    }

    public static int bombIntervalTicks() {
        return values.bombIntervalTicks();
    }

    public static double acidDamage() {
        return values.acidDamage();
    }

    public static double acidRadius() {
        return values.acidRadius();
    }

    public static boolean acidCorrodesBlocks() {
        return values.acidCorrodesBlocks();
    }

    public static int bombBlastRadius() {
        return values.bombBlastRadius();
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        int safe = Math.max(0, COLONY_SAFE_RADIUS.get());
        values = new Values(
                safe, Math.max(safe + 1, COLONY_FULL_STRENGTH_DISTANCE.get()), Math.max(0, COLONY_MAX_TIER.get()),
                Math.max(0.0, COLONY_GROWTH_PER_SECOND.get()), Math.max(1, COLONY_POPULATION_CAP.get()),
                Math.max(0.0, COLONY_AGGRESSION_PER_PRESSURE.get()), Math.max(1.0, COLONY_STRIKE_THRESHOLD.get()),
                Math.max(1, COLONY_WARBAND_SIZE.get()),
                Math.max(1, COLONY_EXPANSION_POPULATION.get()), Math.max(1, COLONY_EXPANSION_COOLDOWN.get()),
                Math.max(16, COLONY_EXPANSION_RANGE.get()),
                clamp01(COLONY_BUD_CHANCE.get()), Math.max(1, COLONY_BUD_POPULATION.get()),
                Math.max(1, COLONY_BUD_COOLDOWN.get()), Math.max(0, COLONY_BUD_CAP_BASE.get()),
                Math.max(0, COLONY_BUD_CAP_PER_TIER.get()), clamp01(COLONY_REINFORCED_EVOLUTION.get()),
                Math.max(1, COLONY_TICK_INTERVAL.get()), Math.max(0.01, COLONY_WARBAND_SPEED.get()),
                Math.max(0, COLONY_MAX_COLONIES.get()), Math.max(safe + 1, COLONY_FRONTIER_DISTANCE.get()),
                Math.max(16, COLONY_PROVOCATION_RADIUS.get()), Math.max(16, COLONY_CROWDING_RADIUS.get()),
                Math.max(1, COLONY_CROWDING_LIMIT.get()),
                Math.max(0, COLONY_NATURAL_SPACING.get()), clamp01(COLONY_NATURAL_CHANCE.get()),
                Math.max(16, COLONY_MATERIALISE_RANGE.get()), Math.max(0, COLONY_MATERIALISE_PER_TICK.get()),
                Math.max(0.0, COLONY_FLYING_CHANCE_PER_TIER.get()), Math.max(1.0, COLONY_FLYING_SPEED_FACTOR.get()),
                Math.max(0.0, COLONY_EVOLUTION_TIME_FACTOR.get()), Math.max(0.0, COLONY_EVOLUTION_PRESSURE_FACTOR.get()),
                Math.max(0.0, COLONY_EVOLUTION_STRENGTH_BONUS.get()),
                Math.max(0.05, GLYPHID_PACE.get()), GLYPHID_EXTENDED_TARGETING.get(), GLYPHID_DIG.get(),
                GLYPHID_DIG_OVERLAY.get(), GLYPHID_WAYPOINT_DEBUG.get(), GLYPHID_BOMBING.get(),
                GLYPHID_BOMB_LOAD.get(), GLYPHID_BOMB_INTERVAL.get(), GLYPHID_ACID_DAMAGE.get(),
                GLYPHID_ACID_RADIUS.get(), GLYPHID_ACID_CORRODES.get(), GLYPHID_BOMB_BLAST.get());
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
