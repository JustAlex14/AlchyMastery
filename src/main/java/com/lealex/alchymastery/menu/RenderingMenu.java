package com.lealex.alchymastery.menu;

import com.lealex.alchymastery.block.entity.RenderingCoreBlockEntity;
import com.lealex.alchymastery.registry.ModRegistries;
import com.lealex.alchyx.menu.CoreMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The rendering cauldron's menu: the essence slot, tanks and progress (synced values), and two buttons handled on
 * the server through clickMenuButton: 0 = drink one level, 1 = drink everything.
 */
public class RenderingMenu extends AbstractContainerMenu implements CoreMenu, com.lealex.alchyx.menu.UpgradeMenu {
    public static final int BUTTON_DRINK_LEVEL = 0, BUTTON_DRINK_ALL = 1;

    private @Nullable BlockPos corePos;
    private final ItemStacksResourceHandler inventory;
    private final ContainerData data;
    private final @Nullable RenderingCoreBlockEntity core; // null on the client

    /** Client side. */
    public RenderingMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, RenderingCoreBlockEntity.createInventory(() -> {}),
                new SimpleContainerData(RenderingCoreBlockEntity.DATA_COUNT), null);
        this.corePos = extraData.readBlockPos();
    }

    /** Server side. */
    public RenderingMenu(int containerId, Inventory playerInventory, ItemStacksResourceHandler inventory,
                         ContainerData data, @Nullable RenderingCoreBlockEntity core) {
        super(ModRegistries.RENDERING_MENU.get(), containerId);
        this.corePos = core == null ? null : core.getBlockPos();
        this.inventory = inventory;
        this.data = data;
        this.core = core;
        addSlot(new ResourceHandlerSlot(inventory, inventory::set, RenderingCoreBlockEntity.INPUT, 56, 35));
        addStandardInventorySlots(playerInventory, 8, 84);
        // The upgrade tab, right of the window (PoweredCoreBlockEntity: one slot per kind; Parallel on the nexuses)
        upgradeStart = slots.size();
        java.util.List<com.lealex.alchyx.upgrade.UpgradeType> upgradeTypes =
                com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity.upgradeTypes(false);
        com.lealex.alchyx.menu.UpgradeSlot.addAll(this::addSlot, core != null ? core.getUpgrades().handler()
                : com.lealex.alchyx.upgrade.UpgradeStorage.createHandler(upgradeTypes, () -> {}), upgradeTypes);
        upgradeEnd = slots.size();
        addDataSlots(data);
    }

    // ---- Values for the screen ----

    public float getProgress() {
        int total = data.get(1);
        return total <= 0 ? 0 : Math.min(1.0F, data.get(0) / (float) total);
    }

    public int getLinkState() {
        return data.get(2);
    }

    public long getAvailableEnergy() {
        return (data.get(3) & 0xFFFFL) | ((data.get(4) & 0xFFFFL) << 16);
    }

    public int getDistortionFluid() {
        return data.get(5) & 0xFFFF;
    }

    public int getExperienceFluid() {
        return (data.get(6) & 0xFFFF) | ((data.get(7) & 0xFFFF) << 16);
    }

    public boolean hasLinkedPlayer() {
        return data.get(8) != 0;
    }

    @Override
    public @Nullable BlockPos getCorePos() {
        return corePos;
    }

    // ---- Buttons (server side) ----

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (core == null) return false;
        if (buttonId == BUTTON_DRINK_LEVEL || buttonId == BUTTON_DRINK_ALL) {
            core.drink(player, buttonId == BUTTON_DRINK_ALL);
            return true;
        }
        return false;
    }

    // ---- Menu behavior ----

    @Override
    public boolean stillValid(Player player) {
        if (core == null) return true;
        return !core.isRemoved() && core.withinReach(player);
    }

    /** Shift-click: the essence slot goes back to the inventory; essences from the inventory go into the slot. */
    private int upgradeStart, upgradeEnd; // the upgrade tab's slots

    @Override
    public int upgradeStart() {
        return upgradeStart;
    }

    @Override
    public int upgradeEnd() {
        return upgradeEnd;
    }

    /** The upgrade tab's part of shift-click: upgrades go into their slot, and back to the inventory. Null: not handled. */
    private @Nullable ItemStack quickMoveUpgrade(Slot slot, int index, ItemStack stack, ItemStack original) {
        if (index >= upgradeStart && index < upgradeEnd) {
            if (!moveItemStackTo(stack, upgradeStart - 36, upgradeStart, true)) return ItemStack.EMPTY;
        } else if (index < upgradeStart && stack.getItem() instanceof com.lealex.alchyx.upgrade.UpgradeItem) {
            if (!moveItemStackTo(stack, upgradeStart, upgradeEnd, false)) return null; // its slot is taken: the usual rules
        } else {
            return null;
        }
        slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        return original;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        ItemStack upgrade = quickMoveUpgrade(slot, index, stack, original);
        if (upgrade != null) return upgrade;
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = inventory.insert(RenderingCoreBlockEntity.INPUT, ItemResource.of(stack), stack.getCount(), tx);
                if (inserted == 0) return ItemStack.EMPTY;
                tx.commit();
                stack.shrink(inserted);
            }
        }
        slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        return original;
    }
}
