package com.wf.wflib.anomaly.api;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import com.wf.wflib.core.api.Hook;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import com.wf.wflib.anomaly.site.CaveFieldPiece;
import com.wf.wflib.anomaly.site.CaveFieldStructure;
import com.wf.wflib.anomaly.site.CaveLayout;
import com.wf.wflib.anomaly.site.CavePainter;
import com.wf.wflib.core.api.WFLib;

/**
 * Cave anomaly fields: registration, the slot hook, and building one on demand (the command). A world's fields and
 * artefacts are fixed by whoever sets {@link #ALLOCATION}, never rolled here.
 */
public final class AnomalyCaves {
    private AnomalyCaves() {
    }

    /**
     * One field the world holds: chunk of its start, tier, and exactly {@code artefacts} of the world's budget.
     * {@code seed} drives its plan; {@code index} < {@code count} of the world's slots.
     */
    public record Slot(int index, int count, int chunkX, int chunkZ, int tier, int artefacts, long seed,
                       long worldSeed) {
        /** Uniques exist at most once per world: only at the one slot their id hashes to; the rest anywhere. */
        public boolean allows(AnomalyMix.Loot l) {
            return l.share != AnomalyMix.UNIQUE_SHARE || hosts(l);
        }

        public boolean hosts(AnomalyMix.Loot l) {
            long h = worldSeed * 0x9E3779B97F4A7C15L + l.ordinal() * 0xC2B2AE3D27D4EB4FL;
            h ^= h >>> 31;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 29;
            return Math.floorMod(h, (long) count) == index;
        }
    }

    /** The world's fields. */
    public interface Allocation {
        @Nullable
        Slot at(int chunkX, int chunkZ);

        List<Slot> slots();

        /** Whether the world has any slot: cheap. */
        boolean any();
    }

    public static final Allocation NONE = new Allocation() {
        @Override
        public Slot at(int chunkX, int chunkZ) {
            return null;
        }

        @Override
        public List<Slot> slots() {
            return List.of();
        }

        @Override
        public boolean any() {
            return false;
        }
    };

    /** No fields unless a worldgen mod sets this. */
    public static final Hook<Allocation> ALLOCATION = Hook.of("anomaly cave allocation", NONE);

    /**
     * Surface sites keep their artefacts only while no cave allocation exists: with one, the cave slots are the whole
     * artefact budget.
     */
    public static boolean surfaceArtefacts() {
        return !ALLOCATION.get().any();
    }

    /** Start chunks are exactly the allocation's slots. */
    public static final class Placement extends StructurePlacement {
        public static final MapCodec<Placement> CODEC = MapCodec.unit(Placement::new);

        Placement() {
            super(net.minecraft.core.Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, 0, Optional.empty());
        }

        @Override
        protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
            return ALLOCATION.get().at(chunkX, chunkZ) != null;
        }

        @Override
        public StructurePlacementType<?> type() {
            return PLACEMENT.get();
        }
    }

    static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS = DeferredRegister.create(
            Registries.STRUCTURE_PLACEMENT, WFLib.MODID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, WFLib.MODID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, WFLib.MODID);
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<Placement>> PLACEMENT =
            PLACEMENTS.register("cave_slot", () -> () -> Placement.CODEC);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.structure.StructureType<?>,
            net.minecraft.world.level.levelgen.structure.StructureType<CaveFieldStructure>> TYPE = TYPES.register(
            "anomaly_cave", () -> () -> CaveFieldStructure.CODEC);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType,
            net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType> PIECE = PIECES.register(
            "anomaly_cave", () -> CaveFieldPiece::new);

    public static void register(IEventBus modBus) {
        PLACEMENTS.register(modBus);
        TYPES.register(modBus);
        PIECES.register(modBus);
    }

    /** What the command made. */
    public record Made(CavePlan plan, BlockPos floor, int anomalies, int artefacts) {
    }

    /** A field whose floor is the block under {@code at}, painted whole, at once. */
    public static Made place(ServerLevel level, BlockPos at, int tier, int artefacts) {
        RandomSource r = RandomSource.create();
        CavePlan plan = CavePlan.roll(r, tier, artefacts, l -> true);
        CaveLayout layout = CaveLayout.build(plan, at.getX(), at.getZ(), at.getY() - 1);
        BoundingBox box = layout.box();
        CavePainter.paint(layout, level, box);
        return new Made(plan, at.below(), layout.anomalies.size(), layout.loot.size());
    }
}
