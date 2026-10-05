package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchymastery.compound.CompoundMaterial;
import com.lealex.alchymastery.compound.CompoundMaterials;
import com.lealex.alchymastery.compound.Transmutation;
import com.lealex.alchymastery.item.CompoundItem;
import com.lealex.alchymastery.menu.TransmutationMenu;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import com.lealex.alchyx.multiblock.MultiblockPatterns;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
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
import org.jspecify.annotations.Nullable;

/**
 * The transmutation chamber's core: turns input compounds into the target compound (a ghost slot), priced by tier.
 * Each operation takes as many inputs as needed to pay for at least one target (80% of their value counts unless
 * input and target are worth the same), keeps the leftover value in a buffer, and makes as many targets as the
 * buffer pays for. The DE (5 per copper of value made) is drawn when the operation starts, so the animation never
 * pauses halfway.
 *
 * Animation data synced to clients: the input and output stacks (shown on the shelves), the target (shown on the
 * core), and the current operation (start time, items taken and made) for the flight between the shelves.
 */
public class TransmutationCoreBlockEntity extends PoweredCoreBlockEntity implements MenuProvider {

    /** Its chamber's blocks switch with its own void particle (registry/ModParticles). */
    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return com.lealex.alchymastery.registry.ModParticles.VOID_GLYPH.get();
    }
    public static final int INPUT = 0, OUTPUT = 1, SLOT_COUNT = 2;
    public static final int DATA_COUNT = 6;
    private static final int MAX_TAKEN_PER_OPERATION = 64;

    private final ItemStacksResourceHandler inventory = createInventory(this::onInventoryChanged);
    private final ResourceHandler<ItemResource> automation = new AutomationHandler(inventory);
    // The ghost target: a copy of the compound the player wants (never a real item)
    private final SimpleContainer target = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            onInventoryChanged();
        }
    };

    private long valueBuffer = 0; // hundredths of an abundant compound, kept between operations

    // Current operation (opStart < 0: none)
    private long opStart = -1;
    private int opTicks = Transmutation.OPERATION_TICKS; // this operation's length (Speed upgrade), saved and synced
    private ItemStack opInput = ItemStack.EMPTY;  // one of the inputs taken, for the animation
    private ItemStack opResult = ItemStack.EMPTY; // one of the targets made
    private int opTaken = 0;
    private int opMade = 0;

    /** Client-only animation state of the book floating on the core (see BookAnimation). */
    public final BookAnimation book = new BookAnimation();
    /** Height of the target / flight ring above the core block's bottom (above the book). */
    public static final double RING_HEIGHT = 1.5;
    private static final double BOOK_HEIGHT = 0.9;

    // Display spots (pattern anchors), found when the structure forms; synced
    private @Nullable BlockPos inputShelf, outputShelf;

    private final ContainerData guiData = new ContainerData() {
        @Override
        public int get(int index) {
            long energy = availableEnergy();
            return switch (index) {
                case 0 -> opStart >= 0 && level != null ? (int) Math.min(opTicks, level.getGameTime() - opStart) : 0;
                case 1 -> opTicks;
                case 2 -> linkState();
                case 3 -> (int) (energy & 0xFFFF);
                case 4 -> (int) ((energy >>> 16) & 0xFFFF);
                case 5 -> (int) Math.min(Short.MAX_VALUE, valueBuffer); // hundredths of copper
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

    /** Input accepts compounds only; the output is filled by the machine. */
    public static ItemStacksResourceHandler createInventory(Runnable onChanged) {
        return new ItemStacksResourceHandler(SLOT_COUNT) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                return index == INPUT && resource.is(ModRegistries.COMPOUND.get());
            }

            @Override
            protected void onContentsChanged(int index, ItemStack previousContents) {
                onChanged.run();
            }
        };
    }

    public TransmutationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.TRANSMUTATION_CORE_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return ModPatterns.TRANSMUTATION_CHAMBER;
    }

    @Override
    protected String machineName() {
        return "Transmutation chamber";
    }

    @Override
    public ResourceHandler<ItemResource> getItemHandler() {
        return automation;
    }

    private void onInventoryChanged() {
        setChanged();
        syncToClients(); // the renderer shows the shelves and the target
    }

    // ---- Accessors for the menu and the renderer ----

    public SimpleContainer getTargetContainer() {
        return target;
    }

    public ItemStack getInputStack() {
        return inventory.getResource(INPUT).toStack(inventory.getAmountAsInt(INPUT));
    }

    public ItemStack getOutputStack() {
        return inventory.getResource(OUTPUT).toStack(inventory.getAmountAsInt(OUTPUT));
    }

    public ItemStack getTargetStack() {
        return target.getItem(0);
    }

    public long getOpStart() { return opStart; }

    /** The current operation's length in ticks (the renderer's animation follows it). */
    public int getOpTicks() { return opTicks; }
    public ItemStack getOpInput() { return opInput; }
    public ItemStack getOpResult() { return opResult; }
    public int getOpTaken() { return opTaken; }
    public int getOpMade() { return opMade; }
    public @Nullable BlockPos getInputShelf() { return inputShelf; }
    public @Nullable BlockPos getOutputShelf() { return outputShelf; }

    // ---- Client: book animation and enchanting glyphs ----

    /**
     * Client: the book turns and flips its pages; while transmuting, enchanting glyphs rise from it into the
     * spinning compounds, and a few drift toward the target the rest of the time.
     */
    @Override
    public void clientTick() {
        super.clientTick(); // the energy stream from the distortion chamber
        if (level == null || !isFormed()) return;
        boolean transmuting = opStart >= 0;
        book.tick(level, worldPosition, transmuting);
        if (level.getNearestPlayer(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 32, false) == null) return;

        var random = level.getRandom();
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        double ringY = worldPosition.getY() + RING_HEIGHT, bookY = worldPosition.getY() + BOOK_HEIGHT;
        int count;
        if (transmuting) {
            float progress = (level.getGameTime() - opStart) / (float) opTicks;
            // Strongest while the compounds spin over the core and change into the target
            count = progress > 0.3F && progress < 0.7F ? 4 : 1;
        } else {
            count = !target.getItem(0).isEmpty() && random.nextInt(8) == 0 ? 1 : 0;
        }
        for (int i = 0; i < count; i++) {
            // Enchant glyphs travel from (position + offset) into the position: from the book up into the ring
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = transmuting ? 0.25 : 0.05;
            double endX = cx + Math.cos(angle) * radius, endZ = cz + Math.sin(angle) * radius;
            level.addParticle(ParticleTypes.ENCHANT, endX, ringY, endZ,
                    cx + (random.nextDouble() - 0.5) * 0.4 - endX, bookY - ringY, cz + (random.nextDouble() - 0.5) * 0.4 - endZ);
        }
    }

    // ---- Structure ----

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        super.onStructureChanged(nowFormed);
        refreshAnchors();
    }

    private void refreshAnchors() {
        MultiblockPattern pattern = pattern();
        if (pattern == null) return;
        inputShelf = pattern.anchor(worldPosition, rotation(), "input_shelf");
        outputShelf = pattern.anchor(worldPosition, rotation(), "output_shelf");
        setChanged();
        syncToClients();
    }

    // ---- Processing ----

    @Override
    protected void tickFormed() {
        if (level == null) return;
        long now = level.getGameTime();
        if (now % 40 == 0) {
            if (getWormholePos() == null) refreshWormhole();
            if (getSourceEmitter() == null && getEnergySource() != null) refreshEmitter();
            if (inputShelf == null) refreshAnchors();
        }

        if (opStart >= 0) {
            long elapsed = now - opStart;
            if (elapsed >= opTicks) {
                finishOperation();
            } else {
                // The moment the compounds turn into the target (halfway, like the renderer)
                if (elapsed == opTicks / 2) {
                    playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                    playSound(worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
                }
                return; // still animating
            }
        }
        setWorking(tryStartOperation(now));
    }

    private boolean tryStartOperation(long now) {
        ItemStack targetStack = target.getItem(0);
        Identifier targetMaterial = targetStack.get(ModRegistries.MATERIAL.get());
        CompoundMaterial targetData = targetMaterial == null ? null : CompoundMaterials.get(targetMaterial);
        if (targetData == null) return false;
        long targetValue = Transmutation.valueOf(targetMaterial);
        if (targetValue <= 0) return false;

        ItemStack inputStack = getInputStack();
        Identifier inputMaterial = inputStack.get(ModRegistries.MATERIAL.get());
        CompoundMaterial inputData = inputMaterial == null ? null : CompoundMaterials.get(inputMaterial);
        if (inputData != null && inputMaterial.equals(targetMaterial)) return false; // nothing to transmute
        if (inputData != null && !Transmutation.compatible(inputMaterial, targetMaterial)) return false; // mineral <-> mob

        // How many inputs pay for at least one target (the buffer may already hold part of it)
        int taken = 0;
        long buffer = valueBuffer;
        if (buffer < targetValue) {
            if (inputData == null) return false;
            long each = Transmutation.effectiveValue(Transmutation.valueOf(inputMaterial), targetValue,
                    com.lealex.alchymastery.upgrade.Upgrades.TRANSMUTATION_LOSS[productivity()]); // Productivity upgrade
            if (each <= 0) return false;
            long needed = (targetValue - buffer + each - 1) / each;
            if (needed > inputStack.getCount() || needed > MAX_TAKEN_PER_OPERATION) return false; // wait for more
            taken = (int) needed;
            buffer += taken * each;
        }

        ItemStack result = CompoundItem.create(targetMaterial, targetData, 1);
        int made = (int) Math.min(buffer / targetValue, roomFor(result));
        if (made <= 0) return false; // output full

        long energy = upgradedEnergy(Transmutation.ENERGY_PER_VALUE * (targetValue / Transmutation.UNIT) * made);
        if (availableEnergy() < energy) return false;
        drawEnergy(energy);

        if (taken > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                if (inventory.extract(INPUT, inventory.getResource(INPUT), taken, tx) != taken) return false;
                tx.commit();
            }
        }
        valueBuffer = buffer - made * targetValue;
        opStart = now;
        opTicks = upgradedTicks(Transmutation.OPERATION_TICKS); // Speed upgrade
        opInput = taken > 0 ? inputStack.copyWithCount(1) : ItemStack.EMPTY;
        opResult = result;
        opTaken = taken;
        opMade = made;
        setChanged();
        syncToClients();
        playSound(worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        return true;
    }

    private void finishOperation() {
        if (level == null) return;
        int room = roomFor(opResult);
        int delivered = Math.min(room, opMade);
        if (delivered > 0) {
            int current = inventory.getResource(OUTPUT).isEmpty() ? 0 : inventory.getAmountAsInt(OUTPUT);
            inventory.set(OUTPUT, ItemResource.of(opResult), current + delivered);
        }
        // (the room was checked when the operation started and only the machine fills the output)
        playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.3f);
        opStart = -1;
        opInput = ItemStack.EMPTY;
        opResult = ItemStack.EMPTY;
        opTaken = 0;
        opMade = 0;
        setChanged();
        syncToClients();
    }

    /** How many of this compound the output slot can still take. */
    private int roomFor(ItemStack stack) {
        ItemResource current = inventory.getResource(OUTPUT);
        if (current.isEmpty()) return stack.getMaxStackSize();
        if (!current.equals(ItemResource.of(stack))) return 0;
        return stack.getMaxStackSize() - inventory.getAmountAsInt(OUTPUT);
    }

    // ---- GUI ----

    @Override
    public void openGui(Player player) {
        // Also before forming: the GUI then offers AlchyX's structure preview (ghost blocks of what's missing)
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchymastery.transmutation_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TransmutationMenu(containerId, playerInventory, inventory, target, guiData, this);
    }

    // ---- Breaking the core drops its items (and an operation in progress) ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) return;
        for (ItemStack stack : inventory.copyToList()) {
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        if (opStart >= 0 && !opResult.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, opResult.copyWithCount(opMade));
        }
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        output.store("target", ItemStack.OPTIONAL_CODEC, target.getItem(0));
        output.putLong("valueBuffer", valueBuffer);
        output.putLong("opStart", opStart);
        output.putInt("opTicks", opTicks);
        output.store("opInput", ItemStack.OPTIONAL_CODEC, opInput);
        output.store("opResult", ItemStack.OPTIONAL_CODEC, opResult);
        output.putInt("opTaken", opTaken);
        output.putInt("opMade", opMade);
        if (inputShelf != null) output.store("inputShelf", BlockPos.CODEC, inputShelf);
        if (outputShelf != null) output.store("outputShelf", BlockPos.CODEC, outputShelf);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        target.setItem(0, input.read("target", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        valueBuffer = input.getLongOr("valueBuffer", 0L);
        opStart = input.getLongOr("opStart", -1L);
        opTicks = Math.max(1, input.getIntOr("opTicks", Transmutation.OPERATION_TICKS));
        opInput = input.read("opInput", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        opResult = input.read("opResult", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        opTaken = input.getIntOr("opTaken", 0);
        opMade = input.getIntOr("opMade", 0);
        inputShelf = input.read("inputShelf", BlockPos.CODEC).orElse(null);
        outputShelf = input.read("outputShelf", BlockPos.CODEC).orElse(null);
    }

    /** Pipes/hoppers: insert only into the input, extract only from the output. */
    private record AutomationHandler(ItemStacksResourceHandler inner) implements ResourceHandler<ItemResource> {
        @Override public int size() { return inner.size(); }
        @Override public ItemResource getResource(int index) { return inner.getResource(index); }
        @Override public long getAmountAsLong(int index) { return inner.getAmountAsLong(index); }
        @Override public long getCapacityAsLong(int index, ItemResource resource) { return inner.getCapacityAsLong(index, resource); }
        @Override public boolean isValid(int index, ItemResource resource) { return index == INPUT && inner.isValid(index, resource); }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index == INPUT ? inner.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index == OUTPUT ? inner.extract(index, resource, amount, transaction) : 0;
        }
    }
}
