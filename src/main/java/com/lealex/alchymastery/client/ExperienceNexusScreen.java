package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.ExperienceNexusBlockEntity;
import com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity;
import com.lealex.alchymastery.menu.ExperienceNexusMenu;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

/**
 * Themed GUI (tools/gui_textures.py) on the vanilla furnace frame: water and distortion fluid tanks (left), the input slot, the
 * rendering progress arrow, the liquid experience tank (right) with the drink buttons, and one small bar per stage
 * (destructure, condense, render) under the slot. Hover for details.
 */
public class ExperienceNexusScreen extends AbstractContainerScreen<ExperienceNexusMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("alchymastery", "textures/gui/experience_nexus.png");

    private static final int BACKGROUND = 0xFFC6C6C6, TANK_BACK = 0x55101018, LABEL = GuiSprites.EXPERIENCE_NEXUS_LABEL;
    private static final int WATER_COLOR = 0xFF3F76E4, LINK_OK = GuiSprites.EXPERIENCE_NEXUS_OK, LINK_BAD = GuiSprites.EXPERIENCE_NEXUS_BAD;
    private static final int TANK_Y = 17, TANK_H = 52;
    private static final int WATER_X = 14, DISTORTION_X = 34, XP_X = 110, ARROW_X = 79, ARROW_Y = 34;
    private static final int BUTTON_X = 132, BUTTON_W = 38, BUTTON_H = 16;
    private static final int BARS_X = 56, BARS_Y = 62, BAR_W = 14, BAR_GAP = 4;
    // Stage colors: nether red, prismarine teal, experience green
    private static final int[] STAGE_COLORS = {0xFFB0402A, 0xFF4FB3C8, 0xFF8BF23E};
    private static final String[] STAGE_NAMES = {"Destructure", "Condense", "Render"};

    public ExperienceNexusScreen(ExperienceNexusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("1 lvl"), b -> press(ExperienceNexusMenu.BUTTON_DRINK_LEVEL))
                .bounds(leftPos + BUTTON_X, topPos + TANK_Y + 2, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.literal("Drink one level of the stored experience")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("All"), b -> press(ExperienceNexusMenu.BUTTON_DRINK_ALL))
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
        GuiDecor.draw(graphics, GuiSprites.EXPERIENCE_NEXUS_ANIM, x, y, menu.isStageWorking(0) || menu.isStageWorking(1) || menu.isStageWorking(2)); // the machine's animated vignette

        drawTank(graphics, x + WATER_X, y + TANK_Y, 16, menu.getWater() / (float) ExperienceNexusBlockEntity.WATER_CAPACITY, WATER_COLOR);
        drawTank(graphics, x + DISTORTION_X, y + TANK_Y, 8, menu.getDistortion() / (float) ExperienceNexusBlockEntity.DISTORTION_CAPACITY,
                ModRegistries.DISTORTION_FLUID_COLOR);
        drawTank(graphics, x + XP_X, y + TANK_Y, 16, menu.getExperience() / (float) ExperienceNexusBlockEntity.EXPERIENCE_CAPACITY,
                ModRegistries.LIQUID_EXPERIENCE_COLOR);

        // The rendering progress on the arrow (that's what fills the experience tank)
        int width = (int) Math.ceil(menu.getStageProgress(ExperienceNexusBlockEntity.RENDER) * 24);
        if (width > 0) GuiDecor.arrow(graphics, GUI_TEXTURE, GuiSprites.EXPERIENCE_NEXUS_ARROW, x + ARROW_X, y + ARROW_Y, width);

        for (int stage = 0; stage < ExperienceNexusBlockEntity.STAGES; stage++) {
            int bx = x + BARS_X + stage * (BAR_W + BAR_GAP), by = y + BARS_Y;
            graphics.fill(bx, by, bx + BAR_W, by + 4, TANK_BACK);
            int filled = Math.round(BAR_W * menu.getStageProgress(stage));
            if (filled > 0) graphics.fill(bx, by, bx + filled, by + 4, STAGE_COLORS[stage]);
            if (!menu.isStageWorking(stage)) graphics.fill(bx, by, bx + BAR_W, by + 4, 0x40101018); // dimmed when idle
        }
        if (menu.isInputRefused()) graphics.fill(x + 56, y + 35, x + 72, y + 51, 0x60AA2222); // not a mob drop
        GuiDecor.overlays(graphics, GUI_TEXTURE, GuiSprites.EXPERIENCE_NEXUS_OVERLAYS, x, y); // the tanks' glass, over the fills
        com.lealex.alchyx.client.UpgradeTab.hints(graphics, menu, x, y, mouseX, mouseY); // empty upgrade slots: their kind
    }

    private static void drawTank(GuiGraphicsExtractor graphics, int x, int y, int width, float fraction, int color) {
        graphics.fill(x, y, x + width, y + TANK_H, TANK_BACK);
        int filled = Math.round(TANK_H * Math.min(1F, fraction));
        if (filled > 0) graphics.fill(x, y + TANK_H - filled, x + width, y + TANK_H, color);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        GuiDecor.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiSprites.EXPERIENCE_NEXUS_LABEL);
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
        graphics.text(font, status, imageWidth - 8 - font.width(status), titleLabelY, color, false);
        String linked = linkedName();
        String text = linked != null ? "Linked: " + linked : "No siphon linked";
        graphics.text(font, text, imageWidth - 8 - font.width(text), inventoryLabelY, linked != null ? LINK_OK : LABEL, false);
    }

    private String linkedName() {
        if (!menu.hasLinkedPlayer() || menu.getCorePos() == null || minecraft == null || minecraft.level == null) return null;
        if (minecraft.level.getBlockEntity(menu.getCorePos()) instanceof ExperienceNexusBlockEntity core && !core.getLinkedName().isEmpty()) {
            return core.getLinkedName();
        }
        return "?";
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        NumberFormat format = NumberFormat.getIntegerInstance();
        if (isHovering(WATER_X, TANK_Y, 16, TANK_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.literal("Water: " + format.format(menu.getWater()) + " / "
                    + format.format(ExperienceNexusBlockEntity.WATER_CAPACITY) + " mB"), mouseX, mouseY);
        } else if (isHovering(DISTORTION_X, TANK_Y, 8, TANK_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.literal("Distortion fluid: " + format.format(menu.getDistortion()) + " / "
                    + format.format(ExperienceNexusBlockEntity.DISTORTION_CAPACITY) + " mB"), mouseX, mouseY);
        } else if (isHovering(XP_X, TANK_Y, 16, TANK_H, mouseX, mouseY)) {
            int mb = menu.getExperience();
            graphics.setTooltipForNextFrame(Component.literal("Liquid experience: " + format.format(mb) + " / "
                    + format.format(ExperienceNexusBlockEntity.EXPERIENCE_CAPACITY) + " mB (" + format.format(mb / ModRegistries.MB_PER_XP)
                    + " points)"), mouseX, mouseY);
        } else if (isHovering(ARROW_X, ARROW_Y, 24, 16, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.literal("Essences waiting: " + menu.getStoredEssences() + " / "
                    + ExperienceNexusBlockEntity.STORE_CAPACITY + " · 1 essence = 100 mB + 10 DE"), mouseX, mouseY);
        } else if (isHovering(56, 35, 16, 16, mouseX, mouseY) && menu.isInputRefused()) {
            graphics.setTooltipForNextFrame(Component.literal("Only mob drops and essences can be rendered"), mouseX, mouseY);
        } else if (isHovering(56, 35, 16, 16, mouseX, mouseY) && menu.slots.get(0).getItem().isEmpty()) {
            graphics.setTooltipForNextFrame(Component.literal("Mob drops (rotten flesh, bones, string...) or essences"), mouseX, mouseY);
        } else {
            for (int stage = 0; stage < ExperienceNexusBlockEntity.STAGES; stage++) {
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
