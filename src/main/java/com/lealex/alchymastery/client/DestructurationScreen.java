package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity;
import com.lealex.alchymastery.menu.DestructurationMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

/**
 * Themed GUI (tools/gui_textures.py) on the vanilla furnace texture: input on the left, progress arrow, main output (the furnace's
 * result slot), an extra byproduct slot, and the wormhole link status underneath.
 */
public class DestructurationScreen extends AbstractContainerScreen<DestructurationMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("alchymastery", "textures/gui/destructuration.png");

    private static final int BACKGROUND = 0xFFC6C6C6; // vanilla GUI gray
    private static final int LABEL = GuiSprites.DESTRUCTURATION_LABEL;
    private static final int LINK_OK = GuiSprites.DESTRUCTURATION_OK;     // distortion purple, darker for text
    private static final int LINK_BAD = GuiSprites.DESTRUCTURATION_BAD;

    public DestructurationScreen(DestructurationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        com.lealex.alchyx.client.UpgradeTab.draw(graphics, menu, x, y); // behind the window: its border covers the tab's edge
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256); // themed background: tools/gui_textures.py
        GuiDecor.draw(graphics, GuiSprites.DESTRUCTURATION_ANIM, x, y, menu.getProgress() > 0); // the machine's animated vignette

        // Hide the furnace's input slot, flame and fuel slot, then draw our input slot in the middle row
        // Byproduct slot, right of the main output

        // Progress arrow
        int width = Mth.ceil(menu.getProgress() * 24.0F);
        if (width > 0) {
            GuiDecor.arrow(graphics, GUI_TEXTURE, GuiSprites.DESTRUCTURATION_ARROW, x + 79, y + 34, width);
        }
        GuiDecor.overlays(graphics, GUI_TEXTURE, GuiSprites.DESTRUCTURATION_OVERLAYS, x, y); // the tanks' glass, over the fills
        com.lealex.alchyx.client.UpgradeTab.hints(graphics, menu, x, y, mouseX, mouseY); // empty upgrade slots: their kind
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        GuiDecor.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiSprites.DESTRUCTURATION_LABEL);
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
        graphics.text(font, status, 8, 60, color, false);
    }

    // The upgrade tab right of the window: clicks there are inside the screen (no dropping the carried item)
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return super.hasClickedOutside(mx, my, xo, yo) && !com.lealex.alchyx.client.UpgradeTab.contains(menu, mx, my, xo, yo);
    }
}
