package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchyx.block.ChamberShellBlock;
import com.lealex.alchymastery.block.StalactiteBlock;
import com.lealex.alchymastery.menu.CondensatorMenu;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The distortion condensator's core: turns water into distortion fluid with energy from a linked distortion
 * chamber (wormhole). Two tanks: water in (pipes, buckets), distortion fluid out (pipes, empty buckets).
 *
 * One batch = 1,000 mB water + 200 DE -> 500 mB distortion fluid over 5 seconds (design spec values, to become
 * config options). The DE is drawn bit by bit over the batch, like the destructuration chamber.
 *
 * Looks: the core is a conduit (drawn by CondensatorCoreRenderer), awake while working. The cauldron under it is
 * the basin: it shows the water tank, and while working its water is drawn up into the conduit. The cauldrons
 * around hold distortion fluid and show the output tank; purple drops fall into them from the stalactites, and
 * glyphs flow from the wormhole into the conduit.
 */
public class CondensatorCoreBlockEntity extends PoweredCoreBlockEntity implements MenuProvider {

    /** Its chamber's blocks switch with its own void particle (registry/ModParticles). */
    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return com.lealex.alchymastery.registry.ModParticles.VOID_DROPLET.get();
    }
    public static final int WATER = 0, DISTORTION = 1;
    public static final int TANK_CAPACITY = 8_000;       // mB, each tank
    public static final int WATER_PER_BATCH = 1_000;     // mB
    public static final int FLUID_PER_BATCH = 500;       // mB
    public static final long ENERGY_PER_BATCH = 200;     // DE
    public static final int BATCH_TICKS = 100;           // 5 seconds
    public static final int DATA_COUNT = 7;
    // Fly-towards particles (nautilus, enchant) sink this far below their target by the end of their flight
    private static final double NAUTILUS_DROP = 1.2;

    private final FluidStacksResourceHandler tanks = createTanks(this::onTanksChanged);
    // What pipes, buckets and the shells see: water goes in, distortion fluid comes out
    private final ResourceHandler<FluidResource> automation = new FluidAutomation(tanks);

    private int progress = 0;
    // What the cauldron shells currently show (0 = empty .. 3 = full); -1 = unknown
    private int cauldronLevel = -1, basinLevel = -1;
    private @Nullable BlockPos basin; // the water cauldron under the core (the conduit drinks from it)

    // Decorations found when the structure forms (synced: the client draws drops and steam there)
    private List<BlockPos> cauldrons = new ArrayList<>();
    private List<BlockPos> drips = new ArrayList<>();
    private boolean decorationsFound = false; // not saved: re-scan once after loading

    private final ContainerData guiData = new ContainerData() {
        @Override
        public int get(int index) {
            long energy = availableEnergy();
            return switch (index) {
                case 0 -> progress;
                case 1 -> batchTicks();
                case 2 -> linkState();
                case 3 -> (int) (energy & 0xFFFF);           // 16-bit halves, like the other GUIs
                case 4 -> (int) ((energy >>> 16) & 0xFFFF);
                case 5 -> tanks.getAmountAsInt(WATER);       // up to 8,000: fits in 16 bits
                case 6 -> tanks.getAmountAsInt(DISTORTION);
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

    /** The two tanks; only water may be put in (the distortion tank is filled by the machine with set()). */
    public static FluidStacksResourceHandler createTanks(Runnable onChanged) {
        return new FluidStacksResourceHandler(2, TANK_CAPACITY) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return index == WATER && resource.getFluid().isSame(Fluids.WATER);
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                onChanged.run();
            }
        };
    }

    public CondensatorCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.CONDENSATOR_CORE_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return ModPatterns.DISTORTION_CONDENSATOR;
    }

    @Override
    protected String machineName() {
        return "Distortion condensator";
    }

    @Override
    public ResourceHandler<FluidResource> getFluidHandler() {
        return automation;
    }

    private void onTanksChanged() {
        setChanged();
    }

    public int getWater() {
        return tanks.getAmountAsInt(WATER);
    }

    public int getDistortionFluid() {
        return tanks.getAmountAsInt(DISTORTION);
    }

    // ---- Structure ----

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        super.onStructureChanged(nowFormed);
        findDecorations();
        cauldronLevel = -1; // show the tanks again on the new cauldron shells
        basinLevel = -1;
    }

    /** Finds the cauldron and dripstone shells of the structure (where the drops fall and the water shows). */
    private void findDecorations() {
        cauldrons = new ArrayList<>();
        drips = new ArrayList<>();
        basin = null;
        MultiblockPattern pattern = pattern();
        if (level != null && pattern != null && isFormed()) {
            for (BlockPos pos : pattern.allPositions(worldPosition, rotation())) {
                BlockState look = ChamberShellBlock.lookAt(level, pos);
                if (look.getBlock() instanceof AbstractCauldronBlock) {
                    if (pos.equals(worldPosition.below())) basin = pos.immutable(); // right under the conduit
                    else cauldrons.add(pos.immutable());
                }
                // Drops fall from the tips only (the stalactites are two blocks long: a base over a tip)
                else if ((look.getBlock() instanceof StalactiteBlock && !look.getValue(StalactiteBlock.BASE))
                        || (look.getBlock() instanceof PointedDripstoneBlock
                            && look.getValue(PointedDripstoneBlock.THICKNESS) == net.minecraft.world.level.block.state.properties.DripstoneThickness.TIP)) {
                    drips.add(pos.immutable());
                }
            }
        }
        decorationsFound = true;
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
        }
        if (!decorationsFound) findDecorations();

        boolean worked = work();
        setWorking(worked);
        if (worked && now % 40 == 0) {
            playSound(worldPosition, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.5f, 1.2f);
        }
        updateCauldronShells();
    }

    /** One tick of work; false when idle (not enough water, output full, not enough energy). */
    private boolean work() {
        if (progress == 0 && !canStartBatch()) return false;

        int ticks = batchTicks();
        long energy = upgradedEnergy(ENERGY_PER_BATCH); // Speed and Efficiency upgrades
        long needed = energy * (progress + 1) / ticks - energy * Math.min(progress, ticks) / ticks;
        if (needed > 0) {
            if (availableEnergy() < needed) return false; // wait for energy, keep the progress
            drawEnergy(needed);
        }

        progress++;
        if (progress >= ticks) {
            progress = 0;
            finishBatch();
        }
        setChanged();
        return true;
    }

    private boolean canStartBatch() {
        return getWater() >= WATER_PER_BATCH && TANK_CAPACITY - getDistortionFluid() >= fluidPerBatch();
    }

    /** A batch's length: shorter with Speed upgrades. */
    private int batchTicks() {
        return upgradedTicks(BATCH_TICKS);
    }

    /** Distortion fluid made per batch: more with Productivity upgrades (up to 1:1 with the water). */
    private int fluidPerBatch() {
        return com.lealex.alchymastery.upgrade.Upgrades.scale(FLUID_PER_BATCH,
                com.lealex.alchymastery.upgrade.Upgrades.CONDENSATOR_FLUID, productivity());
    }

    private void finishBatch() {
        if (level == null || !canStartBatch()) return;
        try (Transaction tx = Transaction.openRoot()) {
            if (tanks.extract(WATER, FluidResource.of(Fluids.WATER), WATER_PER_BATCH, tx) != WATER_PER_BATCH) return;
            tx.commit();
        }
        // The output tank refuses insertion from outside (isValid), so the machine fills it with set()
        tanks.set(DISTORTION, FluidResource.of(ModRegistries.DISTORTION_FLUID.get()), getDistortionFluid() + fluidPerBatch());

        playSound(worldPosition, SoundEvents.BREWING_STAND_BREW, 0.7f, 0.8f + level.getRandom().nextFloat() * 0.2f);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.03);
        }
    }

    /**
     * The cauldron shells show the tanks: the basin under the conduit holds the water tank (normal water), the ring
     * of cauldrons the distortion fluid tank (the water texture, tinted purple). Empty, 1, 2 or 3 levels.
     */
    private void updateCauldronShells() {
        if (level == null) return;
        int ring = levelFor(getDistortionFluid());
        if (ring != cauldronLevel) {
            cauldronLevel = ring;
            for (BlockPos pos : cauldrons) setCauldronLook(pos, ring, ModRegistries.DISTORTION_FLUID_COLOR);
        }
        int water = levelFor(getWater());
        if (basin != null && water != basinLevel) {
            basinLevel = water;
            setCauldronLook(basin, water, 0);
        }
    }

    private static int levelFor(int amount) {
        return amount <= 0 ? 0 : Math.clamp((int) Math.ceil(amount * 3.0 / TANK_CAPACITY), 1, 3);
    }

    private void setCauldronLook(BlockPos pos, int cauldronLevel, int tint) {
        if (level == null || !(level.getBlockEntity(pos) instanceof ChamberShellBlockEntity shell)) return;
        BlockState look = cauldronLevel == 0 ? Blocks.CAULDRON.defaultBlockState()
                : Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, cauldronLevel);
        shell.setTint(tint);
        shell.setDisguise(look);
    }

    /** Height of the liquid surface in a cauldron that looks like {@code look}, or -1 if it holds nothing. */
    private static double surfaceOf(BlockPos pos, BlockState look) {
        if (!look.hasProperty(LayeredCauldronBlock.LEVEL)) return -1;
        return pos.getY() + (6 + 3 * look.getValue(LayeredCauldronBlock.LEVEL)) / 16.0; // like LayeredCauldronBlock
    }

    // ---- Client effects ----

    @Override
    public void clientTick() {
        super.clientTick(); // the energy stream into the wormhole
        if (level == null || !isFormed() || !isWorking()) return;
        if (level.getNearestPlayer(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 32, false) == null) return;
        RandomSource random = level.getRandom();

        // Purple drops forming under the dripstone tips; they fall and splash on their own (vanilla particles)
        for (BlockPos drip : drips) {
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
                        drip.getX() + 0.5, drip.getY() + 0.25, drip.getZ() + 0.5, 0, 0, 0);
            }
        }
        double coreX = worldPosition.getX() + 0.5, coreY = worldPosition.getY() + 0.5, coreZ = worldPosition.getZ() + 0.5;
        // The conduit sends what it draws from the basin to the stalactites, which drip it into the cauldrons:
        // nautilus particles (like a real conduit's) fly from the conduit to the base of each stalactite (where it
        // hangs from the roof). Nautilus particles travel from position + offset into position, and end up
        // NAUTILUS_DROP below the position they were given (vanilla's FlyTowardsPositionParticle), so aim that much higher.
        for (BlockPos drip : drips) {
            if (random.nextInt(3) == 0) {
                double baseX = drip.getX() + 0.5, baseY = drip.getY() + 0.9, baseZ = drip.getZ() + 0.5;
                double aimY = baseY + NAUTILUS_DROP;
                level.addParticle(ParticleTypes.NAUTILUS, baseX, aimY, baseZ,
                        coreX - baseX + (random.nextDouble() - 0.5) * 0.3, coreY - aimY,
                        coreZ - baseZ + (random.nextDouble() - 0.5) * 0.3);
            }
        }
        // The basin's surface ripples as the conduit drinks it
        if (basin != null) {
            double surface = surfaceOf(basin, ChamberShellBlock.lookAt(level, basin));
            if (surface >= 0) {
                if (random.nextInt(2) == 0) {
                    level.addParticle(ParticleTypes.BUBBLE_POP, basin.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.7,
                            surface + 0.02, basin.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.7, 0, 0.02, 0);
                }
            }
        }
        // The distortion fluid in the cauldrons around bubbles and steams
        for (BlockPos cauldron : cauldrons) {
            double surface = surfaceOf(cauldron, ChamberShellBlock.lookAt(level, cauldron));
            if (surface < 0) continue;
            double x = cauldron.getX() + 0.5, z = cauldron.getZ() + 0.5;
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.BUBBLE_POP, x + (random.nextDouble() - 0.5) * 0.7, surface + 0.02,
                        z + (random.nextDouble() - 0.5) * 0.7, 0, 0.02, 0);
            }
            if (random.nextInt(10) == 0) {
                level.addParticle(ParticleTypes.WHITE_SMOKE, x + (random.nextDouble() - 0.5) * 0.5, surface + 0.1,
                        z + (random.nextDouble() - 0.5) * 0.5, 0, 0.03, 0);
            }
        }
        // Glyphs from the wormhole down into the conduit (enchant particles also travel from position + offset in)
        BlockPos wormhole = getWormholePos();
        if (wormhole != null) {
            double bottom = wormhole.getY() - 0.05;
            double aimY = coreY + NAUTILUS_DROP; // they sink by the end of the flight: aim higher to end in the conduit
            level.addParticle(ParticleTypes.ENCHANT, coreX, aimY, coreZ,
                    (random.nextDouble() - 0.5) * 0.6, bottom - aimY, (random.nextDouble() - 0.5) * 0.6);
        }
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
        return Component.translatable("container.alchymastery.condensator_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CondensatorMenu(containerId, playerInventory, guiData, this);
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tanks.serialize(output.child("tanks"));
        output.putInt("progress", progress);
        output.store("cauldrons", BlockPos.CODEC.listOf(), cauldrons);
        output.store("drips", BlockPos.CODEC.listOf(), drips);
        if (basin != null) output.store("basin", BlockPos.CODEC, basin);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tanks.deserialize(input.childOrEmpty("tanks"));
        progress = input.getIntOr("progress", 0);
        cauldrons = new ArrayList<>(input.read("cauldrons", BlockPos.CODEC.listOf()).orElse(List.of()));
        drips = new ArrayList<>(input.read("drips", BlockPos.CODEC.listOf()).orElse(List.of()));
        basin = input.read("basin", BlockPos.CODEC).orElse(null);
    }

    /** Pipes, buckets and shells: insert only water into the water tank, extract only from the distortion tank. */
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
            return index == WATER && inner.isValid(index, resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == WATER ? inner.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == DISTORTION ? inner.extract(index, resource, amount, transaction) : 0;
        }
    }
}
