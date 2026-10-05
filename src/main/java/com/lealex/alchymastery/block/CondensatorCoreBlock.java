package com.lealex.alchymastery.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.core.Direction;
import com.lealex.alchyx.block.FacingCoreBlock;
import com.lealex.alchymastery.block.entity.CondensatorCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The distortion condensator's core: looks like a conduit (drawn by CondensatorCoreRenderer; its block model is
 * empty, except in the PREVIEW state used by structure previews), with the conduit's shape. Faces the player who places it; the pattern's open front goes on that side.
 */
public class CondensatorCoreBlock extends FacingCoreBlock {
    private static final VoxelShape SHAPE = Block.cube(6.0); // like vanilla's conduit
    /**
     * Only for structure previews (the Codex page, the ghost blocks): they draw block models only, and this block
     * is drawn by its renderer. In the world it's always false.
     */
    public static final BooleanProperty PREVIEW = BooleanProperty.create("preview");

    public CondensatorCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PREVIEW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PREVIEW);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CondensatorCoreBlockEntity(pos, state);
    }
}
