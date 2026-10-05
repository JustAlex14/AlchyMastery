package com.lealex.alchymastery.menu;

import net.minecraft.core.BlockPos;
import com.lealex.alchyx.menu.CoreMenu;
import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
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
 * The distortion matrix's menu: the server side holds the real fuel slot and matrix,
 * the client side gets copies that Minecraft keeps in sync.
 */
public class DistortionMatrixMenu extends AbstractContainerMenu implements CoreMenu, com.lealex.alchyx.menu.UpgradeMenu {
    private @Nullable BlockPos corePos; // the core's position (also on the client: read from the menu data)

    public static final int FUEL_SLOT = 0;
    private static final int INVENTORY_START = 1;
    private static final int INVENTORY_END = 37; // 27 inventory + 9 hotbar slots

    private final ItemStacksResourceHandler fuel;
    private final ContainerData data;
    private final @Nullable DistortionMatrixBlockEntity matrix; // null on the client

    /** Client side: called when the server tells the client to open the menu. */
    public DistortionMatrixMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, DistortionMatrixBlockEntity.createFuelHandler(() -> {}),
                new SimpleContainerData(DistortionMatrixBlockEntity.DATA_COUNT), null);
        this.corePos = extraData.readBlockPos();
    }

    /** Server side: built by the matrix with its real fuel slot and synced values. */
    public DistortionMatrixMenu(int containerId, Inventory inventory, ItemStacksResourceHandler fuel,
                                ContainerData data, @Nullable DistortionMatrixBlockEntity matrix) {
        super(ModRegistries.DISTORTION_MATRIX_MENU.get(), containerId);
        this.corePos = matrix == null ? null : matrix.getBlockPos();
        this.fuel = fuel;
        this.data = data;
        this.matrix = matrix;

        addSlot(new ResourceHandlerSlot(fuel, fuel::set, 0, 56, 53)); // the furnace's fuel slot position
        addStandardInventorySlots(inventory, 8, 84);
        // The upgrade tab: Productivity and Efficiency only
        upgradeStart = slots.size();
        java.util.List<com.lealex.alchyx.upgrade.UpgradeType> upgradeTypes = com.lealex.alchymastery.upgrade.AlchymasteryUpgrades.MATRIX;
        com.lealex.alchyx.menu.UpgradeSlot.addAll(this::addSlot, matrix != null ? matrix.getUpgrades().handler()
                : com.lealex.alchyx.upgrade.UpgradeStorage.createHandler(upgradeTypes, () -> {}), upgradeTypes);
        upgradeEnd = slots.size();
        addDataSlots(data);
    }

    // ---- Values for the screen (read from the synced data) ----

    public long getEnergy() {
        // Minecraft syncs menu values as 16-bit numbers, so the energy travels in two halves
        return (data.get(0) & 0xFFFFL) | ((data.get(1) & 0xFFFFL) << 16);
    }

    public int getBurnTicksLeft() {
        return data.get(2);
    }

    public int getBurnTicksTotal() {
        return data.get(4);
    }

    /** 0 = not formed, 1 = forming, 2 = formed. */
    public int getChamberState() {
        return data.get(3);
    }

    // ---- Menu behavior ----

    @Override
    public @Nullable BlockPos getCorePos() {
        return corePos;
    }

    @Override
    public boolean stillValid(Player player) {
        if (matrix == null) return true; // the client trusts the server
        return !matrix.isRemoved()
                && matrix.withinReach(player);
    }

    private int upgradeStart, upgradeEnd; // the upgrade tab's slots

    @Override
    public int upgradeStart() {
        return upgradeStart;
    }

    @Override
    public int upgradeEnd() {
        return upgradeEnd;
    }

    /** Shift-click: lapis goes into the fuel slot, the fuel slot goes back to the inventory; upgrades to their slot. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index >= upgradeStart && index < upgradeEnd) { // an upgrade back to the inventory
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) return ItemStack.EMPTY;
            slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
            return original;
        }
        if (index < upgradeStart && stack.getItem() instanceof com.lealex.alchyx.upgrade.UpgradeItem
                && moveItemStackTo(stack, upgradeStart, upgradeEnd, false)) {
            slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
            return original;
        }

        if (index == FUEL_SLOT) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) return ItemStack.EMPTY;
        } else {
            if (!DistortionMatrixBlockEntity.isFuel(ItemResource.of(stack))) return ItemStack.EMPTY;
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = fuel.insert(ItemResource.of(stack), stack.getCount(), tx);
                if (inserted == 0) return ItemStack.EMPTY;
                tx.commit();
                stack.shrink(inserted);
            }
        }

        slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        return original;
    }
}
