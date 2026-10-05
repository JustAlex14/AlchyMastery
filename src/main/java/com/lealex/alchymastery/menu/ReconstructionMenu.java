package com.lealex.alchymastery.menu;

import net.minecraft.core.BlockPos;
import com.lealex.alchyx.menu.CoreMenu;
import com.lealex.alchymastery.block.entity.ReconstructionCoreBlockEntity;
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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The reconstruction chamber's menu: compounds (top), base block (bottom), output (right), the distortion fluid
 * tank, progress, the wormhole link and what the current recipe needs.
 */
public class ReconstructionMenu extends AbstractContainerMenu implements CoreMenu, com.lealex.alchyx.menu.UpgradeMenu {
    private @Nullable BlockPos corePos; // the core's position (also on the client: read from the menu data)

    private static final int MACHINE_SLOTS = ReconstructionCoreBlockEntity.SLOT_COUNT;
    private static final int INVENTORY_END = MACHINE_SLOTS + 36;

    private final ItemStacksResourceHandler inventory;
    private final ContainerData data;
    private final @Nullable ReconstructionCoreBlockEntity core; // null on the client

    /** Client side. */
    public ReconstructionMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, ReconstructionCoreBlockEntity.createInventory(() -> {}),
                new SimpleContainerData(ReconstructionCoreBlockEntity.DATA_COUNT), null);
        this.corePos = extraData.readBlockPos();
    }

    /** Server side. */
    public ReconstructionMenu(int containerId, Inventory playerInventory, ItemStacksResourceHandler inventory,
                              ContainerData data, @Nullable ReconstructionCoreBlockEntity core) {
        super(ModRegistries.RECONSTRUCTION_MENU.get(), containerId);
        this.corePos = core == null ? null : core.getBlockPos();
        this.inventory = inventory;
        this.data = data;
        this.core = core;
        // Same places as the furnace's input, fuel and result slots
        addSlot(new ResourceHandlerSlot(inventory, inventory::set, ReconstructionCoreBlockEntity.COMPOUNDS, 56, 17));
        addSlot(new ResourceHandlerSlot(inventory, inventory::set, ReconstructionCoreBlockEntity.BASE, 56, 53));
        addSlot(new ResourceHandlerSlot(inventory, inventory::set, ReconstructionCoreBlockEntity.OUTPUT, 116, 35));
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

    public int getFluid() {
        return data.get(5) & 0xFFFF;
    }

    /** Compounds the current recipe needs (0 = no recipe for these inputs). */
    public int getNeededCompounds() {
        return data.get(6) & 0xFFFF;
    }

    public int getNeededFluid() {
        return data.get(7) & 0xFFFF;
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

    /** Shift-click: machine slots go to the inventory; compounds go to the compound slot, blocks to the base slot. */
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

        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, INVENTORY_END, true)) return ItemStack.EMPTY;
        } else {
            int target = stack.is(ModRegistries.COMPOUND.get()) ? ReconstructionCoreBlockEntity.COMPOUNDS : ReconstructionCoreBlockEntity.BASE;
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = inventory.insert(target, ItemResource.of(stack), stack.getCount(), tx);
                if (inserted == 0) return ItemStack.EMPTY;
                tx.commit();
                stack.shrink(inserted);
            }
        }
        slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        return original;
    }
}
