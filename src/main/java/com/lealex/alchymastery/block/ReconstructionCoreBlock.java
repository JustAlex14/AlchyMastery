package com.lealex.alchymastery.block;

import com.lealex.alchyx.block.FacingCoreBlock;
import com.lealex.alchymastery.block.entity.ReconstructionCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The reconstruction chamber's core: looks like a brewing stand (vanilla models). Its three bottles fill up one by
 * one while an item is being rebuilt (the vanilla has_bottle_0..2 properties, set by the block entity). Faces the
 * player who places it; the pattern's open front goes on that side.
 */
public class ReconstructionCoreBlock extends FacingCoreBlock {
    // Same shape as vanilla's brewing stand: the rod and the base plate
    private static final VoxelShape SHAPE = Shapes.or(Block.column(2.0, 2.0, 14.0), Block.column(14.0, 0.0, 2.0));

    public ReconstructionCoreBlock(Properties properties) {
        super(properties);
        BlockState state = defaultBlockState();
        for (var bottle : BrewingStandBlock.HAS_BOTTLE) state = state.setValue(bottle, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); // facing
        builder.add(BrewingStandBlock.HAS_BOTTLE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReconstructionCoreBlockEntity(pos, state);
    }
}
