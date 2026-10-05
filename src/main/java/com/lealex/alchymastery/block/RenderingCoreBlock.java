package com.lealex.alchymastery.block;

import com.lealex.alchymastery.block.entity.RenderingCoreBlockEntity;
import com.lealex.alchyx.block.FacingCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rendering cauldron's core (placeholder look: deepslate tiles with a sculk front). Faces the player who places
 * it; the pattern's front goes on that side.
 */
public class RenderingCoreBlock extends FacingCoreBlock {
    public RenderingCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RenderingCoreBlockEntity(pos, state);
    }
}
