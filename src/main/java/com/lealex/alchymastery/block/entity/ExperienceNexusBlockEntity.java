package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.compound.CompoundMaterial;
import com.lealex.alchymastery.compound.CompoundMaterials;
import com.lealex.alchymastery.item.CompoundItem;
import com.lealex.alchymastery.menu.ExperienceNexusMenu;
import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchymastery.registry.ModRegistries;
import com.lealex.alchyx.block.ChamberShellBlock;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import com.lealex.alchyx.multiblock.MultiblockPatterns;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Rotation;
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
import java.util.UUID;

/**
 * The experience nexus: the experience chain in one structure, around three machine cores on pedestals
 * (destructuration north, condensator west, rendering core east) and a cauldron (south) that shows the stored
 * experience.
 *
 * The player gives it mob drops (or essences directly) and water. Three stages run side by side, each with the same
 * rules and costs as its own machine:
 * <ol>
 *   <li>destructure: mob drops -> essences into an internal store (other families are refused);</li>
 *   <li>condense: 1,000 mB water + 200 DE -> 500 mB distortion fluid;</li>
 *   <li>render: 1 essence + 100 mB distortion fluid + 10 DE -> 2 x 3^tier experience points (liquid experience).</li>
 * </ol>
 * The experience leaves like the rendering cauldron's: pipes and buckets, the GUI's drink buttons, experience taps on
 * the structure, and one linked experience siphon. Each machine core shows an animated miniature (NexusCoreRenderer).
 */
public class ExperienceNexusBlockEntity extends PoweredCoreBlockEntity implements MenuProvider, MiniatureHost, ExperienceReservoir {

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
    public static final int INPUT = 0, SLOT_COUNT = 1;
    public static final int WATER = 0, DISTORTION = 1, EXPERIENCE = 2;
    public static final int WATER_CAPACITY = 8_000, DISTORTION_CAPACITY = 4_000, EXPERIENCE_CAPACITY = 64_000; // mB
    public static final int STORE_CAPACITY = 256; // essences waiting to be rendered
    public static final int DESTRUCTURE = 0, CONDENSE = 1, RENDER = 2, STAGES = 3;
    public static final String[] MACHINES = {"destructuration", "condensator", "rendering"};
    public static final int DATA_COUNT = 14;
    public static final float BYPRODUCT_CHANCE = 0.05f;
    private static final int WATER_PER_BATCH = 1_000, FLUID_PER_BATCH = 500, CONDENSE_TICKS = 100;
    private static final long CONDENSE_ENERGY = 200;
    private static final int SYNC_INTERVAL = 10;

    private final ItemStacksResourceHandler inventory = createInventory(this::onInventoryChanged);
    private final ResourceHandler<ItemResource> itemAutomation = new ItemAutomation(inventory);
    private final FluidStacksResourceHandler tanks = createTanks(this::setChanged);
    private final ResourceHandler<FluidResource> fluidAutomation = new FluidAutomation(tanks);

    private final Map<Identifier, Integer> store = new LinkedHashMap<>(); // essences by material
    private final int[] progress = new int[STAGES], total = new int[STAGES];
    private int workingBits = 0;
    private @Nullable Identifier rendering; // the essence being rendered (taken from the store when it finishes)
    private ItemStack renderingEssence = ItemStack.EMPTY; // synced, for the miniature

    private CompoundMaterials.@Nullable Recipe inputRecipe;
    private ItemResource cachedInput = ItemResource.EMPTY;
    private int cachedInputGeneration = -1;

    // Linked player (experience siphon): one per machine
    private @Nullable UUID linkedPlayer;
    private String linkedName = "";

    // The cauldron showing the stored experience
    private @Nullable BlockPos basin;
    private int basinLook = -1;

    // Miniatures (computed on the server from the machines' patterns, synced when they change)
    private List<NexusCoreBlockEntity.Miniature> miniatures = List.of();
    private boolean miniaturesDirty = false;
    private final float[] displayProgress = new float[STAGES]; // client

    private final ContainerData guiData = new ContainerData() {
        @Override
        public int get(int index) {
            long energy = availableEnergy();
            int xp = tanks.getAmountAsInt(EXPERIENCE);
            return switch (index) {
                case 0, 1, 2 -> total[index] <= 0 ? 0 : progress[index] * 100 / total[index];
                case 3 -> linkState();
                case 4 -> (int) (energy & 0xFFFF);
                case 5 -> (int) ((energy >>> 16) & 0xFFFF);
                case 6 -> tanks.getAmountAsInt(WATER);
                case 7 -> tanks.getAmountAsInt(DISTORTION);
                case 8 -> xp & 0xFFFF;
                case 9 -> (xp >>> 16) & 0xFFFF;
                case 10 -> storeTotal();
                case 11 -> workingBits;
                case 12 -> linkedPlayer != null ? 1 : 0;
                case 13 -> inputRefused() ? 1 : 0;
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

    /** The input takes mob drops (destructurable into essences) and mob essences. */
    public static ItemStacksResourceHandler createInventory(Runnable onChanged) {
        return new ItemStacksResourceHandler(SLOT_COUNT) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                return index == INPUT && acceptsInput(resource);
            }

            @Override
            protected void onContentsChanged(int index, ItemStack previousContents) {
                onChanged.run();
            }
        };
    }

    public static boolean acceptsInput(ItemResource resource) {
        if (resource.isEmpty()) return false;
        ItemStack stack = resource.toStack(1);
        if (RenderingCoreBlockEntity.isEssence(stack)) return true;
        CompoundMaterials.Recipe recipe = CompoundMaterials.find(resource);
        return recipe != null && CompoundMaterial.MOB.equals(recipe.data().family());
    }

    private static FluidStacksResourceHandler createTanks(Runnable onChanged) {
        return new FluidStacksResourceHandler(3, EXPERIENCE_CAPACITY) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return switch (index) {
                    case WATER -> resource.getFluid().isSame(Fluids.WATER);
                    case DISTORTION -> resource.getFluid().isSame(ModRegistries.DISTORTION_FLUID.get());
                    default -> resource.getFluid().isSame(ModRegistries.LIQUID_EXPERIENCE.get());
                };
            }

            @Override
            protected int getCapacity(int index, FluidResource resource) {
                return switch (index) {
                    case WATER -> WATER_CAPACITY;
                    case DISTORTION -> DISTORTION_CAPACITY;
                    default -> EXPERIENCE_CAPACITY;
                };
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                onChanged.run();
            }
        };
    }

    public ExperienceNexusBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.EXPERIENCE_NEXUS_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return ModPatterns.EXPERIENCE_NEXUS;
    }

    @Override
    protected String machineName() {
        return "Experience nexus";
    }

    @Override
    public ResourceHandler<ItemResource> getItemHandler() {
        return itemAutomation;
    }

    @Override
    public ResourceHandler<FluidResource> getFluidHandler() {
        return fluidAutomation;
    }

    private void onInventoryChanged() {
        setChanged();
        syncToClients(); // the destructuration miniature shows the input
    }

    public ItemStack getStack(int slot) {
        return inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
    }

    public Map<Identifier, Integer> getStore() {
        return store;
    }

    private int storeTotal() {
        int sum = 0;
        for (int count : store.values()) sum += count;
        return sum;
    }

    /** Something sits in the input that isn't a mob drop (a pipe can't put it there, a player can). */
    private boolean inputRefused() {
        ItemResource input = inventory.getResource(INPUT);
        return !input.isEmpty() && !acceptsInput(input);
    }

    // ---- Structure ----

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        super.onStructureChanged(nowFormed);
        MultiblockPattern pattern = pattern();
        basin = pattern == null ? null : pattern.anchor(worldPosition, rotation(), "xp_basin");
        basinLook = -1;
        if (!nowFormed && linkedPlayer != null) { // breaking the structure frees it
            linkedPlayer = null;
            linkedName = "";
            linkChanged();
        }
        buildMiniatures();
    }

    /** Reads each machine core's own pattern and keeps its formed look (refreshed after a /reload too). */
    private void buildMiniatures() {
        List<NexusCoreBlockEntity.Miniature> built = new ArrayList<>();
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
                // The rendering hut's core is in its back wall: its essence floats over the pedestal instead.
                // Its anchors (turned like the miniature) place the ghost, the end crystal and the cat.
                Map<String, BlockPos> anchors = new java.util.HashMap<>();
                for (Map.Entry<String, BlockPos> anchor : machinePattern.anchors().entrySet()) {
                    anchors.put(anchor.getKey(), anchor.getValue().rotate(turn)); // a loop: turn isn't final
                }
                BlockPos focus = anchors.getOrDefault("pedestal", BlockPos.ZERO);
                built.add(new NexusCoreBlockEntity.Miniature(machine, offset, cells, focus, Map.copyOf(anchors), turn.ordinal()));
            }
        }
        miniatures = List.copyOf(built);
        miniaturesDirty = true;
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
            if (miniatures.isEmpty() || basin == null) onStructureChanged(true); // after loading
        }
        updateSyncTicks();
        int bits = 0;
        if (tickDestructure()) bits |= 1 << DESTRUCTURE;
        if (tickCondense()) bits |= 1 << CONDENSE;
        if (tickRender()) bits |= 1 << RENDER;
        if (bits != workingBits) {
            workingBits = bits;
            syncToClients();
        } else if (bits != 0 && now % SYNC_INTERVAL == 0) {
            syncToClients(); // keep the miniatures' animations in step
        }
        setWorking(bits != 0);
        updateBasin();
        setChanged();
    }

    private CompoundMaterials.@Nullable Recipe inputRecipe() {
        ItemResource input = inventory.getResource(INPUT);
        if (!input.equals(cachedInput) || cachedInputGeneration != CompoundMaterials.generation()) {
            cachedInput = input;
            cachedInputGeneration = CompoundMaterials.generation();
            CompoundMaterials.Recipe found = input.isEmpty() ? null : CompoundMaterials.find(input);
            inputRecipe = found != null && CompoundMaterial.MOB.equals(found.data().family()) ? found : null;
        }
        return inputRecipe;
    }

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

    private void updateSyncTicks() {
        if (!hasParallel()) {
            syncTicks = 0;
            return;
        }
        int fastest = Math.min(CONDENSE_TICKS, RenderingCoreBlockEntity.TICKS_PER_ESSENCE);
        CompoundMaterials.Recipe input = inputRecipe();
        if (input != null) fastest = Math.min(fastest, input.ticks());
        syncTicks = upgradedTicks(fastest);
    }

    /** Distortion fluid per condensation batch: more with Productivity. */
    private int condenseFluid() {
        return com.lealex.alchymastery.upgrade.Upgrades.scale(FLUID_PER_BATCH, com.lealex.alchymastery.upgrade.Upgrades.CONDENSATOR_FLUID, productivity());
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
        ItemResource input = inventory.getResource(INPUT);
        if (input.isEmpty()) {
            progress[DESTRUCTURE] = total[DESTRUCTURE] = 0;
            return false;
        }
        // Essences go straight into the store (they're already what destructuring makes)
        ItemStack first = input.toStack(1);
        if (RenderingCoreBlockEntity.isEssence(first)) {
            Identifier material = first.get(ModRegistries.MATERIAL.get());
            int room = STORE_CAPACITY - storeTotal();
            int moved = Math.min(room, inventory.getAmountAsInt(INPUT));
            if (material == null || moved <= 0) return false;
            try (Transaction tx = Transaction.openRoot()) {
                if (inventory.extract(INPUT, input, moved, tx) != moved) return false;
                tx.commit();
            }
            store.merge(material, moved, Integer::sum);
            return false; // instant: no operation to animate
        }
        CompoundMaterials.Recipe recipe = inputRecipe();
        if (recipe == null) {
            progress[DESTRUCTURE] = total[DESTRUCTURE] = 0;
            return false;
        }
        if (storeTotal() + recipe.compounds() + 1 > STORE_CAPACITY) return false; // store full: wait
        total[DESTRUCTURE] = stageTicks(recipe.ticks());
        if (!payTick(DESTRUCTURE, recipe.energy())) return false;
        if (++progress[DESTRUCTURE] >= total[DESTRUCTURE]) {
            progress[DESTRUCTURE] = 0;
            try (Transaction tx = Transaction.openRoot()) {
                if (inventory.extract(INPUT, input, 1, tx) != 1) return true;
                tx.commit();
            }
            int made = recipe.compounds();
            if (level != null && level.getRandom().nextFloat() < com.lealex.alchymastery.upgrade.Upgrades.BYPRODUCT_CHANCE[productivity()]) made++;
            store.merge(recipe.material(), made, Integer::sum);
            playSound(worldPosition, SoundEvents.GRINDSTONE_USE, 0.4f, 1.1f);
        }
        return true;
    }

    private boolean tickCondense() {
        int water = tanks.getAmountAsInt(WATER), fluid = tanks.getAmountAsInt(DISTORTION);
        int made = condenseFluid();
        if (progress[CONDENSE] == 0 && (water < WATER_PER_BATCH || DISTORTION_CAPACITY - fluid < made)) return false;
        total[CONDENSE] = stageTicks(CONDENSE_TICKS);
        if (!payTick(CONDENSE, CONDENSE_ENERGY)) return false;
        if (++progress[CONDENSE] >= total[CONDENSE]) {
            progress[CONDENSE] = 0;
            if (water < WATER_PER_BATCH || DISTORTION_CAPACITY - fluid < made) return true;
            try (Transaction tx = Transaction.openRoot()) {
                if (tanks.extract(WATER, FluidResource.of(Fluids.WATER), WATER_PER_BATCH, tx) != WATER_PER_BATCH) return true;
                tx.commit();
            }
            tanks.set(DISTORTION, FluidResource.of(ModRegistries.DISTORTION_FLUID.get()), fluid + made);
        }
        return true;
    }

    /** Experience points of one essence of this material: 2 x 3^tier, like the rendering cauldron. */
    private static int pointsOf(Identifier material) {
        CompoundMaterial data = CompoundMaterials.get(material);
        int points = RenderingCoreBlockEntity.BASE_POINTS;
        for (int i = 0; i < Math.clamp(data == null ? 0 : data.tier(), 0, 6); i++) points *= 3;
        return points;
    }

    private boolean tickRender() {
        if (rendering == null || store.getOrDefault(rendering, 0) <= 0) {
            // Start on the next essence of the store
            rendering = store.isEmpty() ? null : store.keySet().iterator().next();
            progress[RENDER] = 0;
            ItemStack shown = rendering == null ? ItemStack.EMPTY : essenceStack(rendering);
            if (!ItemStack.isSameItemSameComponents(shown, renderingEssence)) {
                renderingEssence = shown;
                syncToClients();
            }
            if (rendering == null) return false;
        }
        int experience = com.lealex.alchymastery.upgrade.Upgrades.scale(pointsOf(rendering) * ModRegistries.MB_PER_XP, com.lealex.alchymastery.upgrade.Upgrades.RENDERING_EXPERIENCE, productivity());
        if (tanks.getAmountAsInt(DISTORTION) < RenderingCoreBlockEntity.FLUID_PER_ESSENCE) return false;
        if (tanks.getAmountAsInt(EXPERIENCE) + experience > EXPERIENCE_CAPACITY) return false; // full: wait
        total[RENDER] = stageTicks(RenderingCoreBlockEntity.TICKS_PER_ESSENCE);
        if (!payTick(RENDER, RenderingCoreBlockEntity.ENERGY_PER_ESSENCE)) return false;
        if (++progress[RENDER] >= total[RENDER]) {
            progress[RENDER] = 0;
            try (Transaction tx = Transaction.openRoot()) {
                if (tanks.extract(DISTORTION, FluidResource.of(ModRegistries.DISTORTION_FLUID.get()),
                        RenderingCoreBlockEntity.FLUID_PER_ESSENCE, tx) != RenderingCoreBlockEntity.FLUID_PER_ESSENCE) return true;
                tx.commit();
            }
            int left = store.getOrDefault(rendering, 0) - 1;
            if (left > 0) store.put(rendering, left);
            else store.remove(rendering);
            tanks.set(EXPERIENCE, FluidResource.of(ModRegistries.LIQUID_EXPERIENCE.get()), tanks.getAmountAsInt(EXPERIENCE) + experience);
            playSound(worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.3f, 0.9f + level.getRandom().nextFloat() * 0.3f);
        }
        return true;
    }

    private static ItemStack essenceStack(Identifier material) {
        CompoundMaterial data = CompoundMaterials.get(material);
        return data == null ? ItemStack.EMPTY : CompoundItem.create(material, data, 1);
    }

    /** The south cauldron shows the stored experience (lime), level 0-3. */
    private void updateBasin() {
        if (level == null || basin == null) return;
        int xp = tanks.getAmountAsInt(EXPERIENCE);
        int fill = xp <= 0 ? 0 : Math.clamp((int) Math.ceil(xp * 3.0 / EXPERIENCE_CAPACITY), 1, 3);
        if (fill == basinLook || !(level.getBlockEntity(basin) instanceof ChamberShellBlockEntity shell)) return;
        basinLook = fill;
        shell.setTint(ModRegistries.LIQUID_EXPERIENCE_COLOR);
        shell.setDisguise(fill == 0 ? Blocks.CAULDRON.defaultBlockState()
                : Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, fill));
    }

    // ---- Experience out: ExperienceReservoir (taps, siphons) and the drink buttons ----

    @Override
    public String reservoirName() {
        return "experience nexus";
    }

    @Override
    public int getStoredPoints() {
        return tanks.getAmountAsInt(EXPERIENCE) / ModRegistries.MB_PER_XP;
    }

    @Override
    public int drainPoints(int maxPoints) {
        int points = Math.min(maxPoints, getStoredPoints());
        if (points <= 0) return 0;
        tanks.set(EXPERIENCE, tanks.getResource(EXPERIENCE), tanks.getAmountAsInt(EXPERIENCE) - points * ModRegistries.MB_PER_XP);
        return points;
    }

    /** GUI: one level's worth (or what's left), or everything stored. */
    public void drink(Player player, boolean all) {
        int wanted;
        if (all) {
            wanted = getStoredPoints();
        } else {
            int needed = player.getXpNeededForNextLevel();
            wanted = Math.max(1, needed - Math.round(player.experienceProgress * needed));
        }
        int points = drainPoints(wanted);
        if (points > 0 && level != null) {
            player.giveExperiencePoints(points);
            playSound(player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f + level.getRandom().nextFloat() * 0.4f);
        }
    }

    @Override
    public boolean link(ServerPlayer player) {
        if (linkedPlayer != null && !linkedPlayer.equals(player.getUUID())) return false;
        linkedPlayer = player.getUUID();
        linkedName = player.getGameProfile().name();
        linkChanged();
        return true;
    }

    @Override
    public void unlink(UUID player) {
        if (player.equals(linkedPlayer)) {
            linkedPlayer = null;
            linkedName = "";
            linkChanged();
        }
    }

    /** The linked player as the "linked_player" display: the miniature hut's ghost figure reads it. */
    private void linkChanged() {
        setAnimationDisplay("linked_player", RenderingCoreBlockEntity.linkedHead(linkedPlayer));
        setChanged();
        syncToClients();
    }

    @Override
    public boolean isLinkedTo(UUID player) {
        return player.equals(linkedPlayer);
    }

    @Override
    public String getLinkedName() {
        return linkedName;
    }

    // ---- MiniatureHost ----

    private static int stageOf(String machine) {
        for (int i = 0; i < MACHINES.length; i++) if (MACHINES[i].equals(machine)) return i;
        return -1;
    }

    public boolean isStageWorking(int stage) {
        return (workingBits & (1 << stage)) != 0;
    }

    @Override
    public List<NexusCoreBlockEntity.Miniature> getMiniatures() {
        return miniatures;
    }

    @Override
    public boolean isMachineWorking(String machine) {
        int stage = stageOf(machine);
        return stage >= 0 && isStageWorking(stage);
    }

    @Override
    public float getMachineProgress(String machine) {
        int stage = stageOf(machine);
        return stage >= 0 ? displayProgress[stage] : 0;
    }

    @Override
    public ItemStack miniatureItemA(String machine) {
        return switch (machine) {
            case "destructuration" -> getStack(INPUT);
            case "rendering" -> renderingEssence;
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public ItemStack miniatureItemB(String machine) {
        return ItemStack.EMPTY;
    }



    // ---- Client effects ----

    @Override
    public void clientTick() {
        super.clientTick(); // the energy stream into the wormhole
        if (level == null || !isFormed()) return;
        for (int stage = 0; stage < STAGES; stage++) {
            if (isStageWorking(stage) && total[stage] > 0) {
                displayProgress[stage] = (displayProgress[stage] + 1F / total[stage]) % 1F;
            }
        }
        if (level.getNearestPlayer(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 32, false) == null) return;
        // Essences flow from the destructuration miniature to the rendering one, distortion fluid from the
        // condensator's, and experience from the rendering miniature into the basin
        RandomSource random = level.getRandom();
        if (isStageWorking(RENDER) && random.nextInt(3) == 0) flow(miniatureCenter("destructuration"), miniatureCenter("rendering"), false);
        if (isStageWorking(CONDENSE) && random.nextInt(3) == 0) flow(miniatureCenter("condensator"), miniatureCenter("rendering"), true);
        if (isStageWorking(RENDER) && basin != null && random.nextInt(4) == 0) {
            flow(miniatureCenter("rendering"), Vec3.upFromBottomCenterOf(basin, 0.6), false);
        }
    }

    private void flow(@Nullable Vec3 start, @Nullable Vec3 end, boolean water) {
        if (level == null || start == null || end == null) return;
        RandomSource random = level.getRandom();
        double aimY = end.y + 1.2; // fly-towards particles end 1.2 below their target
        level.addParticle(water ? ParticleTypes.NAUTILUS : ParticleTypes.ENCHANT, end.x, aimY, end.z,
                start.x - end.x + (random.nextDouble() - 0.5) * 0.3, start.y - aimY, start.z - end.z + (random.nextDouble() - 0.5) * 0.3);
    }

    private @Nullable Vec3 miniatureCenter(String machine) {
        for (NexusCoreBlockEntity.Miniature miniature : miniatures) {
            if (miniature.machine().equals(machine)) {
                BlockPos core = worldPosition.offset(miniature.offset());
                return new Vec3(core.getX() + 0.5, worldPosition.getY() + NexusCoreBlockEntity.MINIATURE_HEIGHT + 0.6, core.getZ() + 0.5);
            }
        }
        return null;
    }

    // ---- GUI ----

    @Override
    public void openGui(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchymastery.experience_nexus");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ExperienceNexusMenu(containerId, playerInventory, inventory, guiData, this);
    }

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
        tag.store("miniatures", NexusCoreBlockEntity.Miniature.CODEC.listOf(), miniatures);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        if (miniaturesDirty) {
            miniaturesDirty = false;
            return ClientboundBlockEntityDataPacket.create(this);
        }
        return ClientboundBlockEntityDataPacket.create(this, (blockEntity, registries) -> blockEntity.saveCustomOnly(registries));
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        tanks.serialize(output.child("tanks"));
        output.store("store", Codec.unboundedMap(Identifier.CODEC, Codec.INT), Map.copyOf(store));
        output.store("progress", Codec.INT.listOf(), List.of(progress[0], progress[1], progress[2]));
        output.store("total", Codec.INT.listOf(), List.of(total[0], total[1], total[2]));
        output.putInt("workingBits", workingBits);
        if (rendering != null) output.store("rendering", Identifier.CODEC, rendering);
        output.store("renderingEssence", ItemStack.OPTIONAL_CODEC, renderingEssence);
        if (linkedPlayer != null) output.store("linkedPlayer", UUIDUtil.CODEC, linkedPlayer);
        output.putString("linkedName", linkedName);
        if (basin != null) output.store("basin", BlockPos.CODEC, basin);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        tanks.deserialize(input.childOrEmpty("tanks"));
        store.clear();
        store.putAll(input.read("store", Codec.unboundedMap(Identifier.CODEC, Codec.INT)).orElse(Map.of()));
        List<Integer> p = input.read("progress", Codec.INT.listOf()).orElse(List.of());
        List<Integer> t = input.read("total", Codec.INT.listOf()).orElse(List.of());
        for (int i = 0; i < STAGES; i++) {
            progress[i] = i < p.size() ? p.get(i) : 0;
            total[i] = i < t.size() ? t.get(i) : 0;
            displayProgress[i] = total[i] > 0 ? progress[i] / (float) total[i] : 0;
        }
        workingBits = input.getIntOr("workingBits", 0);
        rendering = input.read("rendering", Identifier.CODEC).orElse(null);
        renderingEssence = input.read("renderingEssence", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        linkedPlayer = input.read("linkedPlayer", UUIDUtil.CODEC).orElse(null);
        linkedName = input.getStringOr("linkedName", "");
        basin = input.read("basin", BlockPos.CODEC).orElse(null);
        basinLook = -1;
        input.read("miniatures", NexusCoreBlockEntity.Miniature.CODEC.listOf()).ifPresent(list -> miniatures = List.copyOf(list));
    }

    // ---- Automation: mob drops in; water and distortion fluid in, liquid experience out ----

    private record ItemAutomation(ItemStacksResourceHandler inner) implements ResourceHandler<ItemResource> {
        @Override public int size() { return inner.size(); }
        @Override public ItemResource getResource(int index) { return inner.getResource(index); }
        @Override public long getAmountAsLong(int index) { return inner.getAmountAsLong(index); }
        @Override public long getCapacityAsLong(int index, ItemResource resource) { return inner.getCapacityAsLong(index, resource); }
        @Override public boolean isValid(int index, ItemResource resource) { return inner.isValid(index, resource); }
        @Override public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return inner.insert(index, resource, amount, transaction);
        }
        @Override public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }

    private record FluidAutomation(FluidStacksResourceHandler inner) implements ResourceHandler<FluidResource> {
        @Override public int size() { return inner.size(); }
        @Override public FluidResource getResource(int index) { return inner.getResource(index); }
        @Override public long getAmountAsLong(int index) { return inner.getAmountAsLong(index); }
        @Override public long getCapacityAsLong(int index, FluidResource resource) { return inner.getCapacityAsLong(index, resource); }
        @Override public boolean isValid(int index, FluidResource resource) { return index != EXPERIENCE && inner.isValid(index, resource); }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index != EXPERIENCE ? inner.insert(index, resource, amount, transaction) : 0;
        }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == EXPERIENCE ? inner.extract(index, resource, amount, transaction) : 0;
        }
    }
}
