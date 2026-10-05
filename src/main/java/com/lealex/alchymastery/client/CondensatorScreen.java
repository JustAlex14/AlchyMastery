package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.CondensatorCoreBlockEntity;
import com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity;
import com.lealex.alchymastery.menu.CondensatorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

/**
 * Themed GUI (tools/gui_textures.py): a water tank on the left, the distortion fluid tank on the right, a progress arrow between
 * them, and the wormhole link status. Hover a tank for its exact amount. Drawn with plain shapes on the vanilla
 * inventory background (real art comes with the Blockbench models).
 */
public class CondensatorScreen extends AbstractContainerScreen<CondensatorMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("alchymastery", "textures/gui/condensator.png");

    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int TANK_BACK = 0x55101018; // a darkening over the themed tank well
    private static final int TANK_BORDER_LIGHT = 0xFFFFFFFF;
    private static final int WATER_COLOR = 0xFF3F76E4;      // vanilla's default water color
    private static final int FLUID_COLOR = 0xFF9B4DFF;      // distortion fluid (same as its world tint)
    private static final int LINK_OK = GuiSprites.CONDENSATOR_OK;
    private static final int LINK_BAD = GuiSprites.CONDENSATOR_BAD;

    // Tank boxes, relative to the GUI's corner
    private static final int TANK_Y = 16, TANK_W = 18, TANK_H = 44;
    private static final int WATER_X = 52, FLUID_X = 106;

    public CondensatorScreen(CondensatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        com.lealex.alchyx.client.UpgradeTab.draw(graphics, menu, x, y); // behind the window: its border covers the tab's edge
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256); // themed background: tools/gui_textures.py
        GuiDecor.draw(graphics, GuiSprites.CONDENSATOR_ANIM, x, y, menu.getProgress() > 0); // the machine's animated vignette

        drawTank(graphics, x + WATER_X, y + TANK_Y, menu.getWater(), WATER_COLOR);
        drawTank(graphics, x + FLUID_X, y + TANK_Y, menu.getDistortionFluid(), FLUID_COLOR);

        int width = Mth.ceil(menu.getProgress() * 24.0F);
        if (width > 0) {
            GuiDecor.arrow(graphics, GUI_TEXTURE, GuiSprites.CONDENSATOR_ARROW, x + 77, y + 30, width);
        }
        GuiDecor.overlays(graphics, GUI_TEXTURE, GuiSprites.CONDENSATOR_OVERLAYS, x, y); // the tanks' glass, over the fills
        com.lealex.alchyx.client.UpgradeTab.hints(graphics, menu, x, y, mouseX, mouseY); // empty upgrade slots: their kind
    }

    /** A sunken box, filled from the bottom in proportion to the amount, with a tick mark every 1,000 mB. */
    private static void drawTank(GuiGraphicsExtractor graphics, int x, int y, int amount, int color) {
        graphics.fill(x, y, x + TANK_W, y + TANK_H, TANK_BACK);
        int filled = Math.round(TANK_H * Math.min(1F, amount / (float) CondensatorCoreBlockEntity.TANK_CAPACITY));
        if (filled > 0) {
            graphics.fillGradient(x, y + TANK_H - filled, x + TANK_W, y + TANK_H, color, darker(color));
        }
        int marks = CondensatorCoreBlockEntity.TANK_CAPACITY / 1000;
        for (int i = 1; i < marks; i++) {
            int markY = y + TANK_H - TANK_H * i / marks;
            graphics.fill(x, markY, x + (i % 4 == 0 ? 8 : 4), markY + 1, 0x80FFFFFF);
        }
    }

    private static int darker(int color) {
        int r = (color >> 16 & 0xFF) * 3 / 4, g = (color >> 8 & 0xFF) * 3 / 4, b = (color & 0xFF) * 3 / 4;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        GuiDecor.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiSprites.CONDENSATOR_LABEL);
        String status;
        int color;
        switch (menu.getLinkState()) {
            case PoweredCoreBlockEntity.LINK_OK -> {
                status = "Wormhole: " + NumberFormat.getIntegerInstance().format(menu.getAvailableEnergy()) + " DE";
                color = LINK_OK;
            }
            case PoweredCoreBlockEntity.LINK_UNREACHABLE -> {
                status = "Wormhole: chamber unreachable";
                color = LINK_BAD;
            }
            default -> {
                status = "No wormhole link";
                color = LINK_BAD;
            }
        }
        graphics.text(font, status, 8, 62, color, false); // between the tanks and the inventory label
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        NumberFormat format = NumberFormat.getIntegerInstance();
        String capacity = format.format(CondensatorCoreBlockEntity.TANK_CAPACITY);
        if (isHovering(WATER_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.literal("Water: " + format.format(menu.getWater()) + " / " + capacity + " mB"), mouseX, mouseY);
        } else if (isHovering(FLUID_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.literal("Distortion fluid: " + format.format(menu.getDistortionFluid()) + " / " + capacity + " mB"), mouseX, mouseY);
        }
    }

    // The upgrade tab right of the window: clicks there are inside the screen (no dropping the carried item)
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return super.hasClickedOutside(mx, my, xo, yo) && !com.lealex.alchyx.client.UpgradeTab.contains(menu, mx, my, xo, yo);
    }
}
