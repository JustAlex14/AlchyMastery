package com.lealex.alchymastery.block;

import com.lealex.alchyx.block.MultiblockCoreBlock;
import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The distortion chamber's core block (right-click, ticking and the builder are in MultiblockCoreBlock). */
public class DistortionMatrixBlock extends MultiblockCoreBlock {

    public DistortionMatrixBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistortionMatrixBlockEntity(pos, state);
    }
}
