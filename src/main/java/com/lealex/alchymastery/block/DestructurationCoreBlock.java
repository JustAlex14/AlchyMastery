package com.lealex.alchymastery.block;

import com.lealex.alchyx.block.MultiblockCoreBlock;
import com.lealex.alchymastery.block.entity.DestructurationCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The destructuration chamber's core block (placeholder look: magma). */
public class DestructurationCoreBlock extends MultiblockCoreBlock {

    public DestructurationCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DestructurationCoreBlockEntity(pos, state);
    }
}
