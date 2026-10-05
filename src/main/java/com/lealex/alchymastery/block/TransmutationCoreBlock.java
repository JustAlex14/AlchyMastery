package com.lealex.alchymastery.block;

import com.lealex.alchyx.block.FacingCoreBlock;
import com.lealex.alchymastery.block.entity.TransmutationCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The transmutation chamber's core (placeholder look: an enchanting table; the book is drawn by its renderer).
 * Faces the player who places it; the pattern's front (stairs and shelves) goes on that side.
 */
public class TransmutationCoreBlock extends FacingCoreBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16); // enchanting table height

    public TransmutationCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransmutationCoreBlockEntity(pos, state);
    }
}
