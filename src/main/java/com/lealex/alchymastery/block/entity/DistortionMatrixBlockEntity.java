package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchymastery.Config;
import com.lealex.alchymastery.menu.DistortionMatrixMenu;
import com.lealex.alchyx.multiblock.MultiblockPatterns;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** The distortion chamber's core: burns fuel into distortion energy while its chamber is formed. */
public class DistortionMatrixBlockEntity extends MultiblockCoreBlockEntity implements MenuProvider {

    /** Its chamber's blocks switch with its own void particle (registry/ModParticles). */
    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return com.lealex.alchymastery.registry.ModParticles.VOID_SHARD.get();
    }
    public static final long MAX_ENERGY = 100_000L; // buffer cap (DE); meant to become a config option
    public static final long ENERGY_PER_TICK = 5L;       // burn rate; a fuel worth 100 DE burns for 20 ticks
    public static final int DATA_COUNT = 5;              // values synced to an open GUI

    private long energy = 0L;
    private int burnTicksLeft = 0;
    private int burnTicksTotal = 0;   // length of the current fuel's burn, for the GUI flame
    private long burnRate = ENERGY_PER_TICK; // DE per tick of the current burn (Speed burns faster)
    private ItemStack burningItem = ItemStack.EMPTY; // the item being absorbed, shown by the renderer
    private long burnEndTime = 0L;    // game time when the current burn ends (the client animates from it)

    /** Its upgrades (AlchyX's system): faster burning (less DE per fuel), more DE per fuel, fuel not always used up. */
    private final com.lealex.alchyx.upgrade.UpgradeStorage upgrades =
            createUpgrades(com.lealex.alchymastery.upgrade.AlchymasteryUpgrades.MATRIX);

    // 1-slot fuel inventory that only accepts lapis (hoppers, pipes and the GUI respect this too)
    private final ItemStacksResourceHandler fuel = createFuelHandler(this::onFuelChanged);

    /** Values an open GUI needs. Minecraft syncs them as 16-bit numbers, so the energy is split in two. */
    private final ContainerData guiData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) (energy & 0xFFFF);
                case 1 -> (int) ((energy >>> 16) & 0xFFFF);
                case 2 -> burnTicksLeft;
                case 3 -> isFormed() ? 2 : isForming() ? 1 : 0;
                case 4 -> burnTicksTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // read-only: the server is the source of truth
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /** True if the item is listed in the distortionFuels config. */
    public static boolean isFuel(ItemResource resource) {
        return Config.getFuelEnergy(resource) > 0;
    }

    /** The fuel slot; also used by the client-side menu so both sides accept the same items. */
    public static ItemStacksResourceHandler createFuelHandler(Runnable onChanged) {
        return new ItemStacksResourceHandler(1) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                return isFuel(resource);
            }

            @Override
            protected void onContentsChanged(int index, ItemStack previousContents) {
                onChanged.run();
            }
        };
    }

    public DistortionMatrixBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.DISTORTION_MATRIX_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return ModPatterns.DISTORTION_CHAMBER;
    }

    @Override
    protected String machineName() {
        return "Distortion chamber";
    }

    @Override
    public ResourceHandler<ItemResource> getItemHandler() {
        return fuel;
    }

    // ---- Accessors ----

    public ItemStacksResourceHandler getFuelHandler() {
        return fuel;
    }

    public long getEnergy() {
        return energy;
    }

    /** Takes up to {@code amount} DE out of the buffer (machines draw through their wormhole); returns what it gave. */
    public long extractEnergy(long amount) {
        long given = Math.min(Math.max(0, amount), energy);
        if (given > 0) {
            energy -= given;
            setChanged();
        }
        return given;
    }

    /** A copy of the fuel stack, for the floating-item renderer. */
    public ItemStack getFuelStack() {
        return fuel.getResource(0).toStack(fuel.getAmountAsInt(0));
    }

    public ItemStack getBurningItem() {
        return burningItem;
    }

    public long getBurnEndTime() {
        return burnEndTime;
    }

    public int getBurnTicksTotal() {
        return burnTicksTotal;
    }

    public long getFuelCount() {
        return fuel.getAmountAsLong(0);
    }

    // ---- Client sync (for the floating fuel) ----

    private void onFuelChanged() {
        setChanged();
        syncToClients();
    }

    // ---- GUI ----

    /** Opens the GUI for this player (server side). */
    @Override
    public void openGui(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchymastery.distortion_matrix");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DistortionMatrixMenu(containerId, inventory, fuel, guiData, this);
    }

    // ---- Ticking (the structure check and the wave are in MultiblockCoreBlockEntity) ----

    /** Every tick while the chamber is formed: burn fuel into energy. */
    @Override
    protected void tickFormed() {
        if (burnTicksLeft == 0) tryStartBurn();

        // Burn: add energy smoothly while the current fuel lasts
        if (burnTicksLeft > 0) {
            burnTicksLeft--;
            energy = Math.min(MAX_ENERGY, energy + burnRate);
            setChanged();

            // The crystal on the roof streams the energy out during the whole burn
            spawnCrystalParticles(1);

            if (burnTicksLeft == 0) {
                finishAbsorbing();
                // Chain straight into the next item in the same tick, so the floating item never blinks
                tryStartBurn();
            }
        }
    }

    /** Takes one fuel item and starts burning it, if there is room for energy and fuel in the slot. */
    private void tryStartBurn() {
        if (level == null || energy >= MAX_ENERGY || getFuelCount() <= 0) return;
        ItemResource resource = fuel.getResource(0);
        Item burnedItem = getFuelStack().getItem();
        long fuelEnergy = Config.getFuelEnergy(resource);
        if (fuelEnergy <= 0) return;
        // Productivity: more DE out of each item
        fuelEnergy = Math.round(fuelEnergy * com.lealex.alchymastery.upgrade.Upgrades.MATRIX_ENERGY[
                upgrades.tier(com.lealex.alchymastery.upgrade.AlchymasteryUpgrades.PRODUCTIVITY)]);
        // Speed: burns faster, but each item gives less
        int speed = upgrades.tier(com.lealex.alchymastery.upgrade.AlchymasteryUpgrades.SPEED);
        fuelEnergy = Math.max(1, Math.round(fuelEnergy / com.lealex.alchymastery.upgrade.Upgrades.MATRIX_SPEED_FUEL[speed]));
        long rate = Math.max(1, Math.round(ENERGY_PER_TICK * com.lealex.alchymastery.upgrade.Upgrades.SPEED[speed]));
        // Efficiency: a chance the item burns without being used up
        boolean saved = level.getRandom().nextFloat() < com.lealex.alchymastery.upgrade.Upgrades.MATRIX_FUEL_SAVE[
                upgrades.tier(com.lealex.alchymastery.upgrade.AlchymasteryUpgrades.EFFICIENCY)];

        try (Transaction tx = Transaction.openRoot()) {
            int taken = saved ? 1 : fuel.extract(0, resource, 1, tx);
            if (taken == 1) {
                tx.commit();
                // Capped so the GUI can sync it (16-bit values): about 27 minutes of burning max
                burnRate = rate;
                burnTicksTotal = (int) Math.min(Short.MAX_VALUE, Math.max(1, fuelEnergy / rate));
                burnTicksLeft = burnTicksTotal;
                burningItem = new ItemStack(burnedItem);
                burnEndTime = level.getGameTime() + burnTicksLeft;
                syncToClients();
            }
        }
    }

    /** Particles streaming out of the distortion crystal on the roof (two blocks above the core). */
    private void spawnCrystalParticles(int count) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos crystal = worldPosition.above(2);
        if (!level.getBlockState(crystal).is(ModRegistries.DISTORTION_CRYSTAL.get())) return;
        // where client/DistortionCrystalRenderer draws the crystal, about 1.3 blocks above its pedestal's bottom
        double cx = crystal.getX() + 0.5, cy = crystal.getY() + 1.3, cz = crystal.getZ() + 0.5;
        for (int i = 0; i < count; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double up = level.getRandom().nextDouble() * 0.6 - 0.1;
            // with count 0 the offset is the velocity: drifting out of the crystal
            serverLevel.sendParticles(com.lealex.alchymastery.registry.ModParticles.VOID_SHARD.get(), true, false, cx, cy, cz, 0,
                    Math.cos(angle), up, Math.sin(angle), 0.06); // amethyst shards thrown off the crystal
            if (level.getRandom().nextInt(4) == 0) {
                serverLevel.sendParticles(com.lealex.alchymastery.registry.ModParticles.VOID_LINK.get(), true, false, cx, cy, cz, 0,
                        Math.cos(angle), up + 0.2, Math.sin(angle), 0.04);
            }
        }
    }

    /** The burning item is used up. */
    private void finishAbsorbing() {
        burningItem = ItemStack.EMPTY;
        syncToClients();
        spawnCrystalParticles(10);
        playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 1.4f);
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("energy", energy);
        output.putInt("burnTicksLeft", burnTicksLeft);
        output.putInt("burnTicksTotal", burnTicksTotal);
        output.putLong("burnRate", burnRate);
        output.putLong("burnEndTime", burnEndTime);
        output.store("burningItem", ItemStack.OPTIONAL_CODEC, burningItem);
        fuel.serialize(output.child("fuel"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy = Math.min(MAX_ENERGY, input.getLongOr("energy", 0L)); // worlds saved with the old 1e9 cap
        burnTicksLeft = input.getIntOr("burnTicksLeft", 0);
        burnTicksTotal = input.getIntOr("burnTicksTotal", burnTicksLeft);
        burnRate = input.getLongOr("burnRate", ENERGY_PER_TICK);
        burnEndTime = input.getLongOr("burnEndTime", 0L);
        burningItem = input.read("burningItem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        fuel.deserialize(input.childOrEmpty("fuel"));
    }
}
