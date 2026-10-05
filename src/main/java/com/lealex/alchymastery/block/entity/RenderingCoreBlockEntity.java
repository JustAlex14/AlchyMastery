package com.lealex.alchymastery.block.entity;

import com.lealex.alchymastery.compound.CompoundMaterial;
import com.lealex.alchymastery.compound.CompoundMaterials;
import com.lealex.alchymastery.item.CompoundItem;
import com.lealex.alchymastery.menu.RenderingMenu;
import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchymastery.registry.ModRegistries;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.world.level.block.Blocks;
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

import java.util.UUID;

/**
 * The rendering cauldron (design spec "Fluids and experience"): dissolves mob essences in distortion fluid and turns
 * them into liquid experience. Each essence costs 100 mB of distortion fluid and 10 DE (wormhole) and gives
 * 2 x 3^tier experience points (common 2, uncommon 6, rare 18...), stored as liquid experience (20 mB per point).
 * The experience leaves as fluid (pipes, buckets), through the GUI's drink buttons, through XP taps on the structure
 * and through linked experience siphons (repairing Mending gear). One player can be linked at a time; they are
 * drawn as a ghost casting at the end crystal in their own skin (RenderingCoreRenderer), an evoker when nobody is linked.
 */
public class RenderingCoreBlockEntity extends PoweredCoreBlockEntity implements MenuProvider, ExperienceReservoir {

    /** Its chamber's blocks switch with its own void particle (registry/ModParticles). */
    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return com.lealex.alchymastery.registry.ModParticles.VOID_WISP.get();
    }
    public static final int INPUT = 0, SLOT_COUNT = 1;
    public static final int DISTORTION = 0, EXPERIENCE = 1;
    public static final int DISTORTION_CAPACITY = 4_000, EXPERIENCE_CAPACITY = 16_000; // mB
    public static final int FLUID_PER_ESSENCE = 100, ENERGY_PER_ESSENCE = 10, TICKS_PER_ESSENCE = 20;
    public static final int BASE_POINTS = 2; // experience points of a tier 0 essence
    public static final int DATA_COUNT = 9;

    private final ItemStacksResourceHandler inventory = createInventory(this::onInventoryChanged);
    private final ResourceHandler<ItemResource> itemAutomation = new ItemAutomation(inventory);
    private final FluidStacksResourceHandler tanks = createTanks(this::onTanksChanged);
    private final ResourceHandler<FluidResource> fluidAutomation = new FluidAutomation(tanks);

    private int progress = 0;
    private @Nullable BlockPos xpCauldron; // left of the middle one: shows the stored experience
    private int xpCauldronLook = -1;
    private @Nullable BlockPos distortionCauldron; // right of the middle one: shows the distortion fluid
    private int distortionCauldronLook = -1;
    private @Nullable UUID linkedPlayer;
    private String linkedName = "";

    private final ContainerData guiData = new ContainerData() {
        @Override
        public int get(int index) {
            long energy = availableEnergy();
            int xp = tanks.getAmountAsInt(EXPERIENCE);
            return switch (index) {
                case 0 -> progress;
                case 1 -> upgradedTicks(TICKS_PER_ESSENCE);
                case 2 -> linkState();
                case 3 -> (int) (energy & 0xFFFF);
                case 4 -> (int) ((energy >>> 16) & 0xFFFF);
                case 5 -> tanks.getAmountAsInt(DISTORTION);
                case 6 -> xp & 0xFFFF;
                case 7 -> (xp >>> 16) & 0xFFFF;
                case 8 -> linkedPlayer != null ? 1 : 0;
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

    /** The input slot takes mob essences only (mob-family compounds). */
    public static ItemStacksResourceHandler createInventory(Runnable onChanged) {
        return new ItemStacksResourceHandler(SLOT_COUNT) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                return index == INPUT && isEssence(resource.toStack(1));
            }

            @Override
            protected void onContentsChanged(int index, ItemStack previousContents) {
                onChanged.run();
            }
        };
    }

    /** Distortion fluid goes in (tank 0); liquid experience is made inside (tank 1). */
    private static FluidStacksResourceHandler createTanks(Runnable onChanged) {
        return new FluidStacksResourceHandler(2, EXPERIENCE_CAPACITY) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return index == DISTORTION ? resource.getFluid().isSame(ModRegistries.DISTORTION_FLUID.get())
                        : resource.getFluid().isSame(ModRegistries.LIQUID_EXPERIENCE.get());
            }

            // Per-tank capacity (insertion and the reported capacity both read this)
            @Override
            protected int getCapacity(int index, FluidResource resource) {
                return index == DISTORTION ? DISTORTION_CAPACITY : EXPERIENCE_CAPACITY;
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                onChanged.run();
            }
        };
    }

    public static boolean isEssence(ItemStack stack) {
        return stack.is(ModRegistries.COMPOUND.get()) && CompoundMaterial.MOB.equals(CompoundItem.familyOf(stack));
    }

    /** Experience points one essence gives: 2 x 3^tier. */
    public static int pointsFor(ItemStack essence) {
        Integer tier = essence.get(ModRegistries.TIER.get());
        if (tier == null) {
            Identifier material = essence.get(ModRegistries.MATERIAL.get());
            CompoundMaterial data = material == null ? null : CompoundMaterials.get(material);
            tier = data == null ? 0 : data.tier();
        }
        int points = BASE_POINTS;
        for (int i = 0; i < Math.clamp(tier, 0, 6); i++) points *= 3;
        return points;
    }

    public RenderingCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.RENDERING_CORE_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return ModPatterns.RENDERING_CAULDRON;
    }

    @Override
    protected String machineName() {
        return "Rendering cauldron";
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
        setAnimationDisplay("input", inventory.getResource(INPUT).toStack(inventory.getAmountAsInt(INPUT))); // floats over the end crystal while working
    }

    private void onTanksChanged() {
        setChanged();
    }

    // ---- Anchors ----

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        super.onStructureChanged(nowFormed);
        MultiblockPattern pattern = pattern();
        xpCauldron = pattern == null ? null : pattern.anchor(worldPosition, rotation(), "xp_cauldron");
        distortionCauldron = pattern == null ? null : pattern.anchor(worldPosition, rotation(), "distortion_cauldron");
        xpCauldronLook = -1;
        distortionCauldronLook = -1;
        if (!nowFormed && linkedPlayer != null) {
            // Breaking the structure frees it (e.g. the linked player lost their siphon)
            linkedPlayer = null;
            linkedName = "";
            linkChanged();
        }
        setChanged();
    }

    // ---- Processing ----

    @Override
    protected void tickFormed() {
        if (level == null) return;
        long now = level.getGameTime();
        if (now % 40 == 0) {
            if (getWormholePos() == null) refreshWormhole();
            if (getSourceEmitter() == null && getEnergySource() != null) refreshEmitter();
            if (xpCauldron == null || distortionCauldron == null) onStructureChanged(true);
        }
        boolean working = work();
        setWorking(working);
        updateCauldrons();
    }

    private boolean work() {
        ItemResource input = inventory.getResource(INPUT);
        if (input.isEmpty()) { // the usual idle case: no allocation
            progress = 0;
            return false;
        }
        ItemStack essence = input.toStack(1);
        if (!isEssence(essence)) {
            progress = 0;
            return false;
        }
        // Productivity upgrades: more liquid experience per essence (scaled in mB, so small essences gain too)
        int experience = com.lealex.alchymastery.upgrade.Upgrades.scale(pointsFor(essence) * ModRegistries.MB_PER_XP,
                com.lealex.alchymastery.upgrade.Upgrades.RENDERING_EXPERIENCE, productivity());
        int ticks = upgradedTicks(TICKS_PER_ESSENCE); // Speed upgrade
        if (tanks.getAmountAsInt(DISTORTION) < FLUID_PER_ESSENCE) return false;
        if (tanks.getAmountAsInt(EXPERIENCE) + experience > EXPERIENCE_CAPACITY) return false; // full: wait
        if (progress == 0) {
            long energy = upgradedEnergy(ENERGY_PER_ESSENCE); // Speed and Efficiency upgrades
            if (availableEnergy() < energy) return false;
            drawEnergy(energy);
            startAnimationOperation(ticks);
        }
        if (++progress >= ticks) {
            progress = 0;
            try (Transaction tx = Transaction.openRoot()) {
                inventory.extract(INPUT, inventory.getResource(INPUT), 1, tx);
                tanks.extract(DISTORTION, tanks.getResource(DISTORTION), FLUID_PER_ESSENCE, tx);
                tx.commit();
            }
            tanks.set(EXPERIENCE, FluidResource.of(ModRegistries.LIQUID_EXPERIENCE.get()), tanks.getAmountAsInt(EXPERIENCE) + experience);
            finishAnimationOperation();
        }
        setChanged();
        return true;
    }

    /** The cauldrons either side of the crystal's pedestal show the distortion fluid (purple) and the experience (lime), level 0-3. */
    private void updateCauldrons() {
        if (xpCauldron != null) {
            xpCauldronLook = showLevel(xpCauldron, xpCauldronLook, fill(tanks.getAmountAsInt(EXPERIENCE), EXPERIENCE_CAPACITY), ModRegistries.LIQUID_EXPERIENCE_COLOR);
        }
        if (distortionCauldron != null) {
            distortionCauldronLook = showLevel(distortionCauldron, distortionCauldronLook, fill(tanks.getAmountAsInt(DISTORTION), DISTORTION_CAPACITY), ModRegistries.DISTORTION_FLUID_COLOR);
        }
    }

    /** Makes the cauldron shell at pos look filled to fillLevel (0-3) in this color; returns the new look code. */
    private int showLevel(@Nullable BlockPos pos, int previousLook, int fillLevel, int color) {
        int look = (color & 0xFFFFFF) * 4 + fillLevel; // changes when the level or the color changes
        if (level == null || pos == null || look == previousLook
                || !(level.getBlockEntity(pos) instanceof ChamberShellBlockEntity shell)) {
            return previousLook;
        }
        shell.setTint(color);
        shell.setDisguise(fillLevel == 0 ? Blocks.CAULDRON.defaultBlockState()
                : Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, fillLevel));
        return look;
    }

    private static int fill(int amount, int capacity) {
        return amount <= 0 ? 0 : Math.clamp((int) Math.ceil(amount * 3.0 / capacity), 1, 3);
    }

    // ---- Experience out (taps, siphons, drink buttons) ----

    @Override
    public String reservoirName() {
        return "rendering cauldron";
    }

    /** Experience points stored (whole points; liquid below 20 mB stays in the tank). */
    @Override
    public int getStoredPoints() {
        return tanks.getAmountAsInt(EXPERIENCE) / ModRegistries.MB_PER_XP;
    }

    /** Takes up to {@code maxPoints} experience points out of the tank and returns how many it took. */
    @Override
    public int drainPoints(int maxPoints) {
        int points = Math.min(maxPoints, getStoredPoints());
        if (points <= 0) return 0;
        tanks.set(EXPERIENCE, tanks.getResource(EXPERIENCE), tanks.getAmountAsInt(EXPERIENCE) - points * ModRegistries.MB_PER_XP);
        return points;
    }

    /** GUI: gives the player experience: one level's worth (or what's left), or everything stored. */
    public void drink(Player player, boolean all) {
        int wanted;
        if (all) {
            wanted = getStoredPoints();
        } else {
            int needed = player.getXpNeededForNextLevel();
            wanted = Math.max(1, needed - Math.round(player.experienceProgress * needed));
        }
        int points = drainPoints(wanted);
        if (points > 0) {
            player.giveExperiencePoints(points);
            playSound(player.blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f + level.getRandom().nextFloat() * 0.4f);
        }
    }

    // ---- Linked player (experience siphon): one per machine ----

    /**
     * A siphon is being linked to this machine by this player. Refused (false) while another player holds the
     * machine: they unlink their siphon first, or the link is cleared when the structure breaks.
     * The linked player is drawn brewing at the cauldron, in their own skin (an evoker when nobody is linked).
     */
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

    /** True if this player's siphon may draw from the machine. */
    @Override
    public boolean isLinkedTo(UUID player) {
        return player.equals(linkedPlayer);
    }

    public @Nullable UUID getLinkedPlayer() {
        return linkedPlayer;
    }

    @Override
    public String getLinkedName() {
        return linkedName;
    }

    private void linkChanged() {
        setAnimationDisplay("linked_player", linkedHead(linkedPlayer)); // read by the animation file's ghost figure
        setChanged();
        syncToClients();
    }

    /** A player head of this player (empty for none): how a linked player is shown to animation files. */
    public static ItemStack linkedHead(@Nullable UUID player) {
        if (player == null) return ItemStack.EMPTY;
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        head.set(net.minecraft.core.component.DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createUnresolved(player));
        return head;
    }

    // ---- The caster at the cauldron (client) ----

    // Anchor positions, cached: the renderer asks for them every frame. Recomputed when the pattern (a /reload)
    // or the core's facing changes.
    private @Nullable MultiblockPattern anchorsFrom;
    private @Nullable BlockState anchorsFacing;
    private @Nullable BlockPos casterAt, pedestalAt, catAt;

    private void refreshAnchors() {
        MultiblockPattern pattern = pattern();
        BlockState state = getBlockState();
        if (pattern == anchorsFrom && state == anchorsFacing) return;
        anchorsFrom = pattern;
        anchorsFacing = state;
        if (pattern == null) {
            casterAt = pedestalAt = catAt = null;
            return;
        }
        var rotation = rotation();
        casterAt = pattern.anchor(worldPosition, rotation, "caster");
        pedestalAt = pattern.anchor(worldPosition, rotation, "pedestal");
        catAt = pattern.anchor(worldPosition, rotation, "cat");
    }

    /** Where the caster stands (feet block), from the pattern's "caster" anchor; works on both sides. */
    public @Nullable BlockPos casterPos() {
        refreshAnchors();
        return casterAt;
    }

    /** The siphon flows into the crystal over the pedestal (not the core in the back wall). */
    @Override
    protected net.minecraft.world.phys.Vec3 siphonTarget() {
        BlockPos pedestal = pedestalPos();
        return pedestal == null ? super.siphonTarget() : net.minecraft.world.phys.Vec3.upFromBottomCenterOf(pedestal, 1.6);
    }

    /** The obsidian pedestal under the end crystal (the caster faces it), from the "pedestal" anchor; both sides. */
    public @Nullable BlockPos pedestalPos() {
        refreshAnchors();
        return pedestalAt;
    }

    /** Where the witch's cat sits (the pattern's "cat" anchor; drawn by RenderingCoreRenderer), or null. */
    public @Nullable BlockPos catPos() {
        refreshAnchors();
        return catAt;
    }

    /** Client: the evoker's spell particles rise from the caster's raised hands while the machine works. */
    @Override
    public void clientTick() {
        super.clientTick();
        if (level == null || !isFormed() || !isAnimationWorking()) return;
        BlockPos caster = casterPos(), target = pedestalPos();
        if (caster == null || target == null) return;
        double x = caster.getX() + 0.5, y = caster.getY() + 1.8, z = caster.getZ() + 0.5;
        // facing the pedestal, swaying like the evoker's arms
        float angle = (float) Math.atan2(target.getZ() - caster.getZ(), target.getX() - caster.getX())
                + net.minecraft.util.Mth.cos(level.getGameTime() * 0.6662F) * 0.25F;
        // the hands are 0.6 to each side, across the facing direction
        double sideX = -Math.sin(angle) * 0.6, sideZ = Math.cos(angle) * 0.6;
        var particle = net.minecraft.core.particles.ColorParticleOption.create(
                net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, 0.55F, 0.95F, 0.25F); // experience green
        level.addParticle(particle, x + sideX, y, z + sideZ, 0, 0, 0);
        level.addParticle(particle, x - sideX, y, z - sideZ, 0, 0, 0);
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
        return Component.translatable("container.alchymastery.rendering_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RenderingMenu(containerId, playerInventory, inventory, guiData, this);
    }

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
        tanks.serialize(output.child("tanks"));
        output.putInt("progress", progress);
        if (linkedPlayer != null) output.store("linkedPlayer", UUIDUtil.CODEC, linkedPlayer);
        output.putString("linkedName", linkedName);
        if (xpCauldron != null) output.store("xpCauldron", BlockPos.CODEC, xpCauldron);
        if (distortionCauldron != null) output.store("distortionCauldron", BlockPos.CODEC, distortionCauldron);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input.childOrEmpty("inventory"));
        tanks.deserialize(input.childOrEmpty("tanks"));
        progress = input.getIntOr("progress", 0);
        linkedPlayer = input.read("linkedPlayer", UUIDUtil.CODEC).orElse(null);
        linkedName = input.getStringOr("linkedName", "");
        xpCauldron = input.read("xpCauldron", BlockPos.CODEC).orElse(null);
        distortionCauldron = input.read("distortionCauldron", BlockPos.CODEC).orElse(null);
        distortionCauldronLook = -1;
        xpCauldronLook = -1;
    }

    // ---- Automation: essences in; distortion fluid in, liquid experience out ----

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
            return 0; // essences only go in
        }
    }

    private record FluidAutomation(FluidStacksResourceHandler inner) implements ResourceHandler<FluidResource> {
        @Override public int size() { return inner.size(); }
        @Override public FluidResource getResource(int index) { return inner.getResource(index); }
        @Override public long getAmountAsLong(int index) { return inner.getAmountAsLong(index); }
        @Override public long getCapacityAsLong(int index, FluidResource resource) { return inner.getCapacityAsLong(index, resource); }
        @Override public boolean isValid(int index, FluidResource resource) { return index == DISTORTION && inner.isValid(index, resource); }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == DISTORTION ? inner.insert(index, resource, amount, transaction) : 0;
        }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == EXPERIENCE ? inner.extract(index, resource, amount, transaction) : 0;
        }
    }
}
