package com.lealex.alchymastery.block;

import com.lealex.alchyx.block.MultiblockCoreBlock;
import com.lealex.alchymastery.block.entity.NexusCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The alchemical nexus core (placeholder look: a beacon), at the center of the end-game structure. */
public class NexusCoreBlock extends MultiblockCoreBlock {
    public NexusCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NexusCoreBlockEntity(pos, state);
    }
}
