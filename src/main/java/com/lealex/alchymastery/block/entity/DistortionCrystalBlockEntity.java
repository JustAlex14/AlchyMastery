package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: it only lets client/DistortionCrystalRenderer draw the floating crystal. */
public class DistortionCrystalBlockEntity extends BlockEntity {
    public DistortionCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.DISTORTION_CRYSTAL_BE.get(), pos, state);
    }
}
