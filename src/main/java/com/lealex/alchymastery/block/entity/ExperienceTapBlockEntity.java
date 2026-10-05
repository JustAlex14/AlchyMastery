package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.block.ExperienceTapBlock;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * While open, the machine behind pours out of the nozzle as a stream of small experience orbs falling straight down
 * (2 points every 2 ticks: 20 points per second). An empty bottle held to the tap fills into a bottle o' enchanting.
 */
public class ExperienceTapBlockEntity extends BlockEntity {
    public static final int INTERVAL = 2;           // ticks
    public static final int POINTS_PER_POUR = 2;    // 20 points per second
    public static final int POINTS_PER_BOTTLE = 10; // a bottle o' enchanting gives 3-11 back (7 on average)

    public ExperienceTapBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.EXPERIENCE_TAP_BE.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % INTERVAL != 0) return;
        BlockState state = getBlockState();
        if (!state.getValue(ExperienceTapBlock.OPEN)) return;
        Direction facing = state.getValue(ExperienceTapBlock.FACING);
        ExperienceReservoir machine = machineBehind(facing);
        if (machine == null) return;
        int points = machine.drainPoints(POINTS_PER_POUR);
        if (points <= 0) return;
        // Under the nozzle (in the middle of the block), falling like a liquid: no random hop, no merging up there
        Vec3 mouth = Vec3.atCenterOf(worldPosition).add(0, -0.34, 0);
        ExperienceOrb orb = new ExperienceOrb(serverLevel, mouth.x, mouth.y, mouth.z, points);
        orb.setDeltaMovement(0, -0.12, 0);
        serverLevel.addFreshEntity(orb);
    }

    /** Takes a bottle's worth of experience from the machine behind; false (nothing taken) if it hasn't that much. */
    public boolean fillBottle() {
        if (level == null) return false;
        ExperienceReservoir machine = machineBehind(getBlockState().getValue(ExperienceTapBlock.FACING));
        if (machine == null || machine.getStoredPoints() < POINTS_PER_BOTTLE) return false;
        return machine.drainPoints(POINTS_PER_BOTTLE) >= POINTS_PER_BOTTLE;
    }

    /** The machine the tap is fixed to (rendering cauldron, experience nexus): the core or a part behind it. */
    private @Nullable ExperienceReservoir machineBehind(Direction facing) {
        if (level == null) return null;
        ExperienceReservoir machine = ExperienceReservoir.at(level, worldPosition.relative(facing.getOpposite()));
        return machine != null && machine.isFormed() ? machine : null;
    }
}
