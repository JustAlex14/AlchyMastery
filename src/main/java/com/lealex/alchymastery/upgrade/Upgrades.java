package com.lealex.alchymastery.upgrade;

/**
 * What each upgrade tier does (index = tier, 0 = no upgrade). Meant to become config options, like the costs in
 * compound/Transmutation. Every machine reads these through PoweredCoreBlockEntity.
 */
public final class Upgrades {
    private Upgrades() {}

    /** Speed I-IV: operations x1.5 / x2 / x3 / x4 faster... */
    public static final double[] SPEED = {1.0, 1.5, 2.0, 3.0, 4.0};
    /** ...and each costs +10 / 20 / 30 / 40% energy. */
    public static final double[] SPEED_ENERGY = {1.0, 1.1, 1.2, 1.3, 1.4};
    /** Efficiency I-III: -20% energy per operation per tier. */
    public static final double[] EFFICIENCY = {1.0, 0.8, 0.6, 0.4};

    // Productivity I-IV, per machine
    /** Destructuration: chance of an extra compound (5% before upgrades, design spec). */
    public static final float[] BYPRODUCT_CHANCE = {0.05f, 0.10f, 0.15f, 0.20f, 0.25f};
    /** Transmutation: value lost when transmuting between different values, in percent (design spec: 20% -> 5%). */
    public static final int[] TRANSMUTATION_LOSS = {20, 15, 10, 7, 5};
    /** Reconstruction: share of the recipe's distortion fluid it uses. */
    public static final double[] RECONSTRUCTION_FLUID = {1.0, 0.85, 0.7, 0.55, 0.4};
    /** Condensator: distortion fluid made per batch, as a share of the base 500 mB (up to 1:1 with the water). */
    public static final double[] CONDENSATOR_FLUID = {1.0, 1.25, 1.5, 1.75, 2.0};
    /** Rendering: experience per essence. */
    public static final double[] RENDERING_EXPERIENCE = {1.0, 1.15, 1.3, 1.45, 1.6};

    // The distortion chamber (Productivity and Efficiency only)
    /** Productivity: DE per fuel item. */
    /** Distortion chamber: Speed burns faster (x SPEED) but each fuel gives DE / this. */
    public static final double[] MATRIX_SPEED_FUEL = {1.0, 1.15, 1.3, 1.5, 1.75};
    public static final double[] MATRIX_ENERGY = {1.0, 1.25, 1.5, 1.75, 2.0};
    /** Efficiency: chance that a fuel item burns without being used up. */
    public static final float[] MATRIX_FUEL_SAVE = {0f, 0.15f, 0.3f, 0.45f};

    /** An operation's ticks with this speed tier (at least 1). */
    public static int ticks(int baseTicks, int speedTier) {
        if (baseTicks <= 0) return baseTicks;
        return Math.max(1, (int) Math.round(baseTicks / SPEED[clamp(speedTier, SPEED.length)]));
    }

    /** An operation's energy with these speed and efficiency tiers (an operation that costs something still does). */
    public static long energy(long baseEnergy, int speedTier, int efficiencyTier) {
        if (baseEnergy <= 0) return baseEnergy;
        double factor = SPEED_ENERGY[clamp(speedTier, SPEED_ENERGY.length)] * EFFICIENCY[clamp(efficiencyTier, EFFICIENCY.length)];
        return Math.max(1, Math.round(baseEnergy * factor));
    }

    /** Scales an amount (fluid, experience) by a productivity table, rounding to the nearest whole. */
    public static int scale(int amount, double[] table, int tier) {
        return (int) Math.round(amount * table[clamp(tier, table.length)]);
    }

    public static int clamp(int tier, int length) {
        return Math.max(0, Math.min(length - 1, tier));
    }
}
