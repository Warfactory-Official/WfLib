package com.wf.wflib.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import com.wf.wflib.WFLib;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * How readily radiation crosses a block, loaded as named classes - a block list and one value each. An entry
 * id is a class name in this mod's namespace and never a block id: both loaders' datagen filter their output
 * by namespace, so a registry keyed by {@code minecraft:stone} would ship as an empty directory. A block
 * matched by several entries takes the minimum, dynamic-registry iteration order being unspecified, and the
 * mapping's lifetime is world load, which every coefficient cache in the backend rests on.
 */
public record RadiationDiffusivity(List<Block> blocks, float value) {

    public static final ResourceKey<Registry<RadiationDiffusivity>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "rad_diffusivity"));

    /**
     * The identity of the edge law: {@code (la+lb)/(la/da+lb/db)} collapses to 1.0 exactly for two sides at
     * 1.0, so an all-neutral mapping is bit-identical to the feature being off.
     */
    public static final float NEUTRAL = 1.0F;

    /** Mirrors {@code clamp_diffusivity}, so data and the kernel cannot disagree about what is legal. */
    public static final float MIN = 0.01F, MAX = 1.50F;

    public static final Codec<RadiationDiffusivity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().listOf().fieldOf("blocks")
                    .forGetter(RadiationDiffusivity::blocks),
            Codec.floatRange(MIN, MAX).fieldOf("value").forGetter(RadiationDiffusivity::value)
    ).apply(instance, RadiationDiffusivity::new));

    /** Identity-keyed, exactly parallel to {@code RadiationSystemNT.RAD_RESISTANT_STATES}. */
    private static final Reference2FloatOpenHashMap<BlockState> STATES = new Reference2FloatOpenHashMap<>(512);
    private static boolean resolved;

    /** Bumped whenever {@link #STATES} changes, so a thread-local id table can be cached against it. */
    static volatile int stateVersion;

    static {
        STATES.defaultReturnValue(NEUTRAL);
    }

    /** Server-thread only, at world load; every later read is a rebuild task past the barrier that publishes it. */
    static void resolveFor(ServerLevel level) {
        if (resolved) return;
        resolved = true;
        level.registryAccess().lookupOrThrow(REGISTRY).listElements().forEach(holder -> {
            RadiationDiffusivity entry = holder.value();
            // Skipped rather than stored: it is the identity, so it changes no value, and dropping it keeps
            // isTrivial a sharp test.
            if (entry.value() == NEUTRAL) return;
            for (Block block : entry.blocks()) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    if (!STATES.containsKey(state) || entry.value() < STATES.getFloat(state)) {
                        STATES.put(state, entry.value());
                    }
                }
            }
        });
        stateVersion++;
    }

    /** Cleared alongside {@code worldMap}; a second server in one JVM resolves its own registry. */
    static void clear() {
        STATES.clear();
        resolved = false;
        stateVersion++;
    }

    static float of(BlockState state) {
        return STATES.getFloat(state);
    }

    static boolean isTrivial() {
        return STATES.isEmpty();
    }

    static boolean transportEnabled(ServerLevel level) {
        resolveFor(level);
        return RadiationSettings.forLevel(level).diffusivityTransport().orElse(!isTrivial());
    }

    /** Non-finite reads as the floor rather than as a hole; mirrors {@code clamp_diffusivity}. */
    static float clamp(float value) {
        if (!Float.isFinite(value)) return MIN;
        return Math.min(Math.max(value, MIN), MAX);
    }

    /**
     * Series resistance across a face, transcribing {@code edge_diffusivity}.
     *
     * @return 0.0 where the pair is closed, which every caller treats as a skip
     */
    static double edge(double lenA, double diffA, double lenB, double diffB) {
        if (!(diffA > 0.0D) || !(diffB > 0.0D)) return 0.0D;
        double denom = (lenA / diffA) + (lenB / diffB);
        double lenSum = lenA + lenB;
        if (!(denom > 0.0D) || !(lenSum > 0.0D) || !Double.isFinite(denom) || !Double.isFinite(lenSum)) {
            return 0.0D;
        }
        return lenSum / denom;
    }

    /** Transcribes {@code exchange_decay}: the retained-difference factor for an exponent already formed. */
    static double decay(double k) {
        if (!(k > 0.0D) || !Double.isFinite(k)) return 1.0D;
        if (k >= 700.0D) return 0.0D;
        return Math.exp(-k);
    }
}
