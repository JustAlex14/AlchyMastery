package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchyx.block.ChamberShellBlock;
import com.lealex.alchymastery.compound.CompoundMaterial;
import com.lealex.alchymastery.compound.CompoundMaterials;
import com.lealex.alchymastery.compound.Reconstruction;
import com.lealex.alchymastery.compound.Transmutation;
import com.lealex.alchymastery.item.CompoundItem;
import com.lealex.alchymastery.menu.NexusMenu;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import com.lealex.alchyx.multiblock.MultiblockPatterns;
import com.lealex.alchymastery.registry.ModRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The alchemical nexus: the end-game machine that runs the whole chain in one structure, around the four machine
 * cores (destructuration north, transmutation east, reconstruction south, condensator west).
 *
 * The player gives it ores (any destructurable item), water and a target (a ghost slot: any item the reconstruction
 * rules can rebuild, e.g. an iron ingot or raw gold). Four stages run side by side, each with the same rules and
 * costs as its own machine (no base blocks needed):
 * <ol>
 *   <li>destructure: input -> compounds into an internal store;</li>
 *   <li>transmute: compounds of other materials -> the target's material (value rules, 20% loss);</li>
 *   <li>condense: 1,000 mB water + 200 DE -> 500 mB distortion fluid (internal tank);</li>
 *   <li>reconstruct: target compounds + fluid -> the target item in the output.</li>
 * </ol>
 * Each machine core shows an animated miniature of its formed chamber (NexusCoreRenderer); which stages work is
 * synced so each miniature animates on its own.
 */
public class NexusCoreBlockEntity extends PoweredCoreBlockEntity implements MenuProvider, MiniatureHost {

    /** Its converted blocks stay invisible until client/NexusGenesis (Rift Genesis) brings each back through a rift. */
    @Override
    public boolean veilsWave() {
        return true;
    }

    /**
     * It counts as formed (message, sounds, working) once Rift Genesis is over: its length from the config
     * (the defaults on a dedicated server), its machines and its farthest converted block.
     */
    @Override
    protected int formationSettleTicks() {
        double farthest = 0;
        com.lealex.alchyx.multiblock.MultiblockPattern pattern = pattern();
        if (pattern != null) {
            for (com.lealex.alchyx.multiblock.MultiblockPattern.Conversion conversion : pattern.conversions(worldPosition, rotation())) {
                farthest = Math.max(farthest, Math.sqrt(conversion.pos().distSqr(worldPosition)));
            }
        }
        return com.lealex.alchymastery.ClientConfig.genesis().totalTicks(Math.max(1, getMiniatures().size()), farthest);
    }

    /** The siphon flows until Rift Genesis has brought every miniature out (client config timings). */
    @Override
    protected int siphonAfterForming() {
        return com.lealex.alchymastery.ClientConfig.genesis().revealStart(Math.max(1, getMiniatures().size()));
    }
    public static final int INPUT = 0, OUTPUT = 1, SLOT_COUNT = 2;
    public static final int WATER = 0, FLUID = 1;
    public static final int WATER_CAPACITY = 8_000, FLUID_CAPACITY = 4_000; // mB
    public static final int STORE_CAPACITY = 256;   // compounds kept between stages, all materials together
    public static final int DESTRUCTURE = 0, TRANSMUTE = 1, CONDENSE = 2, RECONSTRUCT = 3, STAGES = 4;
    public static final String[] MACHINES = {"destructuration", "transmutation", "condensator", "reconstruction"};
    public static final int DATA_COUNT = 13;
    public static final float BYPRODUCT_CHANCE = 0.05f;
    // Condensation (same values as the condensator)
    private static final int WATER_PER_BATCH = 1_000, FLUID_PER_BATCH = 500, CONDENSE_TICKS = 100;
    private static final long CONDENSE_ENERGY = 200;
    private static final int SYNC_INTERVAL = 10;

    private final ItemStacksResourceHandler inventory = createInventory(this::onInventoryChanged);
    private final ResourceHandler<ItemResource> itemAutomation = new ItemAutomation(inventory);
    private final FluidStacksResourceHandler tanks = createTanks(this::setChanged);
    private final ResourceHandler<FluidResource> fluidAutomation = new FluidAutomation(tanks);

    /** The ghost target: a copy of the item the player wants (never a real item). */
    private final SimpleContainer target = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            onTargetChanged();
        }
    };
    private ItemStack lastTarget = ItemStack.EMPTY; // to notice a new target

    private final Map<Identifier, Integer> store = new LinkedHashMap<>(); // compounds between stages
    private long valueBuffer = 0; // transmutation leftover value (hundredths, see Transmutation)

    private final int[] progress = new int[STAGES], total = new int[STAGES];
    private int workingBits = 0;   // synced: which stages work (one bit per stage)
    private int transmuteMade = 0; // targets the current transmutation makes (0 = none running)
    private ItemStack sourceCompound = ItemStack.EMPTY, targetCompound = ItemStack.EMPTY; // synced, for the animation

    // Recipe caches
    private ItemResource cachedInput = ItemResource.EMPTY, cachedTarget = ItemResource.EMPTY;
    private int cachedInputGeneration = -1, cachedTargetGeneration = -1;
    private CompoundMaterials.@Nullable Recipe inputRecipe;
    private Reconstruction.@Nullable Recipe targetRecipe;

    // ---- Miniatures: the formed look of each machine, computed on the server, sent to clients ----

    /**
     * One miniature: which machine, where its core is (offset from the nexus), its blocks, and the cell (relative
     * to its core, turned like the miniature) where its items float: the core itself, or e.g. the rendering hut's
     * pedestal (its core is in the back wall).
     */
    public record Miniature(String machine, BlockPos offset, List<MultiblockPattern.LookCell> cells, BlockPos focus,
                            Map<String, BlockPos> anchors, int turn) {
        public static final Codec<Miniature> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("machine").forGetter(Miniature::machine),
                BlockPos.CODEC.fieldOf("offset").forGetter(Miniature::offset),
                MultiblockPattern.LookCell.CODEC.listOf().fieldOf("cells").forGetter(Miniature::cells),
                BlockPos.CODEC.optionalFieldOf("focus", BlockPos.ZERO).forGetter(Miniature::focus),
                Codec.unboundedMap(Codec.STRING, BlockPos.CODEC).optionalFieldOf("anchors", Map.of()).forGetter(Miniature::anchors),
                Codec.INT.optionalFieldOf("turn", 0).forGetter(Miniature::turn) // Rotation ordinal: how it's turned
        ).apply(instance, Miniature::new));
    }

    private List<Miniature> miniatures = List.of();
    private boolean miniaturesDirty = false; // send them with the next client update (they're big: not every time)

    // Client-only animation state
    public final BookAnimation book = new BookAnimation();
    private final float[] displayProgress = new float[STAGES];
    private long lastTransmuteSeen = Long.MIN_VALUE / 2; // client: game time the transmutation stage last worked

    private final ContainerData guiData = new ContainerData() {
        @Override
        public int get(int index) {
            long energy = availableEnergy();
            Reconstruction.Recipe recipe = targetRecipe();
            return switch (index) {
                case 0, 1, 2, 3 -> total[index] <= 0 ? 0 : progress[index] * 100 / total[index];
                case 4 -> linkState();
                case 5 -> (int) (energy & 0xFFFF);
                case 6 -> (int) ((energy >>> 16) & 0xFFFF);
                case 7 -> tanks.getAmountAsInt(WATER);
                case 8 -> tanks.getAmountAsInt(FLUID);
                case 9 -> recipe == null ? 0 : store.getOrDefault(recipe.material(), 0);
                case 10 -> storeTotal();
                case 11 -> recipe == null ? 0 : recipe.compounds();
                case 12 -> workingBits;
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

    /** Water (index 0) and distortion fluid (index 1, made inside, or poured in to skip condensing). */
    private static FluidStacksResourceHandler createTanks(Runnable onChanged) {
        return new FluidStacksResourceHandler(2, WATER_CAPACITY) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return index == WATER ? resource.getFluid().isSame(Fluids.WATER)
                        : resource.getFluid().isSame(ModRegistries.DISTORTION_FLUID.get());
            }

            @Override
            protected int getCapacity(int index, FluidResource resource) {
                return index == WATER ? WATER_CAPACITY : FLUID_CAPACITY;
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                onChanged.run();
            }
        };
    }

    public NexusCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.ALCHEMICAL_NEXUS_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return ModPatterns.ALCHEMICAL_NEXUS;
    }

    @Override
    protected String machineName() {
        return "Alchemical nexus";
    }

    @Override
    public ResourceHandler<ItemResource> getItemHandler() {
        return itemAutomation;
    }

    @Override
    public ResourceHandler<FluidResource> getFluidHandler() {
        return fluidAutomation;
    }

    public SimpleContainer getTargetContainer() {
        return target;
    }

    /**
     * A new target resets the machine: every stage starts over, the compounds waiting between
     * stages and the transmutation value are cleared. The tanks keep their water and distortion fluid.
     */
    private void onTargetChanged() {
        ItemStack now = target.getItem(0);
        if (!ItemStack.isSameItemSameComponents(now, lastTarget)) {
            lastTarget = now.copy();
            if (level != null && !level.isClientSide()) {
                java.util.Arrays.fill(progress, 0);
                transmuteMade = 0;
                store.clear();
                valueBuffer = 0;
                sourceCompound = ItemStack.EMPTY;
                targetCompound = ItemStack.EMPTY; // set again from the new target on the next tick
            }
        }
        onInventoryChanged();
    }

    private void onInventoryChanged() {
        setChanged();
        syncToClients(); // the miniatures show the input, the target and the output
    }

    // ---- Accessors for the renderer ----

    public ItemStack getStack(int slot) {
        return inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
    }

    public ItemStack getTargetStack() {
        return target.getItem(0);
    }

    public ItemStack getSourceCompound() {
        return sourceCompound;
    }

    public ItemStack getTargetCompound() {
        return targetCompound;
    }

    public boolean isStageWorking(int stage) {
        return (workingBits & (1 << stage)) != 0;
    }

    /** Client: 0..1 through a stage's current operation. */
    public float getDisplayProgress(int stage) {
        return displayProgress[stage];
    }

    @Override
    public List<Miniature> getMiniatures() {
        return miniatures;
    }

    // ---- MiniatureHost: the machine names are MACHINES, in stage order ----

    private static int stageOf(String machine) {
        for (int i = 0; i < MACHINES.length; i++) if (MACHINES[i].equals(machine)) return i;
        return -1;
    }

    @Override
    public boolean isMachineWorking(String machine) {
        int stage = stageOf(machine);
        return stage >= 0 && isStageWorking(stage);
    }

    @Override
    public float getMachineProgress(String machine) {
        int stage = stageOf(machine);
        return stage >= 0 ? getDisplayProgress(stage) : 0;
    }

    @Override
    public ItemStack miniatureItemA(String machine) {
        return switch (machine) {
            case "destructuration" -> getStack(INPUT);
            case "transmutation" -> isStageWorking(TRANSMUTE) ? sourceCompound : ItemStack.EMPTY;
            case "reconstruction" -> isStageWorking(RECONSTRUCT) ? targetCompound : ItemStack.EMPTY;
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public ItemStack miniatureItemB(String machine) {
        return switch (machine) {
            case "transmutation" -> targetCompound;
            case "reconstruction" -> isStageWorking(RECONSTRUCT) ? getTargetStack() : getStack(OUTPUT);
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public BookAnimation book() {
        return book;
    }

    // ---- Structure ----

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        super.onStructureChanged(nowFormed);
        buildMiniatures();
    }

    /** Reads each machine core's own pattern and keeps its formed look (refreshed after a /reload too). */
    private void buildMiniatures() {
        List<Miniature> built = new ArrayList<>();
        MultiblockPattern pattern = pattern();
        if (level != null && pattern != null && isFormed()) {
            for (String machine : MACHINES) {
                BlockPos corePos = pattern.anchor(worldPosition, rotation(), machine);
                if (corePos == null) continue;
                // The core is absorbed into a hidden "restore" shell once formed: read what it looks like
                MultiblockPattern machinePattern = MultiblockPatterns.forCore(ChamberShellBlock.lookAt(level, corePos).getBlock());
                if (machinePattern == null) continue;
                BlockPos offset = corePos.subtract(worldPosition);
                // Turn the miniature so its front faces the nexus (machines without a front stay as written)
                Rotation turn = Rotation.NONE;
                if (machinePattern.front().isPresent()) {
                    Direction towardNexus = Direction.getApproximateNearest(-offset.getX(), 0, -offset.getZ());
                    for (Rotation candidate : Rotation.values()) {
                        if (candidate.rotate(machinePattern.front().get()) == towardNexus) turn = candidate;
                    }
                }
                List<MultiblockPattern.LookCell> cells = new ArrayList<>();
                for (MultiblockPattern.LookCell cell : machinePattern.formedLook()) {
                    cells.add(new MultiblockPattern.LookCell(cell.offset().rotate(turn), cell.state().rotate(turn)));
                }
                built.add(new Miniature(machine, offset, cells, BlockPos.ZERO, Map.of(), turn.ordinal()));
            }
        }
        miniatures = List.copyOf(built);
        miniaturesDirty = true;
        syncToClients();
    }

    // ---- Recipes ----

    private CompoundMaterials.@Nullable Recipe inputRecipe() {
        ItemResource input = inventory.getResource(INPUT);
        if (!input.equals(cachedInput) || cachedInputGeneration != CompoundMaterials.generation()) {
            cachedInput = input;
            cachedInputGeneration = CompoundMaterials.generation();
            inputRecipe = CompoundMaterials.find(input);
        }
        return inputRecipe;
    }

    private Reconstruction.@Nullable Recipe targetRecipe() {
        ItemResource wanted = ItemResource.of(target.getItem(0));
        if (!wanted.equals(cachedTarget) || cachedTargetGeneration != CompoundMaterials.generation()) {
            cachedTarget = wanted;
            cachedTargetGeneration = CompoundMaterials.generation();
            targetRecipe = Reconstruction.findTarget(wanted);
        }
        return targetRecipe;
    }

    private int storeTotal() {
        int sum = 0;
        for (int count : store.values()) sum += count;
        return sum;
    }

    private void addToStore(Identifier material, int count) {
        store.merge(material, count, Integer::sum);
    }

    private void takeFromStore(Identifier material, int count) {
        int left = store.getOrDefault(material, 0) - count;
        if (left > 0) store.put(material, left);
        else store.remove(material);
    }

    // ---- Processing ----

    @Override
    protected void tickFormed() {
        if (level == null) return;
        long now = level.getGameTime();
        if (now % 40 == 0) {
            if (getWormholePos() == null) refreshWormhole();
            if (getSourceEmitter() == null && getEnergySource() != null) refreshEmitter();
            if (miniatures.isEmpty()) buildMiniatures(); // after loading (not saved: rebuilt from the patterns)
        }
        Reconstruction.Recipe recipe = targetRecipe();
        // The miniatures show the current target's compound (not the one of the first operation)
        ItemStack wantedCompound = recipe == null ? ItemStack.EMPTY : compoundStack(recipe.material());
        if (!ItemStack.isSameItemSameComponents(wantedCompound, targetCompound)) {
            targetCompound = wantedCompound;
            syncToClients();
        }
        updateSyncTicks(recipe);
        int bits = 0;
        if (tickDestructure()) bits |= 1 << DESTRUCTURE;
        if (recipe != null && tickTransmute(recipe)) bits |= 1 << TRANSMUTE;
        if (tickCondense()) bits |= 1 << CONDENSE;
        if (recipe != null && tickReconstruct(recipe)) bits |= 1 << RECONSTRUCT;
        if (bits != workingBits) {
            workingBits = bits;
            syncToClients();
        } else if (bits != 0 && now % SYNC_INTERVAL == 0) {
            syncToClients(); // keep the miniatures' animations in step
        }
        setWorking(bits != 0);
        setChanged();
    }

    /** Spreads an operation's energy over its ticks; false (and no progress) when the chamber lacks it. */
    @Override
    public boolean acceptsParallel() {
        return true;
    }

    /**
     * A stage's length: its base ticks with the Speed upgrade; with Parallel, every stage takes the time of the
     * fastest one ({@link #syncTicks}, worked out each tick), so no stage waits on another.
     */
    private int stageTicks(int baseTicks) {
        return hasParallel() && syncTicks > 0 ? syncTicks : upgradedTicks(baseTicks);
    }

    private int syncTicks; // Parallel: the fastest stage's ticks this tick (0 = none)

    private void updateSyncTicks(Reconstruction.@Nullable Recipe target) {
        if (!hasParallel()) {
            syncTicks = 0;
            return;
        }
        int fastest = Math.min(Transmutation.OPERATION_TICKS, CONDENSE_TICKS);
        CompoundMaterials.Recipe input = inputRecipe();
        if (input != null) fastest = Math.min(fastest, input.ticks());
        if (target != null) fastest = Math.min(fastest, target.ticks());
        syncTicks = upgradedTicks(fastest);
    }

    /** Distortion fluid per condensation batch (Productivity: more) and per reconstruction (Productivity: less). */
    private int condenseFluid() {
        return com.lealex.alchymastery.upgrade.Upgrades.scale(FLUID_PER_BATCH, com.lealex.alchymastery.upgrade.Upgrades.CONDENSATOR_FLUID, productivity());
    }

    private int reconstructFluid(Reconstruction.Recipe recipe) {
        return com.lealex.alchymastery.upgrade.Upgrades.scale(recipe.fluid(), com.lealex.alchymastery.upgrade.Upgrades.RECONSTRUCTION_FLUID, productivity());
    }

    private boolean payTick(int stage, long baseEnergy) {
        long energy = upgradedEnergy(baseEnergy); // Speed and Efficiency upgrades
        long needed = energy * (progress[stage] + 1) / total[stage] - energy * progress[stage] / total[stage];
        if (needed <= 0) return true;
        if (availableEnergy() < needed) return false;
        drawEnergy(needed);
        return true;
    }

    private boolean tickDestructure() {
        CompoundMaterials.Recipe recipe = inputRecipe();
        if (recipe == null) {
            progress[DESTRUCTURE] = total[DESTRUCTURE] = 0;
            return false;
        }
        if (storeTotal() + recipe.compounds() + 1 > STORE_CAPACITY) return false; // store full: wait
        Reconstruction.Recipe wanted = targetRecipe();
        if (wanted != null && !Transmutation.compatible(recipe.material(), wanted.material())) {
            progress[DESTRUCTURE] = 0; // e.g. iron ore with rotten flesh as the target: could never be used
            return false;
        }
        total[DESTRUCTURE] = stageTicks(recipe.ticks());
        if (!payTick(DESTRUCTURE, recipe.energy())) return false;
        if (++progress[DESTRUCTURE] >= total[DESTRUCTURE]) {
            progress[DESTRUCTURE] = 0;
            try (Transaction tx = Transaction.openRoot()) {
                if (inventory.extract(INPUT, inventory.getResource(INPUT), 1, tx) != 1) return true;
                tx.commit();
            }
            int made = recipe.compounds();
            if (level != null && level.getRandom().nextFloat() < com.lealex.alchymastery.upgrade.Upgrades.BYPRODUCT_CHANCE[productivity()]) made++;
            addToStore(recipe.material(), made);
            playSound(worldPosition, SoundEvents.GRINDSTONE_USE, 0.4f, 1.1f);
        }
        return true;
    }

    private boolean tickTransmute(Reconstruction.Recipe recipe) {
        Identifier targetMaterial = recipe.material();
        if (transmuteMade > 0) { // an operation is running: finish it
            total[TRANSMUTE] = stageTicks(Transmutation.OPERATION_TICKS);
            if (++progress[TRANSMUTE] >= total[TRANSMUTE]) {
                addToStore(targetMaterial, transmuteMade);
                transmuteMade = 0;
                progress[TRANSMUTE] = 0;
                playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 1.0f);
            }
            return true;
        }
        long targetValue = Transmutation.valueOf(targetMaterial);
        if (targetValue <= 0) return false;
        // Any other material in the store becomes the target's material
        for (Map.Entry<Identifier, Integer> source : store.entrySet()) {
            Identifier material = source.getKey();
            if (material.equals(targetMaterial) || !Transmutation.compatible(material, targetMaterial)) continue;
            long each = Transmutation.effectiveValue(Transmutation.valueOf(material), targetValue, com.lealex.alchymastery.upgrade.Upgrades.TRANSMUTATION_LOSS[productivity()]);
            if (each <= 0) continue;
            long buffer = valueBuffer;
            int taken = buffer >= targetValue ? 0 : (int) ((targetValue - buffer + each - 1) / each);
            if (taken > source.getValue()) continue; // not enough of it yet
            buffer += taken * each;
            int room = STORE_CAPACITY - storeTotal() + taken;
            int made = (int) Math.min(buffer / targetValue, room);
            if (made <= 0) return false;
            long energy = upgradedEnergy(Transmutation.ENERGY_PER_VALUE * (targetValue / Transmutation.UNIT) * made);
            if (availableEnergy() < energy) return false;
            drawEnergy(energy);
            takeFromStore(material, taken);
            valueBuffer = buffer - made * targetValue;
            transmuteMade = made;
            progress[TRANSMUTE] = 1;
            total[TRANSMUTE] = stageTicks(Transmutation.OPERATION_TICKS);
            sourceCompound = compoundStack(material);
            targetCompound = compoundStack(targetMaterial);
            syncToClients();
            playSound(worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.0f);
            return true;
        }
        return false;
    }

    private static ItemStack compoundStack(Identifier material) {
        CompoundMaterial data = CompoundMaterials.get(material);
        return data == null ? ItemStack.EMPTY : CompoundItem.create(material, data, 1);
    }

    private boolean tickCondense() {
        int water = tanks.getAmountAsInt(WATER), fluid = tanks.getAmountAsInt(FLUID);
        int made = condenseFluid();
        if (progress[CONDENSE] == 0 && (water < WATER_PER_BATCH || FLUID_CAPACITY - fluid < made)) return false;
        total[CONDENSE] = stageTicks(CONDENSE_TICKS);
        if (!payTick(CONDENSE, CONDENSE_ENERGY)) return false;
        if (++progress[CONDENSE] >= total[CONDENSE]) {
            progress[CONDENSE] = 0;
            if (water < WATER_PER_BATCH || FLUID_CAPACITY - fluid < made) return true;
            try (Transaction tx = Transaction.openRoot()) {
                if (tanks.extract(WATER, FluidResource.of(Fluids.WATER), WATER_PER_BATCH, tx) != WATER_PER_BATCH) return true;
                tx.commit();
            }
            tanks.set(FLUID, FluidResource.of(ModRegistries.DISTORTION_FLUID.get()), fluid + made);
        }
        return true;
    }

    private boolean tickReconstruct(Reconstruction.Recipe recipe) {
        boolean ready = store.getOrDefault(recipe.material(), 0) >= recipe.compounds()
                && tanks.getAmountAsInt(FLUID) >= reconstructFluid(recipe)
                && fitsOutput(new ItemStack(recipe.output()));
        if (!ready) return false;
        total[RECONSTRUCT] = stageTicks(recipe.ticks());
        if (!payTick(RECONSTRUCT, recipe.energy())) return false;
        if (++progress[RECONSTRUCT] >= total[RECONSTRUCT]) {
            progress[RECONSTRUCT] = 0;
            try (Transaction tx = Transaction.openRoot()) {
                int fluid = reconstructFluid(recipe);
                if (fluid > 0 && tanks.extract(FLUID, FluidResource.of(ModRegistries.DISTORTION_FLUID.get()), fluid, tx) != fluid) return true;
                tx.commit();
            }
            takeFromStore(recipe.material(), recipe.compounds());
            int amount = inventory.getResource(OUTPUT).isEmpty() ? 0 : inventory.getAmountAsInt(OUTPUT);
            inventory.set(OUTPUT, ItemResource.of(new ItemStack(recipe.output())), amount + 1);
            playSound(worldPosition, SoundEvents.BREWING_STAND_BREW, 0.8f, 1.0f);
            if (level instanceof ServerLevel serverLevel) {
                Vec3 at = Vec3.atCenterOf(worldPosition).add(0, 0.8, 0);
                serverLevel.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.05);
            }
        }
        return true;
    }

    private boolean fitsOutput(ItemStack stack) {
        ItemResource current = inventory.getResource(OUTPUT);
        if (current.isEmpty()) return true;
        return current.equals(ItemResource.of(stack)) && inventory.getAmountAsInt(OUTPUT) + 1 <= stack.getMaxStackSize();
    }

    // ---- Client effects ----

    @Override
    public void clientTick() {
        super.clientTick(); // the energy stream into the wormhole
        if (level == null || !isFormed()) return;
        for (int stage = 0; stage < STAGES; stage++) {
            if (isStageWorking(stage) && total[stage] > 0) {
                // Operations follow each other: loop back to the start (each sync snaps it to the server's value)
                displayProgress[stage] = (displayProgress[stage] + 1F / total[stage]) % 1F;
            }
        }
        BlockPos transmutation = miniatureCore("transmutation");
        book.tick(level, transmutation != null ? transmutation : worldPosition, isStageWorking(TRANSMUTE));
        if (level.getNearestPlayer(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 32, false) == null) return;

        // Material flowing between the miniatures: destructuration -> transmutation -> reconstruction, and
        // condensator -> reconstruction (fly-towards particles end 1.2 below the target: aim higher)
        RandomSource random = level.getRandom();
        if (isStageWorking(TRANSMUTE)) lastTransmuteSeen = level.getGameTime();
        if (isStageWorking(TRANSMUTE) && random.nextInt(3) == 0) flow("destructuration", "transmutation", false);
        // Compounds already of the target's material (ancient debris -> netherite) skip transmutation: they flow
        // straight from the destructuration miniature to the reconstruction one
        boolean viaTransmutation = level.getGameTime() - lastTransmuteSeen < 100;
        if (isStageWorking(RECONSTRUCT) && random.nextInt(3) == 0) {
            flow(viaTransmutation ? "transmutation" : "destructuration", "reconstruction", false);
        }
        if (isStageWorking(CONDENSE) && random.nextInt(3) == 0) flow("condensator", "reconstruction", true);
    }

    private void flow(String from, String to, boolean water) {
        BlockPos a = miniatureCore(from), b = miniatureCore(to);
        if (level == null || a == null || b == null) return;
        RandomSource random = level.getRandom();
        Vec3 start = miniatureCenter(a), end = miniatureCenter(b);
        double aimY = end.y + 1.2;
        level.addParticle(water ? ParticleTypes.NAUTILUS : ParticleTypes.ENCHANT, end.x, aimY, end.z,
                start.x - end.x + (random.nextDouble() - 0.5) * 0.3, start.y - aimY, start.z - end.z + (random.nextDouble() - 0.5) * 0.3);
    }

    /**
     * Height of the miniatures' base above the nexus block's bottom: on the pedestal, in place of their machine core
     * (the core sits on the pedestal and turns invisible when the nexus forms).
     */
    public static final double MINIATURE_HEIGHT = 1.02;

    /** Middle of the miniature under a machine core (for the flowing particles). */
    private Vec3 miniatureCenter(BlockPos machineCore) {
        return new Vec3(machineCore.getX() + 0.5, worldPosition.getY() + MINIATURE_HEIGHT + 0.6, machineCore.getZ() + 0.5);
    }

    /** World position of a machine core whose miniature is shown, or null. */
    public @Nullable BlockPos miniatureCore(String machine) {
        for (Miniature miniature : miniatures) {
            if (miniature.machine().equals(machine)) return worldPosition.offset(miniature.offset());
        }
        return null;
    }

    // ---- GUI (only once formed) ----

    @Override
    public void openGui(Player player) {
        // Also before forming: the GUI then offers AlchyX's structure preview (ghost blocks of what's missing)
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchymastery.alchemical_nexus");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new NexusMenu(containerId, playerInventory, inventory, target, guiData, this);
    }

    /** For the GUI tooltip: the compounds waiting between stages, e.g. "12 iron, 3 gold". */
    public Map<Identifier, Integer> getStore() {
        return store;
    }

    // ---- Breaking the core drops its items (stored compounds and fluids are lost) ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) return;
        for (ItemStack stack : inventory.copyToList()) {
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
    }

    // ---- Client sync: the miniatures only go out when they change (or with the chunk) ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.store("miniatures", Miniature.CODEC.listOf(), miniatures);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        if (miniaturesDirty) {
            miniaturesDirty = false;
            return ClientboundBlockEntityDataPacket.create(this); // getUpdateTag: with the miniatures
        }
        return ClientboundBlockEntityDataPacket.create(this, (blockEntity, registries) -> blockEntity.saveCustomOnly(registries));
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        tanks.serialize(output.child("tanks"));
        output.store("target", ItemStack.OPTIONAL_CODEC, target.getItem(0));
        output.store("store", Codec.unboundedMap(Identifier.CODEC, Codec.INT), Map.copyOf(store));
        output.putLong("valueBuffer", valueBuffer);
        output.store("progress", Codec.INT.listOf(), List.of(progress[0], progress[1], progress[2], progress[3]));
        output.store("total", Codec.INT.listOf(), List.of(total[0], total[1], total[2], total[3]));
        output.putInt("workingBits", workingBits);
        output.putInt("transmuteMade", transmuteMade);
        output.store("sourceCompound", ItemStack.OPTIONAL_CODEC, sourceCompound);
        output.store("targetCompound", ItemStack.OPTIONAL_CODEC, targetCompound);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        tanks.deserialize(input.childOrEmpty("tanks"));
        ItemStack loadedTarget = input.read("target", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        lastTarget = loadedTarget.copy(); // loading isn't a target change: no reset
        target.setItem(0, loadedTarget);
        store.clear();
        store.putAll(input.read("store", Codec.unboundedMap(Identifier.CODEC, Codec.INT)).orElse(Map.of()));
        valueBuffer = input.getLongOr("valueBuffer", 0L);
        List<Integer> p = input.read("progress", Codec.INT.listOf()).orElse(List.of());
        List<Integer> t = input.read("total", Codec.INT.listOf()).orElse(List.of());
        for (int i = 0; i < STAGES; i++) {
            progress[i] = i < p.size() ? p.get(i) : 0;
            total[i] = i < t.size() ? t.get(i) : 0;
            displayProgress[i] = total[i] > 0 ? progress[i] / (float) total[i] : 0; // client: snap the animations
        }
        workingBits = input.getIntOr("workingBits", 0);
        transmuteMade = input.getIntOr("transmuteMade", 0);
        sourceCompound = input.read("sourceCompound", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        targetCompound = input.read("targetCompound", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        // Only in full client updates (chunk load, or after they changed)
        input.read("miniatures", Miniature.CODEC.listOf()).ifPresent(list -> miniatures = List.copyOf(list));
    }

    /** Pipes and hoppers: insert into the input, extract from the output. */
    private record ItemAutomation(ItemStacksResourceHandler inner) implements ResourceHandler<ItemResource> {
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

    /** Pipes and buckets: water (and distortion fluid) go in, nothing comes out. */
    private record FluidAutomation(FluidStacksResourceHandler inner) implements ResourceHandler<FluidResource> {
        @Override public int size() { return inner.size(); }
        @Override public FluidResource getResource(int index) { return inner.getResource(index); }
        @Override public long getAmountAsLong(int index) { return inner.getAmountAsLong(index); }
        @Override public long getCapacityAsLong(int index, FluidResource resource) { return inner.getCapacityAsLong(index, resource); }
        @Override public boolean isValid(int index, FluidResource resource) { return inner.isValid(index, resource); }

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
