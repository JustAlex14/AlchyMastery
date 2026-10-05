package com.lealex.alchymastery.menu;

import net.minecraft.core.BlockPos;
import com.lealex.alchyx.menu.CoreMenu;
import com.lealex.alchymastery.block.entity.CondensatorCoreBlockEntity;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The condensator's menu: no item slots (fluids go in and out with buckets or pipes), just the two tank levels,
 * the batch progress and the wormhole link, plus the player's inventory.
 */
public class CondensatorMenu extends AbstractContainerMenu implements CoreMenu, com.lealex.alchyx.menu.UpgradeMenu {
    private @Nullable BlockPos corePos; // the core's position (also on the client: read from the menu data)


    private final ContainerData data;
    private final @Nullable CondensatorCoreBlockEntity core; // null on the client

    /** Client side. */
    public CondensatorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainerData(CondensatorCoreBlockEntity.DATA_COUNT), null);
        this.corePos = extraData.readBlockPos();
    }

    /** Server side. */
    public CondensatorMenu(int containerId, Inventory playerInventory, ContainerData data, @Nullable CondensatorCoreBlockEntity core) {
        super(ModRegistries.CONDENSATOR_MENU.get(), containerId);
        this.corePos = core == null ? null : core.getBlockPos();
        this.data = data;
        this.core = core;
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

    public int getWater() {
        return data.get(5) & 0xFFFF;
    }

    public int getDistortionFluid() {
        return data.get(6) & 0xFFFF;
    }

    // ---- Menu behavior ----

    @Override
    public @Nullable BlockPos getCorePos() {
        return corePos;
    }

    @Override
    public boolean stillValid(Player player) {
        if (core == null) return true;
        return !core.isRemoved()
                && core.withinReach(player);
    }

    /** No machine slots: shift-click only moves upgrades. */
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
        ItemStack moved = quickMoveUpgrade(slot, index, stack, stack.copy());
        return moved != null ? moved : ItemStack.EMPTY; // no machine slots: only the upgrades move
    }
}
