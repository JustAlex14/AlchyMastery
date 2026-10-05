package com.lealex.alchymastery.block.entity;

import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.multiblock.CoreTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A machine that stores liquid experience and hands it out: the rendering cauldron and the experience nexus.
 * Experience taps pour from it, experience siphons link to it (one player at a time) and repair Mending gear with it.
 */
public interface ExperienceReservoir {
    BlockPos getBlockPos();

    boolean isFormed();

    /** Whole experience points stored. */
    int getStoredPoints();

    /** Takes up to maxPoints experience points out and returns how many it took. */
    int drainPoints(int maxPoints);

    /** Links this player (a siphon); false while another player holds the machine. */
    boolean link(ServerPlayer player);

    void unlink(UUID player);

    boolean isLinkedTo(UUID player);

    String getLinkedName();

    /** What it's called in chat messages, e.g. "rendering cauldron". */
    String reservoirName();

    /** The reservoir whose core or formed structure is at pos (any machine of this kind), or null. */
    static @Nullable ExperienceReservoir at(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ExperienceReservoir reservoir) return reservoir;
        MultiblockCoreBlockEntity core = CoreTracker.formedCoreAt(level, pos);
        return core instanceof ExperienceReservoir reservoir ? reservoir : null;
    }
}
