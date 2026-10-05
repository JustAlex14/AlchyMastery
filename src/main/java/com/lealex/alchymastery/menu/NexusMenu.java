package com.lealex.alchymastery.menu;

import net.minecraft.core.BlockPos;
import com.lealex.alchyx.menu.CoreMenu;
import com.lealex.alchymastery.block.entity.NexusCoreBlockEntity;
import com.lealex.alchymastery.compound.Reconstruction;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
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
 * The alchemical nexus menu: input (ores...), the target ghost slot (click it with the item you want, e.g. an iron
 * ingot; with an empty hand to clear it), the output, two tanks, the four stages and the wormhole link.
 */
public class NexusMenu extends AbstractContainerMenu implements CoreMenu, com.lealex.alchyx.menu.UpgradeMenu {
    private @Nullable BlockPos corePos; // the core's position (also on the client: read from the menu data)

    public static final int INPUT_SLOT = 0, TARGET_SLOT = 1, OUTPUT_SLOT = 2;
    private static final int MACHINE_SLOTS = 3;
    private static final int INVENTORY_END = MACHINE_SLOTS + 36;

    private final ItemStacksResourceHandler inventory;
    private final Container target;
    private final ContainerData data;
    private final @Nullable NexusCoreBlockEntity core; // null on the client

    /** Client side. */
    public NexusMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, NexusCoreBlockEntity.createInventory(() -> {}), new SimpleContainer(1),
                new SimpleContainerData(NexusCoreBlockEntity.DATA_COUNT), null);
        this.corePos = extraData.readBlockPos();
    }

    /** Server side. */
    public NexusMenu(int containerId, Inventory playerInventory, ItemStacksResourceHandler inventory, Container target,
                     ContainerData data, @Nullable NexusCoreBlockEntity core) {
        super(ModRegistries.ALCHEMICAL_NEXUS_MENU.get(), containerId);
        this.corePos = core == null ? null : core.getBlockPos();
        this.inventory = inventory;
        this.target = target;
        this.data = data;
        this.core = core;
        addSlot(new ResourceHandlerSlot(inventory, inventory::set, NexusCoreBlockEntity.INPUT, 56, 17));
        addSlot(new GhostSlot(target, 0, 56, 53));
        addSlot(new ResourceHandlerSlot(inventory, inventory::set, NexusCoreBlockEntity.OUTPUT, 116, 35));
        addStandardInventorySlots(playerInventory, 8, 84);
        // The upgrade tab, right of the window (PoweredCoreBlockEntity: one slot per kind; Parallel on the nexuses)
        upgradeStart = slots.size();
        java.util.List<com.lealex.alchyx.upgrade.UpgradeType> upgradeTypes =
                com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity.upgradeTypes(true);
        com.lealex.alchyx.menu.UpgradeSlot.addAll(this::addSlot, core != null ? core.getUpgrades().handler()
                : com.lealex.alchyx.upgrade.UpgradeStorage.createHandler(upgradeTypes, () -> {}), upgradeTypes);
        upgradeEnd = slots.size();
        addDataSlots(data);
    }

    // ---- Values for the screen ----

    /** 0..1 through a stage's current operation (NexusCoreBlockEntity.DESTRUCTURE...). */
    public float getStageProgress(int stage) {
        return Math.min(1F, data.get(stage) / 100F);
    }

    public boolean isStageWorking(int stage) {
        return (data.get(12) & (1 << stage)) != 0;
    }

    public int getLinkState() {
        return data.get(4);
    }

    public long getAvailableEnergy() {
        return (data.get(5) & 0xFFFFL) | ((data.get(6) & 0xFFFFL) << 16);
    }

    public int getWater() {
        return data.get(7) & 0xFFFF;
    }

    public int getFluid() {
        return data.get(8) & 0xFFFF;
    }

    /** Compounds of the target's material waiting for reconstruction. */
    public int getTargetCompounds() {
        return data.get(9);
    }

    public int getStoredCompounds() {
        return data.get(10);
    }

    /** Compounds one target item needs (0 = no valid target). */
    public int getNeededCompounds() {
        return data.get(11);
    }

    // ---- The ghost slot ----

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
        if (slotIndex == TARGET_SLOT) {
            ItemStack carried = getCarried();
            if (carried.isEmpty()) {
                target.setItem(0, ItemStack.EMPTY);
            } else if (core == null || Reconstruction.findTarget(ItemResource.of(carried)) != null) {
                // The server only accepts items the nexus can make (the client shows it right away; a refused
                // item is put back by the server's slot sync)
                target.setItem(0, carried.copyWithCount(1));
            }
            return; // the carried stack never moves
        }
        super.clicked(slotIndex, buttonNum, containerInput, player);
    }

    /** Shows the target; can't be filled or emptied by normal clicks (handled in clicked above). */
    private static class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
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

    /** Shift-click: input/output go to the inventory; anything from the inventory goes into the input. */
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
        if (index == TARGET_SLOT || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        ItemStack upgrade = quickMoveUpgrade(slot, index, stack, original);
        if (upgrade != null) return upgrade;
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, INVENTORY_END, true)) return ItemStack.EMPTY;
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = inventory.insert(NexusCoreBlockEntity.INPUT, ItemResource.of(stack), stack.getCount(), tx);
                if (inserted == 0) return ItemStack.EMPTY;
                tx.commit();
                stack.shrink(inserted);
            }
        }
        slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        return original;
    }
}
