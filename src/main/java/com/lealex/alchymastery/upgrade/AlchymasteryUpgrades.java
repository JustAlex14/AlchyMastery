package com.lealex.alchymastery.upgrade;

import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchyx.upgrade.UpgradeType;
import com.lealex.alchyx.upgrade.UpgradeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * AlchyMastery's machine upgrades, registered with AlchyX's upgrade system (items, tab, displays, corruption veins).
 * What they do is in {@link Upgrades} and the machines' code; colors tint the corruption veins; emblems are in
 * textures/gui/upgrade_emblems.png (tools/upgrade_tab.py).
 */
public final class AlchymasteryUpgrades {
    private AlchymasteryUpgrades() {}

    private static final Identifier EMBLEMS = Identifier.fromNamespaceAndPath(Alchymastery.MODID, "textures/gui/upgrade_emblems.png");

    /** Faster operations, each costing a little more energy. */
    public static final UpgradeType SPEED = UpgradeTypes.register(new UpgradeType(id("speed"), 4, 0x3FD6E0,
            new UpgradeType.Emblem(EMBLEMS, 0, 0), (tier, lines) -> {
                lines.accept(line(String.format("x%s speed", trim(Upgrades.SPEED[tier])), ChatFormatting.GREEN));
                lines.accept(line(String.format("+%d%% energy per operation", Math.round((Upgrades.SPEED_ENERGY[tier] - 1) * 100)), ChatFormatting.RED));
                lines.accept(line(String.format("Distortion chamber: burns x%s faster, -%d%% DE per fuel", trim(Upgrades.SPEED[tier]),
                        Math.round((1 - 1 / Upgrades.MATRIX_SPEED_FUEL[tier]) * 100)), ChatFormatting.GRAY));
            }));

    /** More from the same input (a different gain on each machine). */
    public static final UpgradeType PRODUCTIVITY = UpgradeTypes.register(new UpgradeType(id("productivity"), 4, 0x8BF23E,
            new UpgradeType.Emblem(EMBLEMS, 16, 0), (tier, lines) -> {
                lines.accept(line("Distortion chamber: +" + Math.round((Upgrades.MATRIX_ENERGY[tier] - 1) * 100) + "% DE per fuel", ChatFormatting.GREEN));
                lines.accept(line("Destructuration: " + Math.round(Upgrades.BYPRODUCT_CHANCE[tier] * 100) + "% byproduct chance", ChatFormatting.GREEN));
                lines.accept(line("Transmutation: " + Upgrades.TRANSMUTATION_LOSS[tier] + "% loss", ChatFormatting.GREEN));
                lines.accept(line("Reconstruction: -" + Math.round((1 - Upgrades.RECONSTRUCTION_FLUID[tier]) * 100) + "% distortion fluid", ChatFormatting.GREEN));
                lines.accept(line("Condensation: +" + Math.round((Upgrades.CONDENSATOR_FLUID[tier] - 1) * 100) + "% distortion fluid", ChatFormatting.GREEN));
                lines.accept(line("Rendering: +" + Math.round((Upgrades.RENDERING_EXPERIENCE[tier] - 1) * 100) + "% experience", ChatFormatting.GREEN));
            }));

    /** Less energy per operation (the distortion chamber: fuel that isn't always used up). */
    public static final UpgradeType EFFICIENCY = UpgradeTypes.register(new UpgradeType(id("efficiency"), 3, 0x9B4DFF,
            new UpgradeType.Emblem(EMBLEMS, 32, 0), (tier, lines) -> {
                lines.accept(line(String.format("-%d%% energy per operation", Math.round((1 - Upgrades.EFFICIENCY[tier]) * 100)), ChatFormatting.GREEN));
                lines.accept(line("Distortion chamber: " + Math.round(Upgrades.MATRIX_FUEL_SAVE[tier] * 100) + "% chance to keep the fuel", ChatFormatting.GREEN));
            }));

    /** Nexus only: every stage takes the time of its fastest one, so no stage clogs waiting for another. */
    public static final UpgradeType PARALLEL = UpgradeTypes.register(new UpgradeType(id("parallel"), 1, 0xFFE15A,
            new UpgradeType.Emblem(EMBLEMS, 48, 0), (tier, lines) -> {
                lines.accept(line("Every stage of a nexus takes the time of its fastest one:", ChatFormatting.GREEN));
                lines.accept(line("no stage waits for another", ChatFormatting.GREEN));
                lines.accept(line("Alchemical and experience nexus only", ChatFormatting.DARK_PURPLE));
            }));

    /** In item and tab order. */
    public static final List<UpgradeType> ALL = List.of(SPEED, PRODUCTIVITY, EFFICIENCY, PARALLEL);
    /** What each machine takes. */
    public static final List<UpgradeType> MACHINE = List.of(SPEED, PRODUCTIVITY, EFFICIENCY);
    public static final List<UpgradeType> NEXUS = List.of(SPEED, PRODUCTIVITY, EFFICIENCY, PARALLEL);
    public static final List<UpgradeType> MATRIX = List.of(SPEED, PRODUCTIVITY, EFFICIENCY);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Alchymastery.MODID, path);
    }

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

}
