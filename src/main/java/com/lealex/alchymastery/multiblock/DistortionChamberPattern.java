package com.lealex.alchymastery.multiblock;

import com.lealex.alchyx.multiblock.MultiblockPatterns;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import org.jspecify.annotations.Nullable;

/**
 * The distortion chamber's shape. It is no longer hardcoded: it comes from
 * {@code data/alchymastery/multiblock/distortion_chamber.json}, which a datapack can override
 * (then {@code /reload}). See {@link MultiblockPattern} for the format.
 */
public final class DistortionChamberPattern {

    private DistortionChamberPattern() {}

    /** The current pattern, or null if the JSON is missing or invalid (see the log). */
    public static @Nullable MultiblockPattern get() {
        return MultiblockPatterns.get(ModPatterns.DISTORTION_CHAMBER);
    }
}
