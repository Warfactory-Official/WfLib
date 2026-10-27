package com.wf.wflib.anomaly;

import com.mojang.serialization.MapCodec;
import com.wf.wflib.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** An anomaly of one {@link AnomalyType}: invisible, walk-through, targetable only by creative players (to remove it). */
public class AnomalyBlock extends BaseEntityBlock {
    private final AnomalyType type;
    private final MapCodec<AnomalyBlock> codec;

    public AnomalyBlock(AnomalyType type, Properties properties) {
        super(properties);
        this.type = type;
        this.codec = simpleCodec(p -> new AnomalyBlock(type, p));
    }

    public AnomalyType type() {
        return type;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context instanceof EntityCollisionContext entity && entity.getEntity() instanceof Player player
                && player.isCreative() ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnomalyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.ANOMALY.get(), level.isClientSide
                ? AnomalyBlockEntity::clientTick : AnomalyBlockEntity::serverTick);
    }
}
