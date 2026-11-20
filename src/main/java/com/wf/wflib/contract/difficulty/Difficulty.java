package com.wf.wflib.contract.difficulty;

import com.wf.wflib.contract.profile.ProfileBook;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * The world's difficulty settings: the config's defaults, with any this world overrode ({@code /contract difficulty}), saved with the
 * world. {@link #tier} is what a contract or a soldier squad is built to.
 */
public final class Difficulty extends SavedData {
    private String mode = "";
    private double start = Double.NaN, perDay = Double.NaN, max = Double.NaN, weight = Double.NaN, density = Double.NaN, reward = Double.NaN;
    private int stepDays = -1;

    public static Difficulty get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Difficulty::new, (tag, reg) -> load(tag)), "wflib_difficulty");
    }

    public Progression.Curve curve() {
        return new Progression.Curve(Progression.Mode.of(mode.isEmpty() ? DifficultyConfig.MODE.get() : mode),
                or(start, DifficultyConfig.START.get()), or(perDay, DifficultyConfig.PER_DAY.get()), or(max, DifficultyConfig.MAX.get()),
                stepDays > 0 ? stepDays : DifficultyConfig.STEP_DAYS.get());
    }

    private static double or(double own, double fallback) {
        return Double.isNaN(own) ? fallback : own;
    }

    public double weight() {
        return or(weight, DifficultyConfig.PLAYER_WEIGHT.get());
    }

    public double worldgenDensity() {
        return or(density, DifficultyConfig.WORLDGEN_DENSITY.get());
    }

    public double rewardScale() {
        return or(reward, DifficultyConfig.REWARD_SCALE.get());
    }

    /** In-game day, 0 for the first. */
    public static long day(MinecraftServer server) {
        return server.overworld().getGameTime() / 24000L;
    }

    /** The world's tier today. */
    public double worldTier(MinecraftServer server) {
        return curve().at(day(server));
    }

    /** What one operator's jobs are built to: the world tier plus their standing. */
    public double tier(MinecraftServer server, UUID player) {
        return Progression.playerTier(curve(), day(server), ProfileBook.get(server).of(player).rank().ordinal(), weight());
    }

    /** Applies one setting by name; throws with the reason when the value is not allowed. */
    public void set(String key, String value) {
        Progression.Curve c = curve();
        switch (key) {
            case "mode" -> {
                Progression.Mode m = Progression.Mode.of(value);
                new Progression.Curve(m, c.start(), c.perDay(), c.max(), c.stepDays());
                mode = m.id();
            }
            case "start" -> {
                double v = Double.parseDouble(value);
                new Progression.Curve(c.mode(), v, c.perDay(), c.max(), c.stepDays());
                start = v;
            }
            case "rate" -> {
                double v = Double.parseDouble(value);
                new Progression.Curve(c.mode(), c.start(), v, c.max(), c.stepDays());
                perDay = v;
            }
            case "max" -> {
                double v = Double.parseDouble(value);
                new Progression.Curve(c.mode(), Math.min(c.start(), v), c.perDay(), v, c.stepDays());
                max = v;
                start = Math.min(c.start(), v);
            }
            case "step" -> {
                int v = Integer.parseInt(value);
                new Progression.Curve(c.mode(), c.start(), c.perDay(), c.max(), v);
                stepDays = v;
            }
            case "weight" -> weight = range(value, 0, 1);
            case "worldgen" -> density = range(value, 0, 5);
            case "reward" -> reward = range(value, 0.1, 10);
            default -> throw new IllegalArgumentException("unknown setting '" + key + "'");
        }
        setDirty();
    }

    /** Back to the config's defaults. */
    public void reset() {
        mode = "";
        start = perDay = max = weight = density = reward = Double.NaN;
        stepDays = -1;
        setDirty();
    }

    private static double range(String value, double lo, double hi) {
        double v = Double.parseDouble(value);
        if (!(v >= lo && v <= hi)) throw new IllegalArgumentException("between " + lo + " and " + hi);
        return v;
    }

    public String describe(MinecraftServer server) {
        Progression.Curve c = curve();
        return String.format(java.util.Locale.ROOT, "%s, start %.2f, +%.3f/day, max %.1f%s; today %s; operator weight %.2f; worldgen x%.2f; pay x%.2f",
                c.mode().id(), c.start(), c.perDay(), c.max(), c.mode() == Progression.Mode.STEPPED ? ", every " + c.stepDays() + " days" : "",
                Tier.label(c.at(day(server))), weight(), worldgenDensity(), rewardScale());
    }

    private static Difficulty load(CompoundTag t) {
        Difficulty d = new Difficulty();
        d.mode = t.getString("mode");
        d.start = t.contains("start") ? t.getDouble("start") : Double.NaN;
        d.perDay = t.contains("perDay") ? t.getDouble("perDay") : Double.NaN;
        d.max = t.contains("max") ? t.getDouble("max") : Double.NaN;
        d.weight = t.contains("weight") ? t.getDouble("weight") : Double.NaN;
        d.density = t.contains("density") ? t.getDouble("density") : Double.NaN;
        d.reward = t.contains("reward") ? t.getDouble("reward") : Double.NaN;
        d.stepDays = t.contains("stepDays") ? t.getInt("stepDays") : -1;
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider registries) {
        t.putString("mode", mode);
        if (!Double.isNaN(start)) t.putDouble("start", start);
        if (!Double.isNaN(perDay)) t.putDouble("perDay", perDay);
        if (!Double.isNaN(max)) t.putDouble("max", max);
        if (!Double.isNaN(weight)) t.putDouble("weight", weight);
        if (!Double.isNaN(density)) t.putDouble("density", density);
        if (!Double.isNaN(reward)) t.putDouble("reward", reward);
        if (stepDays > 0) t.putInt("stepDays", stepDays);
        return t;
    }
}
