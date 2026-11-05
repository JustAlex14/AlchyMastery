package com.lealex.alchymastery.compound;

import net.minecraft.resources.Identifier;

/**
 * Transmutation rules from the design spec, as named constants (meant to become config options).
 * Values are counted in hundredths of an abundant compound, so the 20% loss never needs fractions.
 */
public final class Transmutation {
    public static final int LOSS_PERCENT = 20;      // value lost when transmuting between different values
    public static final int ENERGY_PER_VALUE = 5;   // DE per abundant (copper) of value produced
    public static final int OPERATION_TICKS = 40;   // 2 seconds per operation
    public static final long UNIT = 100;            // 1 abundant compound = 100

    private Transmutation() {}

    /**
     * Value of one compound of this tier, in hundredths. Set by the compoundTiers config
     * (default tier 0 = 100, tier 1 = 300, tier 2 = 900...).
     */
    public static long valueOf(int tier) {
        return com.lealex.alchymastery.Config.tierValue(tier) * UNIT;
    }

    /** Value of one compound of this family and tier, in hundredths (the family's own tiers if it has some). */
    public static long valueOf(String family, int tier) {
        return com.lealex.alchymastery.Config.tierValue(family, tier) * UNIT;
    }

    /**
     * Value of one compound of this material: from its tier, or from its "worth" (count x another material's
     * value, followed through the chain). 0 if the material is unknown or the chain loops.
     */
    public static long valueOf(Identifier material) {
        return valueOf(material, 0);
    }

    private static long valueOf(Identifier material, int depth) {
        CompoundMaterial data = CompoundMaterials.get(material);
        if (data == null || depth > 8) return 0;
        if (data.worth().isPresent()) {
            return data.worth().get().count() * valueOf(data.worth().get().of(), depth + 1);
        }
        return valueOf(data.family(), data.tier());
    }

    /**
     * Whether compounds of {@code from} can become {@code to}: both known and of the same family (a mineral never
     * becomes a mob drop and the other way around).
     */
    public static boolean compatible(Identifier from, Identifier to) {
        CompoundMaterial a = CompoundMaterials.get(from), b = CompoundMaterials.get(to);
        return a != null && b != null && a.family().equals(b.family());
    }

    /** What one input compound adds to the buffer: its full value if it equals the target's, else 80% of it. */
    public static long effectiveValue(long inputValue, long targetValue) {
        return effectiveValue(inputValue, targetValue, LOSS_PERCENT);
    }

    /** Same with another loss (Productivity upgrades lower it: upgrade/Upgrades.TRANSMUTATION_LOSS). */
    public static long effectiveValue(long inputValue, long targetValue, int lossPercent) {
        return inputValue == targetValue ? inputValue : inputValue * (100 - lossPercent) / 100;
    }
}
