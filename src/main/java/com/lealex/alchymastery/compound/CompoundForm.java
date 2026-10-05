package com.lealex.alchymastery.compound;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The forms of a material the destructuration chamber accepts, with the design spec's defaults:
 * compounds produced, DE per operation, and time in ticks (20 ticks = 1 second).
 * A material file can override the compound count per form with "costs".
 */
public enum CompoundForm implements StringRepresentable {
    RAW("raw", 3, 20, 100),               // raw ore (raw iron)
    ORE("ore", 6, 40, 160),               // natural ore block
    INGOT("ingot", 1, 10, 40),
    BLOCK("block", 9, 90, 360),           // ingot storage block (block of iron)
    RAW_BLOCK("raw_block", 27, 180, 900), // raw ore storage block
    GEM("gem", 1, 10, 40),                // diamond, emerald...: like an ingot
    GEM_ORE("gem_ore", 3, 20, 100),       // 3, not 6: Fortune already multiplies gem drops
    DROP("drop", 1, 10, 60),              // a mob drop (rotten flesh, bone, ender pearl...): spec 10 DE, 3 s
    DROP_BLOCK("drop_block", 9, 90, 360); // a mob drop storage block (slime block = 9; bone block overrides to 3)

    public static final Codec<CompoundForm> CODEC = StringRepresentable.fromEnum(CompoundForm::values);

    private final String id;
    public final int defaultCompounds;
    public final int energy;
    public final int ticks;

    CompoundForm(String id, int defaultCompounds, int energy, int ticks) {
        this.id = id;
        this.defaultCompounds = defaultCompounds;
        this.energy = energy;
        this.ticks = ticks;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
