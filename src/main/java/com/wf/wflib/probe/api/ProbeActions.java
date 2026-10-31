package com.wf.wflib.probe.api;

import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.core.api.WFNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.wf.wflib.probe.ProbeActionPacket;
import com.wf.wflib.probe.ProbeSight;

/** What happens when a probe action is chosen: the server half, keyed by action id. */
public final class ProbeActions {

    /** Beyond this an action request is dropped: further than anyone can be interacting from. */
    static final double MAX_RANGE_SQR = 8.0 * 8.0;
    private static final long MIN_INTERVAL_MS = 100L;

    private static final WFRegistry<BlockHandler> BLOCKS = WFRegistry.create("probe", "probe.block_actions");
    private static final WFRegistry<EntityHandler> ENTITIES = WFRegistry.create("probe", "probe.entity_actions");
    private static final Map<UUID, Long> LAST_ACTED = new ConcurrentHashMap<>();

    private ProbeActions() {
    }

    @FunctionalInterface
    public interface BlockHandler {
        /** @return true if the action was performed; false leaves the player's list as it was */
        boolean perform(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, int arg);
    }

    @FunctionalInterface
    public interface EntityHandler {
        boolean perform(ServerPlayer player, Entity entity, int arg);
    }

    /**
     * Channelled: {@link #ticks} decides and starts, {@link ProbeJobs} watches, {@link #perform} runs at the end.
     * Interrupts: player hurt or moved, target moved or out of reach, {@link #holds} false, G pressed again.
     */
    public interface TimedEntity extends EntityHandler {
        /** Duration; {@code <= 0} refuses to start (tell the player why). */
        int ticks(ServerPlayer player, Entity entity, int arg);

        /** Re-checked every tick; false cancels. */
        default boolean holds(ServerPlayer player, Entity entity, int arg) {
            return true;
        }
    }

    public interface TimedBlock extends BlockHandler {
        int ticks(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, int arg);

        default boolean holds(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, int arg) {
            return true;
        }
    }

    /** A {@link TimedBlock} registers as a timed action. */
    public static void registerBlock(ResourceLocation id, BlockHandler handler) {
        BLOCKS.register(id, handler);
    }

    /** A {@link TimedEntity} registers as a timed action. */
    public static void registerEntity(ResourceLocation id, EntityHandler handler) {
        ENTITIES.register(id, handler);
    }

    public static void registerTimedBlock(ResourceLocation id, TimedBlock handler) {
        BLOCKS.register(id, handler);
    }

    public static void registerTimedEntity(ResourceLocation id, TimedEntity handler) {
        ENTITIES.register(id, handler);
    }

    /** Client: asks the server to run {@code action} on an entity, as if its row were chosen. */
    public static void request(int entityId, ResourceLocation action, int arg) {
        WFNetwork.sendToServer(new ProbeActionPacket(BlockPos.ZERO, entityId, action, arg));
    }

    /** Payload handler. */
    public static void perform(BlockPos pos, int entityId, ResourceLocation action, int arg, ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_ACTED.get(player.getUUID());
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return;
        }
        LAST_ACTED.put(player.getUUID(), now);
        if (ProbeJobs.cancel(player, ProbeJobs.Reason.STOPPED)) {
            return; // G while busy = stop
        }

        ServerLevel level = player.serverLevel();
        if (entityId >= 0) {
            Entity entity = level.getEntity(entityId);
            if (entity == null || entity.getBoundingBox().distanceToSqr(player.getEyePosition()) > MAX_RANGE_SQR
                    || !ProbeSight.sees(player, entity)) {
                return;
            }
            EntityHandler handler = ENTITIES.get(action);
            if (handler instanceof TimedEntity timed) {
                int ticks = timed.ticks(player, entity, arg);
                if (ticks > 0) {
                    ProbeJobs.start(player, new ProbeJobs.EntityJob(player, entity, timed, action,
                            arg, ticks));
                }
                return;
            }
            if (handler != null) {
                handler.perform(player, entity, arg);
            }
            return;
        }

        if (!level.isLoaded(pos)
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_RANGE_SQR
                || !ProbeSight.sees(player, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockHandler handler = BLOCKS.get(action);
        if (handler instanceof TimedBlock timed) {
            int ticks = timed.ticks(player, level, pos, state, arg);
            if (ticks > 0) {
                ProbeJobs.start(player, new ProbeJobs.BlockJob(player, level, pos, state, timed, action,
                        arg, ticks));
            }
            return;
        }
        if (handler != null) {
            handler.perform(player, level, pos, state, arg);
        }
    }

    /** Server-side entry, as if {@code player} chose the row: commands, tests. */
    public static void performEntity(ServerPlayer player, Entity entity, ResourceLocation action, int arg) {
        LAST_ACTED.remove(player.getUUID());
        perform(BlockPos.ZERO, entity.getId(), action, arg, player);
    }

    public static void forget(ServerPlayer player) {
        LAST_ACTED.remove(player.getUUID());
        ProbeJobs.forget(player);
    }
}
