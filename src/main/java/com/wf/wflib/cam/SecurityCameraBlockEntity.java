package com.wf.wflib.cam;

import com.wf.wflib.cam.api.CameraNet;
import com.wf.wflib.cam.api.CameraSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * The head of a wall camera: where it is turned (pan, relative to the way the mount faces) and tilted (positive looks down).
 * The server moves it: toward whoever is operating the feed, else after the nearest hostile mob or player it can see, else it
 * sweeps the room. Clients are told where it is and smooth the rest.
 */
public class SecurityCameraBlockEntity extends BlockEntity {
    /** How far it looks for something to follow, in blocks. */
    public static final double RANGE = 24.0;
    /** Degrees per tick when it follows something, and while it sweeps. */
    private static final float FOLLOW_RATE = 7.0f, SWEEP_RATE = 0.9f;
    private static final int SCAN_EVERY = 8, LOSE_AFTER = 40;

    /** Where the head is, as the server has it (degrees). */
    private float pan, tilt;
    /** What clients last drew (client side only), for the smoothing. */
    public float drawPan, drawTilt, prevPan, prevTilt;
    /** On: follows targets and sweeps. Off: holds still unless an operator moves it. */
    private boolean auto = true;
    private boolean tracking;
    /** Somebody was aiming it last tick (so letting go can be noticed). */
    private boolean operated;
    private int sinceSeen;
    private long sweepClock;
    private int sentPan = Integer.MIN_VALUE, sentTilt = Integer.MIN_VALUE;

    public SecurityCameraBlockEntity(BlockPos pos, BlockState state) {
        super(CamSystem.SECURITY_CAMERA_BE.get(), pos, state);
        this.tilt = restTilt();
    }

    private static float restTilt() {
        return 10.0f;
    }

    public float pan() {
        return pan;
    }

    public float tilt() {
        return tilt;
    }

    public boolean automatic() {
        return auto;
    }

    /** True while the head is following something: what a redstone line reads. */
    public boolean tracking() {
        return tracking;
    }

    public boolean toggleAutomatic() {
        auto = !auto;
        setChanged();
        return auto;
    }

    private float mountYaw() {
        return getBlockState().getValue(SecurityCameraBlock.FACING).toYRot();
    }

    /** One server tick: decide where to look, turn toward it, tell the clients when the head has moved. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, SecurityCameraBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        be.step(sl);
    }

    /** One client tick: the drawn head eases toward the one the server sent. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, SecurityCameraBlockEntity be) {
        be.prevPan = be.drawPan;
        be.prevTilt = be.drawTilt;
        be.drawPan += Mth.wrapDegrees(be.pan - be.drawPan) * 0.45f;
        be.drawTilt += (be.tilt - be.drawTilt) * 0.45f;
    }

    private void step(ServerLevel level) {
        CameraSpec spec = CameraSpec.SECURITY;
        float wantPan = pan, wantTilt = tilt, rate = FOLLOW_RATE;
        var order = CameraNet.order(level, StaticCameraFeeds.idFor(level, worldPosition));
        boolean was = tracking;
        if (order == null && operated) {                               // the operator let go: stay where they left it until someone switches auto back on
            operated = false;
            auto = false;
            aim = null;
            setChanged();
        }
        if (order != null) {
            operated = true;                                           // somebody is at the screen: the head goes where they point it
            wantPan = Mth.wrapDegrees(order.yaw() - mountYaw());
            wantTilt = order.pitch();
            tracking = false;
            rate = spec.slewRate();
        } else if (auto) {
            LivingEntity target = level.getGameTime() % SCAN_EVERY == 0 ? findTarget(level, spec) : null;
            if (target != null) {
                sinceSeen = 0;
                aim = target;
            } else if (aim != null && (!aim.isAlive() || ++sinceSeen > LOSE_AFTER || !canSee(level, aim))) {
                aim = null;
            }
            if (aim != null) {
                Vec3 eye = lens();
                Vec3 at = aim.getEyePosition();
                double dx = at.x - eye.x, dz = at.z - eye.z, dy = at.y - eye.y;
                wantPan = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - mountYaw());
                wantTilt = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                tracking = true;
            } else {
                tracking = false;
                rate = SWEEP_RATE;
                sweepClock++;
                wantPan = (float) (Math.sin(sweepClock * 0.011) * spec.yawRange() * 0.6);
                wantTilt = restTilt() + (float) Math.sin(sweepClock * 0.017) * 4.0f;
            }
        } else {
            tracking = false;
            aim = null;
        }
        wantPan = Mth.clamp(wantPan, -spec.yawRange(), spec.yawRange());
        wantTilt = Mth.clamp(wantTilt, spec.pitchMin(), spec.pitchMax());
        pan += GimbalMath.approach(Mth.wrapDegrees(wantPan - pan), 1, rate);
        tilt += GimbalMath.approach(wantTilt - tilt, 1, rate);
        if (tracking != was) {
            if (tracking) {
                level.playSound(null, worldPosition, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.5f, 1.7f);
            }
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        int p = Math.round(pan * 2), t = Math.round(tilt * 2);          // half a degree is more than anyone can see
        if (p != sentPan || t != sentTilt) {
            sentPan = p;
            sentTilt = t;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private LivingEntity aim;

    private Vec3 lens() {
        return Vec3.atCenterOf(worldPosition).add(0, 0.1, 0);
    }

    private boolean canSee(ServerLevel level, LivingEntity e) {
        Vec3 eye = lens();
        return eye.distanceToSqr(e.getEyePosition()) <= RANGE * RANGE
                && level.clip(new ClipContext(eye, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e))
                .getType() == HitResult.Type.MISS;
    }

    /** The nearest hostile mob or player in the arc the mount can reach and in line of sight; none while nothing qualifies. */
    private LivingEntity findTarget(ServerLevel level, CameraSpec spec) {
        Vec3 eye = lens();
        float mount = mountYaw();
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(RANGE), e -> interesting(e)
                        && Math.abs(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-(e.getX() - eye.x), e.getZ() - eye.z)) - mount)) <= spec.yawRange()
                        && canSee(level, e)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(eye))).orElse(null);
    }

    private static boolean interesting(LivingEntity e) {
        if (!e.isAlive()) return false;
        if (e instanceof Player p) return !p.isSpectator() && !p.isCreative();
        return e instanceof Enemy && (!(e instanceof NeutralMob n) || n.isAngry());
    }

    /**
     * Everything a client needs to draw the head is in the saved tag, because the per-tick block update ({@code getUpdatePacket}) carries
     * exactly that: leaving pan and tilt out of it left the head frozen on every client and sent it back to rest on a reload.
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("auto", auto);
        tag.putFloat("pan", pan);
        tag.putFloat("tilt", tilt);
        tag.putBoolean("tracking", tracking);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("auto")) auto = tag.getBoolean("auto");
        if (!tag.contains("pan")) {
            return;                                                    // a block saved before the head was kept
        }
        boolean first = pan == 0 && tilt == restTilt() && drawPan == 0;
        pan = tag.getFloat("pan");
        tilt = tag.getFloat("tilt");
        tracking = tag.getBoolean("tracking");
        if (first) {                                                   // a camera that has just come into view starts where it is
            drawPan = pan;
            drawTilt = tilt;
            prevPan = pan;
            prevTilt = tilt;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
