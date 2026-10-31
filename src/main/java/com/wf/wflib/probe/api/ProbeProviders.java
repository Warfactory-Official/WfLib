package com.wf.wflib.probe.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/** Who gets asked about what. */
public final class ProbeProviders {

    private static final OrderedRegistry<Entry<Predicate<BlockState>, ProbeProvider.Blocks>> BLOCKS =
            OrderedRegistry.create("probe", "probe.blocks");
    private static final OrderedRegistry<Entry<Predicate<Entity>, ProbeProvider.Entities>> ENTITIES =
            OrderedRegistry.create("probe", "probe.entities");
    private static final OrderedRegistry<Entry<Predicate<BlockState>, ProbeDataProvider.Blocks>> BLOCK_DATA =
            OrderedRegistry.create("probe", "probe.block_data");
    private static final OrderedRegistry<Entry<Predicate<Entity>, ProbeDataProvider.Entities>> ENTITY_DATA =
            OrderedRegistry.create("probe", "probe.entity_data");
    private static final OrderedRegistry<Entry<Predicate<Entity>, ProbeProvider.EntityNodes>> ENTITY_NODES =
            OrderedRegistry.create("probe", "probe.entity_nodes");
    private static final OrderedRegistry<Entry<Predicate<BlockEntity>, ProbeProvider.BlockNodes>> BLOCK_NODES =
            OrderedRegistry.create("probe", "probe.block_nodes");

    /** The priority a "describe anything" provider registers at. */
    public static final int DEFAULT_PRIORITY = Integer.MIN_VALUE / 2;

    /** Where a capability's provider sits in the order. */
    public static final int CAPABILITY_PRIORITY = 0;

    private ProbeProviders() {
    }

    private record Entry<T, P>(T test, P provider, int priority) {
    }

    /** Providers run priority-ascending; {@link OrderedRegistry} is descending, hence the negated key. */
    private static <T, P> void insert(OrderedRegistry<Entry<T, P>> into, ResourceLocation id, T test, P provider,
                                      int priority) {
        into.register(id, -priority, new Entry<>(test, provider, priority));
    }

    // --- client side -----------------------------------------------------------------------------

    public static void addBlocks(ResourceLocation id, Predicate<BlockState> test, ProbeProvider.Blocks provider, int priority) {
        insert(BLOCKS, id, test, provider, priority);
    }

    public static void addBlock(ResourceLocation id, Block block, ProbeProvider.Blocks provider) {
        addBlocks(id, state -> state.is(block), provider, CAPABILITY_PRIORITY);
    }

    /** Asked about every block. The fallback that makes the panel show for anything at all. */
    public static void addAnyBlock(ResourceLocation id, ProbeProvider.Blocks provider, int priority) {
        addBlocks(id, state -> true, provider, priority);
    }

    public static void addEntities(ResourceLocation id, Predicate<Entity> test, ProbeProvider.Entities provider, int priority) {
        insert(ENTITIES, id, test, provider, priority);
    }

    public static void addEntity(ResourceLocation id, EntityType<?> type, ProbeProvider.Entities provider) {
        addEntities(id, entity -> entity.getType() == type, provider, CAPABILITY_PRIORITY);
    }

    public static void addAnyEntity(ResourceLocation id, ProbeProvider.Entities provider, int priority) {
        addEntities(id, entity -> true, provider, priority);
    }

    /** Target with nodes => no crosshair panel: its nodes carry everything. */
    public static void addEntityNodes(ResourceLocation id, Predicate<Entity> test, ProbeProvider.EntityNodes provider, int priority) {
        insert(ENTITY_NODES, id, test, provider, priority);
    }

    /** @see #addEntityNodes */
    public static void addBlockNodes(ResourceLocation id, Predicate<BlockEntity> test, ProbeProvider.BlockNodes provider, int priority) {
        insert(BLOCK_NODES, id, test, provider, priority);
    }

    public static boolean hasNodes(Entity entity) {
        for (Entry<Predicate<Entity>, ProbeProvider.EntityNodes> entry : ENTITY_NODES.values()) {
            if (entry.test().test(entity)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasNodes(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        for (Entry<Predicate<BlockEntity>, ProbeProvider.BlockNodes> entry : BLOCK_NODES.values()) {
            if (entry.test().test(blockEntity)) {
                return true;
            }
        }
        return false;
    }

    public static boolean anyBlockNodes() {
        return !BLOCK_NODES.values().isEmpty();
    }

    public static void collectNodes(List<ProbeNode> out, ProbeContext ctx, Entity entity) {
        for (Entry<Predicate<Entity>, ProbeProvider.EntityNodes> entry : ENTITY_NODES.values()) {
            if (entry.test().test(entity)) {
                entry.provider().nodes(out, ctx, entity);
            }
        }
    }

    public static void collectNodes(List<ProbeNode> out, ProbeContext ctx, BlockEntity blockEntity) {
        for (Entry<Predicate<BlockEntity>, ProbeProvider.BlockNodes> entry : BLOCK_NODES.values()) {
            if (entry.test().test(blockEntity)) {
                entry.provider().nodes(out, ctx, blockEntity);
            }
        }
    }

    // --- server side -----------------------------------------------------------------------------

    public static void addBlockData(ResourceLocation id, Predicate<BlockState> test, ProbeDataProvider.Blocks provider,
                                    int priority) {
        insert(BLOCK_DATA, id, test, provider, priority);
    }

    public static void addEntityData(ResourceLocation id, Predicate<Entity> test, ProbeDataProvider.Entities provider,
                                     int priority) {
        insert(ENTITY_DATA, id, test, provider, priority);
    }

    /** Whether anything at all wants server data about this block, so a request is worth sending. */
    public static boolean wantsBlockData(Level level, BlockPos pos, BlockState state,
                                         @Nullable BlockEntity blockEntity) {
        if (ProbeCapabilities.BLOCK_DATA.getCapability(level, pos, state, blockEntity, null) != null) {
            return true;
        }
        for (Entry<Predicate<BlockState>, ProbeDataProvider.Blocks> entry : BLOCK_DATA.values()) {
            if (entry.test().test(state)) {
                return true;
            }
        }
        return false;
    }

    public static boolean wantsEntityData(Entity entity) {
        if (entity.getCapability(ProbeCapabilities.ENTITY_DATA) != null) {
            return true;
        }
        for (Entry<Predicate<Entity>, ProbeDataProvider.Entities> entry : ENTITY_DATA.values()) {
            if (entry.test().test(entity)) {
                return true;
            }
        }
        return false;
    }

    // --- collection ------------------------------------------------------------------------------

    /**
     * Builds the panel for a block.
     *
     * @return whether the block is <em>supported</em>: it carries the probe capability, or a provider
     *      registered at anything but {@link #DEFAULT_PRIORITY} claimed it. Support is a matter of
     *      who claimed the target, not of whether they had anything to say about it this frame: a
     *      machine with nothing to report is still a machine, and its panel should not blink out.
     */
    public static boolean collect(ProbeInfo info, ProbeContext ctx, BlockState state, BlockPos pos,
                                  @Nullable BlockEntity blockEntity) {
        ProbeProvider.Blocks attached =
                ProbeCapabilities.BLOCK.getCapability(ctx.level(), pos, state, blockEntity, null);
        boolean supported = attached != null;
        boolean capabilityRun = attached == null;

        for (Entry<Predicate<BlockState>, ProbeProvider.Blocks> entry : BLOCKS.values()) {
            if (!capabilityRun && entry.priority() > CAPABILITY_PRIORITY) {
                attached.append(info, ctx, state, pos, blockEntity);
                capabilityRun = true;
            }
            if (!entry.test().test(state)) {
                continue;
            }
            entry.provider().append(info, ctx, state, pos, blockEntity);
            supported |= entry.priority() != DEFAULT_PRIORITY;
        }
        if (!capabilityRun) {
            attached.append(info, ctx, state, pos, blockEntity);
        }
        return supported;
    }

    /** @see #collect(ProbeInfo, ProbeContext, BlockState, BlockPos, BlockEntity) */
    public static boolean collect(ProbeInfo info, ProbeContext ctx, Entity entity) {
        ProbeProvider.Entities attached = entity.getCapability(ProbeCapabilities.ENTITY);
        boolean supported = attached != null;
        boolean capabilityRun = attached == null;

        for (Entry<Predicate<Entity>, ProbeProvider.Entities> entry : ENTITIES.values()) {
            if (!capabilityRun && entry.priority() > CAPABILITY_PRIORITY) {
                attached.append(info, ctx, entity);
                capabilityRun = true;
            }
            if (!entry.test().test(entity)) {
                continue;
            }
            entry.provider().append(info, ctx, entity);
            supported |= entry.priority() != DEFAULT_PRIORITY;
        }
        if (!capabilityRun) {
            attached.append(info, ctx, entity);
        }
        return supported;
    }

    public static void collectData(net.minecraft.nbt.CompoundTag data,
                                   net.minecraft.server.level.ServerPlayer player,
                                   Level level, BlockPos pos, BlockState state,
                                   @Nullable BlockEntity blockEntity) {
        ProbeDataProvider.Blocks attached =
                ProbeCapabilities.BLOCK_DATA.getCapability(level, pos, state, blockEntity, null);
        if (attached != null) {
            attached.append(data, player, level, pos, state, blockEntity);
        }
        for (Entry<Predicate<BlockState>, ProbeDataProvider.Blocks> entry : BLOCK_DATA.values()) {
            if (entry.test().test(state)) {
                entry.provider().append(data, player, level, pos, state, blockEntity);
            }
        }
    }

    public static void collectData(net.minecraft.nbt.CompoundTag data,
                                   net.minecraft.server.level.ServerPlayer player, Entity entity) {
        ProbeDataProvider.Entities attached = entity.getCapability(ProbeCapabilities.ENTITY_DATA);
        if (attached != null) {
            attached.append(data, player, entity);
        }
        for (Entry<Predicate<Entity>, ProbeDataProvider.Entities> entry : ENTITY_DATA.values()) {
            if (entry.test().test(entity)) {
                entry.provider().append(data, player, entity);
            }
        }
    }
}
