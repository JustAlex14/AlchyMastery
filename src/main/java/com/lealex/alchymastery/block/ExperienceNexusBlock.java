package com.lealex.alchymastery.block;

import com.lealex.alchyx.block.FacingCoreBlock;
import com.lealex.alchymastery.block.entity.ExperienceNexusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The experience nexus core, at the center of the experience chain's all-in-one structure. Faces the player who
 * places it: the pattern's front (the experience basin) goes on that side, so it's the first thing they see.
 */
public class ExperienceNexusBlock extends FacingCoreBlock {
    public ExperienceNexusBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExperienceNexusBlockEntity(pos, state);
    }
}
