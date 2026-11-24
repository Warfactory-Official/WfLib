package com.wf.wflib.cam.api;

import com.wf.wflib.cam.FeedHosts;
import com.wf.wflib.core.api.ChunkTicketOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import com.wf.wflib.cam.CamSystem;

public class CameraMonitorBlockEntity extends BlockEntity {

    public static final double BIND_RANGE = 1600.0;
    /** Cheap; 1s staleness invisible. */
    private static final int RESOLVE_INTERVAL = 20;

    private CameraChannels channels = CameraChannels.EMPTY;
    /** Auto-tune pick; meaningful only while {@link #channels} empty. */
    @Nullable
    private UUID bound;

    private volatile int feedId;
    /** Entity id per channel; 0 = unreachable. */
    private volatile int[] feedIds = new int[0];
    @Nullable
    private volatile CameraNet.Monitor monitor;
    /** Client-display mirror; server reads {@link #channels} directly. */
    private int selectedChannel;
    private int resolveIn;

    public CameraMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(CamSystem.CAMERA_MONITOR_BE.get(), pos, state);
    }

    public void serverTick() {
        if (!(this.level instanceof ServerLevel sl)) {
            return;
        }
        if (--this.resolveIn > 0) {
            CameraNet.Monitor held = CameraNet.touchMonitor(sl, this.monitor, this.worldPosition, this.feedId,
                    this.feedIds);
            if (held != null) {
                this.monitor = held;
                return;
            }
        }
        if (sl.getServer().isSameThread()) {
            refresh(sl);
        } else {
            // Entity lookups by uuid, chunk wake-ups and feed-id allocation are server-thread only.
            this.resolveIn = RESOLVE_INTERVAL;
            ChunkTicketOps.run(sl, this::refreshIfLive);
        }
    }

    private void refreshIfLive() {
        if (!this.isRemoved() && this.level instanceof ServerLevel sl) {
            refresh(sl);
        }
    }

    private void refresh(ServerLevel level) {
        this.resolveIn = RESOLVE_INTERVAL;
        int resolved;
        int[] all;
        if (this.channels.isEmpty()) {
            Entity drone = resolveDrone(level);
            this.bound = drone == null ? null : drone.getUUID();
            resolved = drone == null ? 0 : drone.getId();
            all = resolved == 0 ? new int[0] : new int[]{resolved};
        } else {
            all = this.channels.resolveAll(level);
            resolved = this.channels.selectedFeed(level);
            if (resolved == 0 && recall(level)) {
                all = this.channels.resolveAll(level);
                resolved = this.channels.selectedFeed(level);
            }
        }
        if (resolved != this.feedId || this.selectedChannel != this.channels.selected()
                || !Arrays.equals(all, this.feedIds)) {
            this.feedId = resolved;
            this.feedIds = all;
            this.selectedChannel = this.channels.selected();
            this.setChanged();
            level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        this.monitor = CameraNet.registerMonitor(level, this.worldPosition, this.feedId, this.feedIds);
    }

    /** @return true if worth re-resolving immediately. */
    private boolean recall(ServerLevel level) {
        FeedTarget target = this.channels.current();
        if (target == null || target.drone().isEmpty()) {
            return false;
        }
        if (!level.hasNearbyAlivePlayer(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5, CameraNet.MONITOR_AUDIENCE)) {
            return false;
        }
        return FeedHosts.recall(level, target.drone().get());
    }

    public void cycle(ServerLevel level) {
        if (this.channels.isEmpty()) {
            List<Entity> candidates = candidates(level);
            if (candidates.isEmpty()) {
                this.bound = null;
            } else {
                int at = -1;
                for (int i = 0; i < candidates.size(); i++) {
                    if (candidates.get(i).getUUID().equals(this.bound)) {
                        at = i;
                        break;
                    }
                }
                this.bound = candidates.get((at + 1) % candidates.size()).getUUID();
            }
        } else {
            this.channels = this.channels.cycle(1);
        }
        this.setChanged();
        refresh(level);
    }

    public void setChannels(CameraChannels next, ServerLevel level) {
        this.channels = next;
        this.setChanged();
        refresh(level);
    }

    public CameraChannels channels() {
        return this.channels;
    }

    public int feedId() {
        return this.feedId;
    }

    public int[] feedIds() {
        return this.feedIds;
    }

    public int selectedChannel() {
        return this.selectedChannel;
    }

    @Nullable
    private Entity resolveDrone(ServerLevel level) {
        List<Entity> candidates = candidates(level);
        if (candidates.isEmpty()) {
            return null;
        }
        if (this.bound != null) {
            for (Entity drone : candidates) {
                if (drone.getUUID().equals(this.bound)) {
                    return drone;
                }
            }
        }
        return candidates.get(0);
    }

    private List<Entity> candidates(ServerLevel level) {
        return FeedHosts.near(level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5, BIND_RANGE);
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel sl) {
            CameraNet.unregisterMonitor(sl, this.worldPosition);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.bound != null) {
            tag.putUUID("Bound", this.bound);
        }
        if (!this.channels.isEmpty()) {
            CameraChannels.CODEC.encodeStart(NbtOps.INSTANCE, this.channels).result()
                    .ifPresent(encoded -> tag.put("Channels", encoded));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.bound = tag.hasUUID("Bound") ? tag.getUUID("Bound") : null;
        this.channels = tag.contains("Channels")
                ? CameraChannels.CODEC.parse(NbtOps.INSTANCE, tag.get("Channels"))
                .result().orElse(CameraChannels.EMPTY)
                : CameraChannels.EMPTY;
        this.feedId = tag.getInt("Feed");
        this.feedIds = tag.getIntArray("Feeds");
        this.selectedChannel = tag.getInt("Selected");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Feed", this.feedId);
        tag.putIntArray("Feeds", this.feedIds);
        tag.putInt("Selected", this.selectedChannel);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
