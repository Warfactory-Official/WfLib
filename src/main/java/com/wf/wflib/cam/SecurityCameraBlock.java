package com.wf.wflib.cam;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A fixed camera, bolted to a wall, feeding the same pipeline a drone does. */
public class SecurityCameraBlock extends Block implements EntityBlock {

    public static final MapCodec<SecurityCameraBlock> CODEC = simpleCodec(SecurityCameraBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Housing sits against the wall behind the lens: box on the side away from {@code FACING}. */
    private static final VoxelShape NORTH = Block.box(3.0, 1.0, 4.0, 13.0, 13.0, 16.0);
    private static final VoxelShape SOUTH = Block.box(3.0, 1.0, 0.0, 13.0, 13.0, 12.0);
    private static final VoxelShape WEST = Block.box(4.0, 1.0, 3.0, 16.0, 13.0, 13.0);
    private static final VoxelShape EAST = Block.box(0.0, 1.0, 3.0, 12.0, 13.0, 13.0);

    public SecurityCameraBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection();
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecurityCameraBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != CamSystem.SECURITY_CAMERA_BE.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (level.isClientSide
                ? (BlockEntityTicker<SecurityCameraBlockEntity>) SecurityCameraBlockEntity::clientTick
                : (BlockEntityTicker<SecurityCameraBlockEntity>) SecurityCameraBlockEntity::serverTick);
    }

    /** Using it with an empty hand switches it between following things and holding still. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof SecurityCameraBlockEntity camera) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("Security camera: " + (camera.toggleAutomatic() ? "following movement" : "holding still")), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** A redstone signal while the head is following something. */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof SecurityCameraBlockEntity camera && camera.tracking() ? 15 : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            StaticCameraFeeds.invalidate(sl, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        if (level instanceof ServerLevel sl) {
            StaticCameraFeeds.revive(sl, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }
}
