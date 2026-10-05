package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
import com.lealex.alchymastery.menu.DistortionMatrixMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

/**
 * Themed GUI (tools/gui_textures.py) built on the vanilla furnace texture: the fuel slot and flame stay,
 * the furnace's input/output slots are painted over, and an energy bar replaces them.
 */
public class DistortionMatrixScreen extends AbstractContainerScreen<DistortionMatrixMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("alchymastery", "textures/gui/distortion_matrix.png");

    private static final int BACKGROUND = 0xFFC6C6C6;   // vanilla GUI gray
    private static final int LABEL = GuiSprites.MATRIX_LABEL;        // vanilla label color
    private static final int BAR_BORDER = 0xFF373737;
    private static final int BAR_EMPTY = 0xFF1E1E1E;
    private static final int BAR_FILL = 0xFF9B4DFF;     // distortion purple

    private static final int BAR_X = 80, BAR_Y = 38, BAR_WIDTH = 88, BAR_HEIGHT = 10;

    public DistortionMatrixScreen(DistortionMatrixMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        com.lealex.alchyx.client.UpgradeTab.draw(graphics, menu, x, y); // behind the window: its border covers the tab's edge
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256); // themed background: tools/gui_textures.py
        GuiDecor.draw(graphics, GuiSprites.MATRIX_ANIM, x, y, menu.getBurnTicksLeft() > 0); // the machine's animated vignette

        // Flame above the fuel slot while a fuel item is burning
        int burn = menu.getBurnTicksLeft();
        int total = Math.max(1, menu.getBurnTicksTotal());
        GuiDecor.flame(graphics, GUI_TEXTURE, GuiSprites.MATRIX_FLAME, x + 56, y + 36, burn / (float) total);

        // Energy bar
        double ratio = menu.getEnergy() / (double) DistortionMatrixBlockEntity.MAX_ENERGY;
        int filled = (int) Math.ceil(ratio * BAR_WIDTH);
        if (menu.getEnergy() > 0 && filled > 0) {
            graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + filled, y + BAR_Y + BAR_HEIGHT, BAR_FILL);
        }
        GuiDecor.overlays(graphics, GUI_TEXTURE, GuiSprites.MATRIX_OVERLAYS, x, y); // the tanks' glass, over the fills
        com.lealex.alchyx.client.UpgradeTab.hints(graphics, menu, x, y, mouseX, mouseY); // empty upgrade slots: their kind
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        GuiDecor.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiSprites.MATRIX_LABEL);
        String energy = NumberFormat.getIntegerInstance().format(menu.getEnergy()) + " DE";
        graphics.text(font, energy, BAR_X, BAR_Y - 11, LABEL, false);

        String state = switch (menu.getChamberState()) {
            case 2 -> "Chamber formed";
            case 1 -> "Chamber forming...";
            default -> "Chamber incomplete";
        };
        graphics.text(font, state, BAR_X, BAR_Y + BAR_HEIGHT + 4, LABEL, false);
    }

    // The upgrade tab right of the window: clicks there are inside the screen (no dropping the carried item)
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return super.hasClickedOutside(mx, my, xo, yo) && !com.lealex.alchyx.client.UpgradeTab.contains(menu, mx, my, xo, yo);
    }
}
