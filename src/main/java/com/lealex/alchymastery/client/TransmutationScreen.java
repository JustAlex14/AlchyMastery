package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity;
import com.lealex.alchymastery.item.CompoundItem;
import com.lealex.alchymastery.menu.TransmutationMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;

/**
 * Themed GUI (tools/gui_textures.py) on the vanilla furnace texture: input on top-left, the target ghost slot where the furnace's fuel
 * slot was, progress arrow, output; wormhole energy and the value buffer on the right.
 */
public class TransmutationScreen extends AbstractContainerScreen<TransmutationMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("alchymastery", "textures/gui/transmutation.png");

    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int LABEL = GuiSprites.TRANSMUTATION_LABEL;
    private static final int GOOD = GuiSprites.TRANSMUTATION_OK;
    private static final int BAD = GuiSprites.TRANSMUTATION_BAD;
    private static final int TARGET_TINT = 0x406A2FB8; // faint purple over the ghost slot

    public TransmutationScreen(TransmutationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        com.lealex.alchyx.client.UpgradeTab.draw(graphics, menu, x, y); // behind the window: its border covers the tab's edge
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256); // themed background: tools/gui_textures.py
        GuiDecor.draw(graphics, GuiSprites.TRANSMUTATION_ANIM, x, y, menu.getProgress() > 0); // the machine's animated vignette

        // Hide the flame; the fuel slot becomes the target ghost slot (tinted so it reads as special)
        graphics.fill(x + 56, y + 53, x + 72, y + 69, TARGET_TINT);

        int width = Mth.ceil(menu.getProgress() * 24.0F);
        if (width > 0) {
            GuiDecor.arrow(graphics, GUI_TEXTURE, GuiSprites.TRANSMUTATION_ARROW, x + 79, y + 34, width);
        }
        GuiDecor.overlays(graphics, GUI_TEXTURE, GuiSprites.TRANSMUTATION_OVERLAYS, x, y); // the tanks' glass, over the fills
        com.lealex.alchyx.client.UpgradeTab.hints(graphics, menu, x, y, mouseX, mouseY); // empty upgrade slots: their kind
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        GuiDecor.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiSprites.TRANSMUTATION_LABEL);
        graphics.text(font, "Target", 20, 57, LABEL, false);

        String status;
        int color;
        switch (menu.getLinkState()) {
            case PoweredCoreBlockEntity.LINK_OK -> {
                status = NumberFormat.getIntegerInstance().format(menu.getAvailableEnergy()) + " DE";
                color = GOOD;
            }
            case PoweredCoreBlockEntity.LINK_UNREACHABLE -> {
                status = "Chamber unreachable";
                color = BAD;
            }
            default -> {
                status = "No wormhole link";
                color = BAD;
            }
        }
        graphics.text(font, status, 80, 58, color, false);

        // Compounds only transmute within their family (iron can't become rotten flesh): say so instead of idling
        ItemStack input = menu.getSlot(0).getItem(), wanted = menu.getSlot(1).getItem();
        if (!input.isEmpty() && !wanted.isEmpty()) {
            String from = CompoundItem.familyOf(input), to = CompoundItem.familyOf(wanted);
            if (!from.equals(to)) {
                Component text = Component.translatable("gui.alchymastery.transmutation.incompatible",
                        familyName(from), familyName(to));
                graphics.text(font, text, 80, 22, BAD, false);
            }
        }
    }

    private static Component familyName(String family) {
        return Component.translatableWithFallback("family.alchymastery." + family,
                Character.toUpperCase(family.charAt(0)) + family.substring(1));
    }

    // The upgrade tab right of the window: clicks there are inside the screen (no dropping the carried item)
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return super.hasClickedOutside(mx, my, xo, yo) && !com.lealex.alchyx.client.UpgradeTab.contains(menu, mx, my, xo, yo);
    }
}
