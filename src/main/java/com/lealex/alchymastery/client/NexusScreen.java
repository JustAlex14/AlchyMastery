package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.NexusCoreBlockEntity;
import com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity;
import com.lealex.alchymastery.menu.NexusMenu;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

/**
 * Themed GUI (tools/gui_textures.py) on the vanilla furnace texture: input where the furnace's input is, the target ghost slot where
 * its fuel goes, the result on the right, a water tank on the left, a thin distortion fluid gauge on the right,
 * and one small bar per stage (destructure, transmute, condense, reconstruct) under the arrow. Hover for details.
 */
public class NexusScreen extends AbstractContainerScreen<NexusMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("alchymastery", "textures/gui/alchemical_nexus.png");

    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int TANK_BACK = 0x55101018; // a darkening over the themed tank well
    private static final int WATER_COLOR = 0xFF3F76E4;
    private static final int LINK_OK = GuiSprites.ALCHEMICAL_NEXUS_OK;
    private static final int LINK_BAD = GuiSprites.ALCHEMICAL_NEXUS_BAD;
    private static final int WATER_X = 20, FLUID_X = 154, TANK_Y = 17, TANK_H = 52;
    private static final int BARS_X = 80, BARS_Y = 62, BAR_W = 14, BAR_GAP = 4;
    // Stage colors: nether red, warped green, prismarine teal, cherry pink
    private static final int[] STAGE_COLORS = {0xFFB0402A, 0xFF2FA088, 0xFF4FB3C8, 0xFFE58FB8};
    private static final String[] STAGE_NAMES = {"Destructure", "Transmute", "Condense", "Reconstruct"};

    public NexusScreen(NexusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        com.lealex.alchyx.client.UpgradeTab.draw(graphics, menu, x, y); // behind the window: its border covers the tab's edge
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256); // themed background: tools/gui_textures.py
        GuiDecor.draw(graphics, GuiSprites.ALCHEMICAL_NEXUS_ANIM, x, y, menu.isStageWorking(0) || menu.isStageWorking(1) || menu.isStageWorking(2) || menu.isStageWorking(3)); // the machine's animated vignette

        drawTank(graphics, x + WATER_X, y + TANK_Y, 16, menu.getWater() / (float) NexusCoreBlockEntity.WATER_CAPACITY, WATER_COLOR);
        drawTank(graphics, x + FLUID_X, y + TANK_Y, 8, menu.getFluid() / (float) NexusCoreBlockEntity.FLUID_CAPACITY,
                ModRegistries.DISTORTION_FLUID_COLOR);

        // The reconstruction progress on the big arrow (that's what fills the output)
        int width = (int) Math.ceil(menu.getStageProgress(NexusCoreBlockEntity.RECONSTRUCT) * 24);
        if (width > 0) GuiDecor.arrow(graphics, GUI_TEXTURE, GuiSprites.ALCHEMICAL_NEXUS_ARROW, x + 79, y + 34, width);

        // One bar per stage
        for (int stage = 0; stage < NexusCoreBlockEntity.STAGES; stage++) {
            int bx = x + BARS_X + stage * (BAR_W + BAR_GAP), by = y + BARS_Y;
            graphics.fill(bx, by, bx + BAR_W, by + 4, TANK_BACK);
            int filled = Math.round(BAR_W * menu.getStageProgress(stage));
            if (filled > 0) graphics.fill(bx, by, bx + filled, by + 4, STAGE_COLORS[stage]);
            if (!menu.isStageWorking(stage)) graphics.fill(bx, by, bx + BAR_W, by + 4, 0x40101018); // dimmed when idle
        }
        GuiDecor.overlays(graphics, GUI_TEXTURE, GuiSprites.ALCHEMICAL_NEXUS_OVERLAYS, x, y); // the tanks' glass, over the fills
        com.lealex.alchyx.client.UpgradeTab.hints(graphics, menu, x, y, mouseX, mouseY); // empty upgrade slots: their kind
    }

    private static void drawTank(GuiGraphicsExtractor graphics, int x, int y, int width, float fraction, int color) {
        graphics.fill(x, y, x + width, y + TANK_H, TANK_BACK);
        int filled = Math.round(TANK_H * Math.min(1F, fraction));
        if (filled > 0) graphics.fill(x, y + TANK_H - filled, x + width, y + TANK_H, color);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        GuiDecor.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiSprites.ALCHEMICAL_NEXUS_LABEL);
        String status;
        int color;
        switch (menu.getLinkState()) {
            case PoweredCoreBlockEntity.LINK_OK -> {
                status = NumberFormat.getIntegerInstance().format(menu.getAvailableEnergy()) + " DE";
                color = LINK_OK;
            }
            case PoweredCoreBlockEntity.LINK_UNREACHABLE -> {
                status = "Unreachable";
                color = LINK_BAD;
            }
            default -> {
                status = "No wormhole";
                color = LINK_BAD;
            }
        }
        graphics.text(font, status, imageWidth - 8 - font.width(status), titleLabelY, color, false); // right of the title
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        NumberFormat format = NumberFormat.getIntegerInstance();
        if (isHovering(WATER_X, TANK_Y, 16, TANK_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.literal("Water: " + format.format(menu.getWater()) + " / "
                    + format.format(NexusCoreBlockEntity.WATER_CAPACITY) + " mB"), mouseX, mouseY);
        } else if (isHovering(FLUID_X, TANK_Y, 8, TANK_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.literal("Distortion fluid: " + format.format(menu.getFluid()) + " / "
                    + format.format(NexusCoreBlockEntity.FLUID_CAPACITY) + " mB"), mouseX, mouseY);
        } else if (isHovering(56, 53, 16, 16, mouseX, mouseY) && menu.slots.get(NexusMenu.TARGET_SLOT).getItem().isEmpty()) {
            graphics.setTooltipForNextFrame(Component.literal("Target: click with the item to make (ingot, raw ore, gem, ore)"), mouseX, mouseY);
        } else if (isHovering(79, 34, 24, 16, mouseX, mouseY)) {
            String text = menu.getNeededCompounds() > 0
                    ? "Target compounds: " + menu.getTargetCompounds() + " (needs " + menu.getNeededCompounds() + " per item), "
                      + menu.getStoredCompounds() + " / " + NexusCoreBlockEntity.STORE_CAPACITY + " stored"
                    : "No target set (or it can't be made)";
            graphics.setTooltipForNextFrame(Component.literal(text), mouseX, mouseY);
        } else {
            for (int stage = 0; stage < NexusCoreBlockEntity.STAGES; stage++) {
                if (isHovering(BARS_X + stage * (BAR_W + BAR_GAP), BARS_Y, BAR_W, 4, mouseX, mouseY)) {
                    String state = menu.isStageWorking(stage) ? Math.round(menu.getStageProgress(stage) * 100) + "%" : "idle";
                    graphics.setTooltipForNextFrame(Component.literal(STAGE_NAMES[stage] + ": " + state), mouseX, mouseY);
                }
            }
        }
    }

    // The upgrade tab right of the window: clicks there are inside the screen (no dropping the carried item)
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return super.hasClickedOutside(mx, my, xo, yo) && !com.lealex.alchyx.client.UpgradeTab.contains(menu, mx, my, xo, yo);
    }
}
