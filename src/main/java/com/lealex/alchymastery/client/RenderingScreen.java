package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity;
import com.lealex.alchymastery.block.entity.RenderingCoreBlockEntity;
import com.lealex.alchymastery.menu.RenderingMenu;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

/**
 * Themed GUI (tools/gui_textures.py) on the vanilla furnace frame: distortion fluid tank (left), the essence slot, a progress arrow,
 * the liquid experience tank (right) and two drink buttons; the linked player under the tanks. Hover a tank for
 * its amount, the arrow for the wormhole status.
 */
public class RenderingScreen extends AbstractContainerScreen<RenderingMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("alchymastery", "textures/gui/rendering.png");

    private static final int BACKGROUND = 0xFFC6C6C6, TANK_BACK = 0x55101018, LABEL = GuiSprites.RENDERING_LABEL;
    private static final int LINK_OK = GuiSprites.RENDERING_OK, LINK_BAD = GuiSprites.RENDERING_BAD;
    private static final int TANK_Y = 17, TANK_W = 16, TANK_H = 52;
    private static final int FLUID_X = 20, XP_X = 112, ARROW_X = 79, ARROW_Y = 34;
    private static final int BUTTON_X = 134, BUTTON_W = 36, BUTTON_H = 16;

    public RenderingScreen(RenderingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("1 lvl"), b -> press(RenderingMenu.BUTTON_DRINK_LEVEL))
                .bounds(leftPos + BUTTON_X, topPos + TANK_Y + 2, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.literal("Drink one level of the stored experience")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("All"), b -> press(RenderingMenu.BUTTON_DRINK_ALL))
                .bounds(leftPos + BUTTON_X, topPos + TANK_Y + 22, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.literal("Drink all the stored experience")))
                .build());
    }

    private void press(int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        com.lealex.alchyx.client.UpgradeTab.draw(graphics, menu, x, y); // behind the window: its border covers the tab's edge
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256); // themed background: tools/gui_textures.py
        GuiDecor.draw(graphics, GuiSprites.RENDERING_ANIM, x, y, menu.getProgress() > 0); // the machine's animated vignette

        drawTank(graphics, x + FLUID_X, y + TANK_Y, menu.getDistortionFluid(), RenderingCoreBlockEntity.DISTORTION_CAPACITY, ModRegistries.DISTORTION_FLUID_COLOR);
        drawTank(graphics, x + XP_X, y + TANK_Y, menu.getExperienceFluid(), RenderingCoreBlockEntity.EXPERIENCE_CAPACITY, ModRegistries.LIQUID_EXPERIENCE_COLOR);

        int width = Mth.ceil(menu.getProgress() * 24.0F);
        if (width > 0) GuiDecor.arrow(graphics, GUI_TEXTURE, GuiSprites.RENDERING_ARROW, x + ARROW_X, y + ARROW_Y, width);
        GuiDecor.overlays(graphics, GUI_TEXTURE, GuiSprites.RENDERING_OVERLAYS, x, y); // the tanks' glass, over the fills
        com.lealex.alchyx.client.UpgradeTab.hints(graphics, menu, x, y, mouseX, mouseY); // empty upgrade slots: their kind
    }

    private static void drawTank(GuiGraphicsExtractor graphics, int x, int y, int amount, int capacity, int color) {
        graphics.fill(x, y, x + TANK_W, y + TANK_H, TANK_BACK);
        int filled = Math.round(TANK_H * Math.min(1F, amount / (float) capacity));
        if (filled > 0) graphics.fillGradient(x, y + TANK_H - filled, x + TANK_W, y + TANK_H, color, darker(color));
    }

    private static int darker(int color) {
        int r = (color >> 16 & 0xFF) * 3 / 4, g = (color >> 8 & 0xFF) * 3 / 4, b = (color & 0xFF) * 3 / 4;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        GuiDecor.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiSprites.RENDERING_LABEL);
        // Who the glass chamber is linked to (an experience siphon), right-aligned on the inventory label's line
        String linked = linkedName();
        String text = linked != null ? "Linked: " + linked : "No siphon linked";
        graphics.text(font, text, imageWidth - 8 - font.width(text), inventoryLabelY, linked != null ? LINK_OK : LABEL, false);
    }

    /** The linked player's name, read from the client's copy of the core (synced with it). */
    private String linkedName() {
        if (!menu.hasLinkedPlayer() || menu.getCorePos() == null || minecraft == null || minecraft.level == null) return null;
        if (minecraft.level.getBlockEntity(menu.getCorePos()) instanceof RenderingCoreBlockEntity core && !core.getLinkedName().isEmpty()) {
            return core.getLinkedName();
        }
        return "?";
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        NumberFormat format = NumberFormat.getIntegerInstance();
        if (isHovering(FLUID_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.literal("Distortion fluid: " + format.format(menu.getDistortionFluid())
                    + " / " + format.format(RenderingCoreBlockEntity.DISTORTION_CAPACITY) + " mB"), mouseX, mouseY);
        } else if (isHovering(XP_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            int mb = menu.getExperienceFluid();
            graphics.setTooltipForNextFrame(Component.literal("Liquid experience: " + format.format(mb) + " / "
                    + format.format(RenderingCoreBlockEntity.EXPERIENCE_CAPACITY) + " mB (" + format.format(mb / ModRegistries.MB_PER_XP) + " points)"), mouseX, mouseY);
        } else if (isHovering(ARROW_X, ARROW_Y, 24, 16, mouseX, mouseY)) {
            String status = switch (menu.getLinkState()) {
                case PoweredCoreBlockEntity.LINK_OK -> "Wormhole: " + format.format(menu.getAvailableEnergy()) + " DE";
                case PoweredCoreBlockEntity.LINK_UNREACHABLE -> "Wormhole: chamber unreachable";
                default -> "No wormhole link";
            };
            graphics.setTooltipForNextFrame(Component.literal(status + ", 1 essence = 100 mB + 10 DE"), mouseX, mouseY);
        }
    }

    // The upgrade tab right of the window: clicks there are inside the screen (no dropping the carried item)
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return super.hasClickedOutside(mx, my, xo, yo) && !com.lealex.alchyx.client.UpgradeTab.contains(menu, mx, my, xo, yo);
    }
}
