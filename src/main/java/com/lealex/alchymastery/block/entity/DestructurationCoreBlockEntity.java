package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchymastery.compound.CompoundMaterials;
import com.lealex.alchymastery.item.CompoundItem;
import com.lealex.alchymastery.menu.DestructurationMenu;
import com.lealex.alchyx.multiblock.MultiblockPatterns;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The destructuration chamber's core: 1 input, 1 main output, 1 byproduct output (per the design spec),
 * powered through a wormhole link. Turns the input into compounds using the recipes from the material files
 * (data/<namespace>/compound_material/*.json), drawing the DE bit by bit from the linked distortion chamber.
 */
public class DestructurationCoreBlockEntity extends PoweredCoreBlockEntity implements MenuProvider {

    /** Its chamber's blocks switch with its own void particle (registry/ModParticles). */
    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return com.lealex.alchymastery.registry.ModParticles.VOID_EMBER.get();
    }
    public static final int INPUT = 0, OUTPUT = 1, BYPRODUCT = 2, SLOT_COUNT = 3;
    public static final int DATA_COUNT = 5; // values synced to an open GUI
    public static final float BYPRODUCT_CHANCE = 0.05f; // 5% before upgrades (design spec; Upgrades.BYPRODUCT_CHANCE)

    private final ItemStacksResourceHandler inventory = createInventory(this::onInventoryChanged);
    // What pipes and hoppers see through the parts: they may only insert into the input and only take outputs
    private final ResourceHandler<ItemResource> automation = new AutomationHandler(inventory);

    private int progress = 0;       // ticks done on the current item
    private int progressTotal = 0;  // ticks the current item needs

    // Recipe lookup cache: recomputed when the input item changes or the material files are reloaded
    private ItemResource cachedInput = ItemResource.EMPTY;
    private int cachedGeneration = -1;
    private CompoundMaterials.@Nullable Recipe cachedRecipe;

    // Animations (floating input, piston strikes, particles, sounds) are in
    // data/alchymastery/machine_animation/destructuration_chamber.json, played by AlchyX on the client.
    private boolean retractPending = true; // not saved: pistons left extended by older versions are closed once

    private final ContainerData guiData = new ContainerData() {
        @Override
        public int get(int index) {
            long energy = availableEnergy();
            return switch (index) {
                case 0 -> progress;
                case 1 -> progressTotal;
                case 2 -> linkState();
                case 3 -> (int) (energy & 0xFFFF);           // 16-bit halves, like the matrix GUI
                case 4 -> (int) ((energy >>> 16) & 0xFFFF);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /** The 3 slots; the player and pipes can only put items in the input (outputs are filled by the machine). */
    public static ItemStacksResourceHandler createInventory(Runnable onChanged) {
        return new ItemStacksResourceHandler(SLOT_COUNT) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                return index == INPUT;
            }

            @Override
            protected void onContentsChanged(int index, ItemStack previousContents) {
                onChanged.run();
            }
        };
    }

    public DestructurationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.DESTRUCTURATION_CORE_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return ModPatterns.DESTRUCTURATION_CHAMBER;
    }

    @Override
    protected String machineName() {
        return "Destructuration chamber";
    }

    @Override
    public ResourceHandler<ItemResource> getItemHandler() {
        return automation;
    }

    private void onInventoryChanged() {
        setChanged();
        setAnimationDisplay("input", getInputStack()); // the floating item and the crumbs of the animation file
    }

    /** A copy of the input stack, for the floating-item renderer. */
    public ItemStack getInputStack() {
        return inventory.getResource(INPUT).toStack(inventory.getAmountAsInt(INPUT));
    }

    // ---- Processing ----

    /** Every tick while formed: work on the input item, drawing energy as it goes, and drive the pistons. */
    @Override
    protected void tickFormed() {
        if (level == null) return;
        long now = level.getGameTime();
        // Structures formed before wormholes were tracked: find it now (cheap, every 2 seconds at most)
        if (now % 40 == 0) {
            if (getWormholePos() == null) refreshWormhole();
            if (getSourceEmitter() == null && getEnergySource() != null) refreshEmitter();
        }
        // Worlds saved by older versions mid-strike: close any piston left extended (strikes are client-only now)
        if (retractPending) {
            for (ChamberShellBlockEntity piston : pistonShells()) piston.endStrike();
            retractPending = false;
            setAnimationDisplay("input", getInputStack());
        }

        setWorking(work());
    }

    /** The shells of this structure that look like pistons. */
    private List<ChamberShellBlockEntity> pistonShells() {
        List<ChamberShellBlockEntity> pistons = new ArrayList<>();
        if (level == null || pattern() == null) return pistons;
        for (BlockPos pos : pattern().allPositions(worldPosition, rotation())) {
            if (level.getBlockEntity(pos) instanceof ChamberShellBlockEntity shell
                    && shell.getDisguise().getBlock() instanceof PistonBaseBlock) {
                pistons.add(shell);
            }
        }
        return pistons;
    }

    /** One tick of work; false when idle (no recipe, output full, not enough energy). */
    private boolean work() {
        ItemResource input = inventory.getResource(INPUT);
        CompoundMaterials.Recipe recipe = recipeFor(input);
        if (recipe == null) {
            resetProgress();
            return false;
        }
        ItemStack result = CompoundItem.create(recipe.material(), recipe.data(), recipe.compounds());
        if (!fits(OUTPUT, result)) return false; // output full: wait, keep the progress

        int ticks = upgradedTicks(recipe.ticks()); // Speed upgrade
        if (progressTotal != ticks) {
            progressTotal = ticks;
            progress = Math.min(progress, progressTotal);
        }

        // Spread the operation's DE over its ticks; with too little energy available, just wait
        long total = upgradedEnergy(recipe.energy()); // Speed and Efficiency upgrades
        long needed = total * (progress + 1) / progressTotal - total * progress / progressTotal;
        if (needed > 0) {
            if (availableEnergy() < needed) return false;
            drawEnergy(needed);
        }

        if (progress == 0) startAnimationOperation(progressTotal); // animation files: "when": "start"
        progress++;
        if (progress >= progressTotal) {
            finishItem(input, result, recipe);
            progress = 0;
        }
        setChanged();
        return true;
    }

    private void finishItem(ItemResource input, ItemStack result, CompoundMaterials.Recipe recipe) {
        if (level == null) return;
        try (Transaction tx = Transaction.openRoot()) {
            if (inventory.extract(INPUT, input, 1, tx) != 1) return;
            tx.commit();
        }
        add(OUTPUT, result);
        if (level.getRandom().nextFloat() < com.lealex.alchymastery.upgrade.Upgrades.BYPRODUCT_CHANCE[productivity()]) {
            ItemStack extra = CompoundItem.create(recipe.material(), recipe.data(), 1);
            if (fits(BYPRODUCT, extra)) add(BYPRODUCT, extra);
        }
        finishAnimationOperation(); // animation files: "when": "finish" (grindstone sound, distortion puff)
    }

    private CompoundMaterials.@Nullable Recipe recipeFor(ItemResource input) {
        if (!input.equals(cachedInput) || cachedGeneration != CompoundMaterials.generation()) {
            cachedInput = input;
            cachedGeneration = CompoundMaterials.generation();
            cachedRecipe = CompoundMaterials.find(input);
        }
        return cachedRecipe;
    }

    private void resetProgress() {
        if (progress != 0 || progressTotal != 0) {
            progress = 0;
            progressTotal = 0;
            setChanged();
        }
    }

    /** True if the whole stack fits in that slot (empty, or the same compound with room left). */
    private boolean fits(int slot, ItemStack stack) {
        ItemResource current = inventory.getResource(slot);
        if (current.isEmpty()) return true;
        return current.equals(ItemResource.of(stack))
                && inventory.getAmountAsInt(slot) + stack.getCount() <= stack.getMaxStackSize();
    }

    /** Adds the stack to an output slot (call fits() first). Outputs bypass isValid, so use set(). */
    private void add(int slot, ItemStack stack) {
        int amount = inventory.getResource(slot).isEmpty() ? 0 : inventory.getAmountAsInt(slot);
        inventory.set(slot, ItemResource.of(stack), amount + stack.getCount());
    }

    // ---- GUI (only once the structure is formed) ----

    @Override
    public void openGui(Player player) {
        // Also before forming: the GUI then offers AlchyX's structure preview (ghost blocks of what's missing)
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchymastery.destructuration_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DestructurationMenu(containerId, playerInventory, inventory, guiData, this);
    }

    // ---- Breaking the core drops what's inside ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) return;
        for (ItemStack stack : inventory.copyToList()) {
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        output.putInt("progress", progress);
        output.putInt("progressTotal", progressTotal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        progress = input.getIntOr("progress", 0);
        progressTotal = input.getIntOr("progressTotal", 0);
    }

    /** Pipes/hoppers view: insert only into the input, extract only from the two outputs. */
    private record AutomationHandler(ItemStacksResourceHandler inner) implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return inner.size();
        }

        @Override
        public ItemResource getResource(int index) {
            return inner.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return inner.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return inner.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return index == INPUT && inner.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index == INPUT ? inner.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index != INPUT ? inner.extract(index, resource, amount, transaction) : 0;
        }
    }
}
