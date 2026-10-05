package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchyx.block.ChamberShellBlock;
import com.lealex.alchymastery.compound.CompoundMaterials;
import com.lealex.alchymastery.compound.Reconstruction;
import com.lealex.alchymastery.menu.ReconstructionMenu;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import com.lealex.alchyx.multiblock.MultiblockPatterns;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The reconstruction chamber's core: compounds + a base block + distortion fluid -> an ore, ingot or gem
 * (rules in {@link Reconstruction}), with DE from a linked distortion chamber.
 *
 * Slots: compounds, base block, output; one distortion fluid tank (buckets or pipes on any block).
 *
 * Looks: the cauldron holds distortion fluid and shows the tank; it simmers over its campfire. While rebuilding,
 * the compound and the base block orbit above the brewing stand and merge into the result (ReconstructionCoreRenderer),
 * glyphs fall from the spore blossom into them, and the brewing stand's bottles fill one by one.
 */
public class ReconstructionCoreBlockEntity extends PoweredCoreBlockEntity implements MenuProvider {

    /** Its chamber's blocks switch with its own void particle (registry/ModParticles). */
    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return com.lealex.alchymastery.registry.ModParticles.VOID_POLLEN.get();
    }
    public static final int COMPOUNDS = 0, BASE = 1, OUTPUT = 2, SLOT_COUNT = 3;
    public static final int TANK_CAPACITY = 4_000; // mB of distortion fluid
    public static final int DATA_COUNT = 8;
    /** Height of the floating items above the core block's bottom (renderer and particles). */
    public static final double ITEM_HEIGHT = 1.25;
    // Fly-towards particles (enchant) end this far below their target: aim higher
    private static final double PARTICLE_DROP = 1.2;
    private static final int SYNC_INTERVAL = 10; // ticks between progress syncs while working

    private final ItemStacksResourceHandler inventory = createInventory(this::onInventoryChanged);
    private final ResourceHandler<ItemResource> automation = new ItemAutomation(inventory);
    private final FluidStacksResourceHandler tank = createTank(this::onTankChanged);
    private final ResourceHandler<FluidResource> fluidAutomation = new FluidAutomation(tank);

    private int progress = 0, progressTotal = 0;
    private int displayProgress = 0; // client: advances every tick while working, corrected by each sync
    private ItemStack displayResult = ItemStack.EMPTY; // what the current recipe makes (synced, for the renderer)

    // Recipe cache: recomputed when the inputs change or the material files are reloaded
    private ItemResource cachedCompound = ItemResource.EMPTY, cachedBase = ItemResource.EMPTY;
    private int cachedGeneration = -1;
    private Reconstruction.@Nullable Recipe cachedRecipe;

    // Display spots (pattern anchors), found when the structure forms; synced so the client can draw effects
    // flower: the spore blossom under the wormhole, where the work particles come from
    private @Nullable BlockPos cauldron, campfire, flower;
    private int cauldronLevel = -1; // what the cauldron shell shows (0 = empty .. 3 = full); -1 = unknown

    private final ContainerData guiData = new ContainerData() {
        @Override
        public int get(int index) {
            long energy = availableEnergy();
            Reconstruction.Recipe recipe = currentRecipe();
            return switch (index) {
                case 0 -> progress;
                case 1 -> progressTotal;
                case 2 -> linkState();
                case 3 -> (int) (energy & 0xFFFF);           // 16-bit halves, like the other GUIs
                case 4 -> (int) ((energy >>> 16) & 0xFFFF);
                case 5 -> tank.getAmountAsInt(0);             // up to 4,000: fits in 16 bits
                case 6 -> recipe == null ? 0 : recipe.compounds();
                case 7 -> recipe == null ? 0 : fluidFor(recipe);
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

    /** Compound slot: compounds only; base slot: any block; output: filled by the machine (set()). */
    public static ItemStacksResourceHandler createInventory(Runnable onChanged) {
        return new ItemStacksResourceHandler(SLOT_COUNT) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                return switch (index) {
                    case COMPOUNDS -> resource.is(ModRegistries.COMPOUND.get());
                    case BASE -> resource.getItem() instanceof BlockItem;
                    default -> false;
                };
            }

            @Override
            protected void onContentsChanged(int index, ItemStack previousContents) {
                onChanged.run();
            }
        };
    }

    private static FluidStacksResourceHandler createTank(Runnable onChanged) {
        return new FluidStacksResourceHandler(1, TANK_CAPACITY) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return resource.getFluid().isSame(ModRegistries.DISTORTION_FLUID.get());
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                onChanged.run();
            }
        };
    }

    public ReconstructionCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.RECONSTRUCTION_CORE_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return ModPatterns.RECONSTRUCTION_CHAMBER;
    }

    @Override
    protected String machineName() {
        return "Reconstruction chamber";
    }

    @Override
    public ResourceHandler<ItemResource> getItemHandler() {
        return automation;
    }

    @Override
    public ResourceHandler<FluidResource> getFluidHandler() {
        return fluidAutomation;
    }

    private void onInventoryChanged() {
        setChanged();
        syncToClients(); // the renderer shows the items
    }

    private void onTankChanged() {
        setChanged();
    }

    // ---- For the renderer ----

    public ItemStack getStack(int slot) {
        return inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
    }

    /** What the current recipe makes (for the merging animation). */
    public ItemStack getDisplayResult() {
        return displayResult;
    }

    /** Client: 0..1 through the current operation (smoothed between syncs). */
    public float getDisplayProgress(float partialTicks) {
        if (progressTotal <= 0) return 0;
        return Math.min(1F, (displayProgress + (isWorking() ? partialTicks : 0)) / progressTotal);
    }

    // ---- Anchors ----

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        super.onStructureChanged(nowFormed);
        refreshAnchors();
        cauldronLevel = -1;
        if (!nowFormed) updateBottles(false); // don't leave full bottles on a broken machine
    }

    private void refreshAnchors() {
        MultiblockPattern pattern = pattern();
        if (pattern == null) return;
        cauldron = pattern.anchor(worldPosition, rotation(), "cauldron");
        campfire = pattern.anchor(worldPosition, rotation(), "campfire");
        flower = pattern.anchor(worldPosition, rotation(), "flower");
        setChanged();
        syncToClients();
    }

    public @Nullable BlockPos getFlower() {
        return flower;
    }

    // ---- Processing ----

    private Reconstruction.@Nullable Recipe currentRecipe() {
        ItemResource compound = inventory.getResource(COMPOUNDS), base = inventory.getResource(BASE);
        if (!compound.equals(cachedCompound) || !base.equals(cachedBase) || cachedGeneration != CompoundMaterials.generation()) {
            cachedCompound = compound;
            cachedBase = base;
            cachedGeneration = CompoundMaterials.generation();
            Identifier material = compound.isEmpty() ? null : compound.getComponents().get(ModRegistries.MATERIAL.get());
            cachedRecipe = Reconstruction.find(material, base);
        }
        return cachedRecipe;
    }

    @Override
    protected void tickFormed() {
        if (level == null) return;
        long now = level.getGameTime();
        if (now % 40 == 0) {
            if (getWormholePos() == null) refreshWormhole();
            if (getSourceEmitter() == null && getEnergySource() != null) refreshEmitter();
            if (cauldron == null) refreshAnchors(); // chambers formed before the anchors existed
        }
        boolean worked = work();
        setWorking(worked);
        if (worked && now % SYNC_INTERVAL == 0) syncToClients(); // keep the client's animation in step
        updateBottles(worked);
        updateCauldron();
    }

    /** One tick of work; false when idle (missing inputs, fluid, output room or energy). */
    private boolean work() {
        Reconstruction.Recipe recipe = currentRecipe();
        if (recipe == null) {
            resetProgress();
            return false;
        }
        boolean ready = inventory.getAmountAsInt(COMPOUNDS) >= recipe.compounds()
                && tank.getAmountAsInt(0) >= fluidFor(recipe)
                && fits(new ItemStack(recipe.output()));
        if (!ready) return false; // keep the progress, wait

        int ticks = upgradedTicks(recipe.ticks()); // Speed upgrade
        if (progressTotal != ticks || !displayResult.is(recipe.output())) {
            progressTotal = ticks;
            progress = Math.min(progress, progressTotal);
            displayResult = new ItemStack(recipe.output());
            syncToClients();
        }
        long total = upgradedEnergy(recipe.energy()); // Speed and Efficiency upgrades
        long needed = total * (progress + 1) / progressTotal - total * progress / progressTotal;
        if (needed > 0) {
            if (availableEnergy() < needed) return false;
            drawEnergy(needed);
        }
        progress++;
        if (progress >= progressTotal) {
            progress = 0;
            finish(recipe);
            syncToClients();
        }
        setChanged();
        return true;
    }

    /** The distortion fluid an operation uses: less with Productivity upgrades. */
    private int fluidFor(Reconstruction.Recipe recipe) {
        return com.lealex.alchymastery.upgrade.Upgrades.scale(recipe.fluid(),
                com.lealex.alchymastery.upgrade.Upgrades.RECONSTRUCTION_FLUID, productivity());
    }

    private void finish(Reconstruction.Recipe recipe) {
        if (level == null) return;
        try (Transaction tx = Transaction.openRoot()) {
            if (inventory.extract(COMPOUNDS, inventory.getResource(COMPOUNDS), recipe.compounds(), tx) != recipe.compounds()) return;
            if (inventory.extract(BASE, inventory.getResource(BASE), 1, tx) != 1) return;
            int fluid = fluidFor(recipe);
            if (fluid > 0 && tank.extract(0, FluidResource.of(ModRegistries.DISTORTION_FLUID.get()), fluid, tx) != fluid) return;
            tx.commit();
        }
        ItemStack result = new ItemStack(recipe.output());
        int amount = inventory.getResource(OUTPUT).isEmpty() ? 0 : inventory.getAmountAsInt(OUTPUT);
        inventory.set(OUTPUT, ItemResource.of(result), amount + 1); // the output refuses insertion, so set()

        playSound(worldPosition, SoundEvents.BREWING_STAND_BREW, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        if (level instanceof ServerLevel serverLevel) {
            double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + ITEM_HEIGHT, z = worldPosition.getZ() + 0.5;
            serverLevel.sendParticles(ParticleTypes.WITCH, x, y, z, 16, 0.25, 0.2, 0.25, 0.05);
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 12, 0.2, 0.2, 0.2, 0.03);
        }
    }

    private void resetProgress() {
        if (progress != 0 || progressTotal != 0) {
            progress = 0;
            progressTotal = 0;
            setChanged();
            syncToClients();
        }
    }

    /** True if one more of this item fits in the output slot. */
    private boolean fits(ItemStack stack) {
        ItemResource current = inventory.getResource(OUTPUT);
        if (current.isEmpty()) return true;
        return current.equals(ItemResource.of(stack)) && inventory.getAmountAsInt(OUTPUT) + 1 <= stack.getMaxStackSize();
    }

    /** The brewing stand's bottles fill one by one through an operation (empty when idle). */
    private void updateBottles(boolean worked) {
        if (level == null) return;
        int bottles = !worked || progressTotal <= 0 ? 0 : 1 + Math.min(2, progress * 3 / progressTotal);
        BlockState state = getBlockState();
        if (!state.hasProperty(BrewingStandBlock.HAS_BOTTLE[0])) return;
        BlockState wanted = state;
        for (int i = 0; i < 3; i++) wanted = wanted.setValue(BrewingStandBlock.HAS_BOTTLE[i], i < bottles);
        if (wanted != state) level.setBlock(worldPosition, wanted, Block.UPDATE_CLIENTS);
    }

    /** The cauldron shell holds distortion fluid and shows the tank: empty, 1, 2 or 3 levels. */
    private void updateCauldron() {
        if (level == null || cauldron == null) return;
        int amount = tank.getAmountAsInt(0);
        int wanted = amount <= 0 ? 0 : Math.clamp((int) Math.ceil(amount * 3.0 / TANK_CAPACITY), 1, 3);
        if (wanted == cauldronLevel) return;
        if (!(level.getBlockEntity(cauldron) instanceof ChamberShellBlockEntity shell)) return;
        cauldronLevel = wanted;
        shell.setTint(ModRegistries.DISTORTION_FLUID_COLOR); // the water texture, tinted purple
        shell.setDisguise(wanted == 0 ? Blocks.CAULDRON.defaultBlockState()
                : Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, wanted));
    }

    // ---- Client effects ----

    @Override
    public void clientTick() {
        super.clientTick(); // the energy stream into the wormhole
        if (level == null || !isFormed()) return;
        RandomSource random = level.getRandom();
        if (isWorking() && displayProgress < progressTotal) displayProgress++;

        // The cauldron simmers over its lit campfire (shells: read what they look like)
        if (cauldron != null) {
            BlockState liquid = ChamberShellBlock.lookAt(level, cauldron);
            BlockPos fire = campfire != null ? campfire : cauldron.below();
            if (liquid.hasProperty(LayeredCauldronBlock.LEVEL) && CampfireBlock.isLitCampfire(ChamberShellBlock.lookAt(level, fire))) {
                double surface = cauldron.getY() + (6 + 3 * liquid.getValue(LayeredCauldronBlock.LEVEL)) / 16.0;
                double x0 = cauldron.getX() + 0.5, z0 = cauldron.getZ() + 0.5;
                if (random.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.BUBBLE_POP, x0 + (random.nextDouble() - 0.5) * 0.7, surface + 0.02,
                            z0 + (random.nextDouble() - 0.5) * 0.7, 0, 0.02, 0);
                }
                if (random.nextInt(8) == 0) {
                    level.addParticle(ParticleTypes.WHITE_SMOKE, x0 + (random.nextDouble() - 0.5) * 0.5, surface + 0.1,
                            z0 + (random.nextDouble() - 0.5) * 0.5, 0, 0.03, 0);
                }
                if (random.nextInt(60) == 0) {
                    level.playLocalSound(x0, surface, z0, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS,
                            0.4F, 0.8F + random.nextFloat() * 0.3F, false);
                }
            }
        }

        // While rebuilding: glyphs fall from the spore blossom into the items over the brewing stand
        if (isWorking() && flower != null) {
            double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + ITEM_HEIGHT, z = worldPosition.getZ() + 0.5;
            double aimY = y + PARTICLE_DROP; // enchant particles sink by the end of their flight
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.ENCHANT, x, aimY, z,
                        flower.getX() + 0.5 - x + (random.nextDouble() - 0.5) * 0.4, flower.getY() + 0.3 - aimY,
                        flower.getZ() + 0.5 - z + (random.nextDouble() - 0.5) * 0.4);
            }
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.WITCH, x + (random.nextDouble() - 0.5) * 0.4, y,
                        z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
            }
        }
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
        return Component.translatable("container.alchymastery.reconstruction_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ReconstructionMenu(containerId, playerInventory, inventory, guiData, this);
    }

    // ---- Breaking the core drops what's inside (the fluid is lost) ----

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
        tank.serialize(output.child("tank"));
        output.putInt("progress", progress);
        output.putInt("progressTotal", progressTotal);
        output.store("displayResult", ItemStack.OPTIONAL_CODEC, displayResult);
        if (cauldron != null) output.store("cauldron", BlockPos.CODEC, cauldron);
        if (campfire != null) output.store("campfire", BlockPos.CODEC, campfire);
        if (flower != null) output.store("flower", BlockPos.CODEC, flower);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        tank.deserialize(input.childOrEmpty("tank"));
        progress = input.getIntOr("progress", 0);
        progressTotal = input.getIntOr("progressTotal", 0);
        displayProgress = progress; // client: snap the animation to the server's progress
        displayResult = input.read("displayResult", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        cauldron = input.read("cauldron", BlockPos.CODEC).orElse(null);
        campfire = input.read("campfire", BlockPos.CODEC).orElse(null);
        flower = input.read("flower", BlockPos.CODEC).orElse(null);
    }

    /** Pipes and hoppers: insert compounds and base blocks, extract only the output. */
    private record ItemAutomation(ItemStacksResourceHandler inner) implements ResourceHandler<ItemResource> {
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
            return index != OUTPUT && inner.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index != OUTPUT ? inner.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return index == OUTPUT ? inner.extract(index, resource, amount, transaction) : 0;
        }
    }

    /** Pipes and buckets: distortion fluid goes in, nothing comes out. */
    private record FluidAutomation(FluidStacksResourceHandler inner) implements ResourceHandler<FluidResource> {
        @Override
        public int size() {
            return inner.size();
        }

        @Override
        public FluidResource getResource(int index) {
            return inner.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return inner.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return inner.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return inner.isValid(index, resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return inner.insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }
}
