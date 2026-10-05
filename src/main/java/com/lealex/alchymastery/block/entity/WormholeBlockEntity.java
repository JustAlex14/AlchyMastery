package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: it only lets client/WormholeRenderer draw the wormhole (the look is in the block state). */
public class WormholeBlockEntity extends BlockEntity {
    public WormholeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.WORMHOLE_BE.get(), pos, state);
    }
}
