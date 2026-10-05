package com.lealex.alchymastery.compat.jei;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** What each AlchyMastery JEI page shows (plain data, built from the material files by AlchymasteryJeiPlugin). */
public final class JeiDisplays {
    private JeiDisplays() {}

    /** Destructuration: any of these items -> compounds. */
    public record Destructuration(List<ItemStack> inputs, ItemStack compounds, int energy, int ticks) {}

    /** Transmutation: input compounds -> output compounds (the smallest whole exchange, 20% loss included). */
    public record Transmutation(ItemStack input, ItemStack output, long energy) {}

    /** Reconstruction: compounds + one of the base blocks + distortion fluid -> an item. */
    public record Reconstruction(ItemStack compounds, List<ItemStack> bases, int fluid, ItemStack output, int energy, int ticks) {}

    /** Condensation: water + DE -> distortion fluid (one fixed recipe). */
    public record Condensation(int water, int fluid, long energy, int ticks) {}

    /** Rendering: one mob essence + distortion fluid + DE -> liquid experience (points x 20 mB). */
    public record Rendering(ItemStack essence, int fluid, int experience, int points, int energy, int ticks) {}
}
