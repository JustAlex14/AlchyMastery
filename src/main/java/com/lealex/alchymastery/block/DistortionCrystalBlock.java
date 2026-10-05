package com.lealex.alchymastery.block;

import com.lealex.alchymastery.block.entity.DistortionCrystalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The distortion crystal: crowns the distortion chamber (its roof center, on the upper amethyst block). Its block
 * model is the pedestal; client/DistortionCrystalRenderer draws the crystal spinning above it (vanilla's end crystal
 * model, textures/entity/distortion_crystal.png). The chamber's particles come out of it.
 */
public class DistortionCrystalBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 10, 15);
    /** Only for structure previews (guide pages, ghost blocks) and the item: adds a still crystal to the model. */
    public static final BooleanProperty PREVIEW = BooleanProperty.create("preview");

    public DistortionCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PREVIEW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PREVIEW);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistortionCrystalBlockEntity(pos, state);
    }
}
