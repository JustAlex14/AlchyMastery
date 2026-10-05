package com.lealex.alchymastery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A look for chamber shells: hanging dripstone recolored to prismarine (the condensator's dripstone). {@code base}:
 * the upper block of a two-block stalactite (a thicker frustum); otherwise the tip. No item, no drops; it only
 * exists as a shell's disguise. Same shapes as vanilla's hanging tip and frustum.
 */
public class StalactiteBlock extends Block {
    public static final BooleanProperty BASE = BooleanProperty.create("base");
    private static final VoxelShape TIP = Block.column(6.0, 5.0, 16.0);
    private static final VoxelShape FRUSTUM = Block.column(8.0, 0.0, 16.0);

    public StalactiteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BASE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BASE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BASE) ? FRUSTUM : TIP;
    }
}
