package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity;
import com.lealex.alchymastery.block.entity.ReconstructionCoreBlockEntity;
import com.lealex.alchymastery.menu.ReconstructionMenu;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

/**
 * Themed GUI (tools/gui_textures.py) on the vanilla furnace texture: compounds where the furnace's input is, the base block where its
 * fuel goes, the result on the right, a distortion fluid tank on the left and the wormhole status. Hover the tank
 * for its amount and the arrow for what the current recipe needs.
 */
public class ReconstructionScreen extends AbstractContainerScreen<ReconstructionMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("alchymastery", "textures/gui/reconstruction.png");

    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int TANK_BACK = 0x55101018; // a darkening over the themed tank well
    private static final int LINK_OK = GuiSprites.RECONSTRUCTION_OK;
    private static final int LINK_BAD = GuiSprites.RECONSTRUCTION_BAD;
    private static final int TANK_X = 20, TANK_Y = 17, TANK_W = 16, TANK_H = 52;
    private static final int ARROW_X = 79, ARROW_Y = 34;

    public ReconstructionScreen(ReconstructionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        com.lealex.alchyx.client.UpgradeTab.draw(graphics, menu, x, y); // behind the window: its border covers the tab's edge
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256); // themed background: tools/gui_textures.py
        GuiDecor.draw(graphics, GuiSprites.RECONSTRUCTION_ANIM, x, y, menu.getProgress() > 0); // the machine's animated vignette

        // Distortion fluid tank
        int tx = x + TANK_X, ty = y + TANK_Y;
        graphics.fill(tx, ty, tx + TANK_W, ty + TANK_H, TANK_BACK);
        int filled = Math.round(TANK_H * Math.min(1F, menu.getFluid() / (float) ReconstructionCoreBlockEntity.TANK_CAPACITY));
        if (filled > 0) {
            int color = ModRegistries.DISTORTION_FLUID_COLOR;
            graphics.fillGradient(tx, ty + TANK_H - filled, tx + TANK_W, ty + TANK_H, color, darker(color));
        }
        // A mark at the amount the current recipe needs
        int needed = menu.getNeededFluid();
        if (needed > 0) {
            int markY = ty + TANK_H - Math.round(TANK_H * Math.min(1F, needed / (float) ReconstructionCoreBlockEntity.TANK_CAPACITY));
            graphics.fill(tx, markY, tx + TANK_W, markY + 1, 0xC0FFFFFF);
        }

        int width = Mth.ceil(menu.getProgress() * 24.0F);
        if (width > 0) {
            GuiDecor.arrow(graphics, GUI_TEXTURE, GuiSprites.RECONSTRUCTION_ARROW, x + ARROW_X, y + ARROW_Y, width);
        }
        GuiDecor.overlays(graphics, GUI_TEXTURE, GuiSprites.RECONSTRUCTION_OVERLAYS, x, y); // the tanks' glass, over the fills
        com.lealex.alchyx.client.UpgradeTab.hints(graphics, menu, x, y, mouseX, mouseY); // empty upgrade slots: their kind
    }

    private static int darker(int color) {
        int r = (color >> 16 & 0xFF) * 3 / 4, g = (color >> 8 & 0xFF) * 3 / 4, b = (color & 0xFF) * 3 / 4;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        GuiDecor.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiSprites.RECONSTRUCTION_LABEL);
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
        graphics.text(font, status, 80, 60, color, false); // under the arrow and the result
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        NumberFormat format = NumberFormat.getIntegerInstance();
        if (isHovering(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.literal("Distortion fluid: " + format.format(menu.getFluid()) + " / "
                    + format.format(ReconstructionCoreBlockEntity.TANK_CAPACITY) + " mB"), mouseX, mouseY);
        } else if (isHovering(ARROW_X, ARROW_Y, 24, 16, mouseX, mouseY)) {
            String text = menu.getNeededCompounds() > 0
                    ? "Needs " + menu.getNeededCompounds() + " compound" + (menu.getNeededCompounds() > 1 ? "s" : "")
                      + ", 1 base block and " + format.format(menu.getNeededFluid()) + " mB"
                    : "Compounds on top; base block below: stone = raw ore, cobblestone = ingot or gem, deepslate = ore";
            graphics.setTooltipForNextFrame(Component.literal(text), mouseX, mouseY);
        }
    }

    // The upgrade tab right of the window: clicks there are inside the screen (no dropping the carried item)
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return super.hasClickedOutside(mx, my, xo, yo) && !com.lealex.alchyx.client.UpgradeTab.contains(menu, mx, my, xo, yo);
    }
}
