package com.lealex.alchymastery.block.entity;

import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchymastery.block.WormholeBlock;
import com.lealex.alchymastery.registry.ModParticles;
import com.lealex.alchymastery.registry.ModRegistries;
import com.lealex.alchymastery.upgrade.AlchymasteryUpgrades;
import com.lealex.alchymastery.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A machine that runs on distortion energy: it remembers the distortion chamber it is linked to (set with the
 * wormhole attuner) and draws energy straight from that chamber's buffer.
 */
public abstract class PoweredCoreBlockEntity extends MultiblockCoreBlockEntity {
    /** Max distance between a distortion chamber and a machine it powers, same dimension only. */
    public static final int WORMHOLE_RANGE = 124;

    public static final int LINK_NONE = 0, LINK_OK = 1, LINK_UNREACHABLE = 2;

    private static final double PARTICLE_PLAYER_RANGE = 48.0; // stream particles only when a player is this close

    private @Nullable BlockPos energySource; // the distortion matrix's position
    private @Nullable BlockPos wormholePos;  // the Wormhole block in the structure, found when it forms
    private @Nullable BlockPos sourceEmitter; // top center block of the linked chamber, where the stream starts
    private boolean working = false;         // synced: the particle stream runs fast while the machine works

    // Upgrades (AlchyX's system: saved, synced, dropped, floated on the structure and corrupting it by the base class)
    private final com.lealex.alchyx.upgrade.UpgradeStorage upgrades =
            createUpgrades(acceptsParallel() ? AlchymasteryUpgrades.NEXUS : AlchymasteryUpgrades.MACHINE);

    protected PoweredCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- Upgrades ----

    /** The upgrade types a menu shows for this kind of machine (the client builds its copy of the slots from them). */
    public static java.util.List<com.lealex.alchyx.upgrade.UpgradeType> upgradeTypes(boolean parallel) {
        return parallel ? AlchymasteryUpgrades.NEXUS : AlchymasteryUpgrades.MACHINE;
    }

    /** Only the nexuses take the Parallel upgrade. */
    public boolean acceptsParallel() {
        return false;
    }

    /** The installed tier of this type (0 = none). */
    public int upgradeTier(com.lealex.alchyx.upgrade.UpgradeType type) {
        return upgrades.tier(type);
    }

    /** An operation's ticks with the installed Speed upgrade. */
    protected int upgradedTicks(int baseTicks) {
        return Upgrades.ticks(baseTicks, upgradeTier(AlchymasteryUpgrades.SPEED));
    }

    /** An operation's energy with the installed Speed (costs more) and Efficiency (costs less) upgrades. */
    protected long upgradedEnergy(long baseEnergy) {
        return Upgrades.energy(baseEnergy, upgradeTier(AlchymasteryUpgrades.SPEED), upgradeTier(AlchymasteryUpgrades.EFFICIENCY));
    }

    protected int productivity() {
        return upgradeTier(AlchymasteryUpgrades.PRODUCTIVITY);
    }

    protected boolean hasParallel() {
        return acceptsParallel() && upgradeTier(AlchymasteryUpgrades.PARALLEL) > 0;
    }

    public @Nullable BlockPos getEnergySource() {
        return energySource;
    }

    public void setEnergySource(@Nullable BlockPos source) {
        this.energySource = source == null ? null : source.immutable();
        setChanged();
        refreshEmitter();
        syncToClients(); // clients need it for the particle stream
        updateWormholeLook();
    }

    public @Nullable BlockPos getSourceEmitter() {
        return sourceEmitter;
    }

    /** The linked chamber's top center block (its roof light), straight above the matrix at the pattern's top. */
    protected void refreshEmitter() {
        DistortionMatrixBlockEntity matrix = linkedMatrix();
        if (matrix == null || matrix.pattern() == null) return;
        sourceEmitter = matrix.getBlockPos().above(matrix.pattern().maxOffset().getY());
        setChanged();
        syncToClients();
    }

    public boolean isWorking() {
        return working;
    }

    protected void setWorking(boolean working) {
        if (this.working != working) {
            this.working = working;
            setAnimationWorking(working); // animation files ("when": "working")
            syncToClients();
            updateWormholeLook();
        } else if (level != null && level.getGameTime() % 20 == 0) {
            updateWormholeLook(); // the linked chamber may have been unloaded, broken or rebuilt meanwhile
        }
    }

    /** Server: shows the link on the wormhole block (unlinked / linked / active). Only sets the block when it changes. */
    protected void updateWormholeLook() {
        if (wormholePos == null) return;
        WormholeBlock.Look look = !isSettled() ? WormholeBlock.Look.LINKED // still forming: open, distortion pouring in
                : linkState() != LINK_OK ? WormholeBlock.Look.UNLINKED
                : working ? WormholeBlock.Look.ACTIVE : WormholeBlock.Look.LINKED;
        setWormholeLook(wormholePos, look);
    }

    private void setWormholeLook(BlockPos pos, WormholeBlock.Look look) {
        if (level == null || level.isClientSide() || !level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.is(ModRegistries.WORMHOLE.get()) && state.getValue(WormholeBlock.LOOK) != look) {
            level.setBlock(pos, state.setValue(WormholeBlock.LOOK, look), Block.UPDATE_CLIENTS); // no neighbor updates
        }
    }

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        super.onStructureChanged(nowFormed);
        if (nowFormed) {
            refreshWormhole();
            refreshEmitter();
        } else {
            if (wormholePos != null) setWormholeLook(wormholePos, WormholeBlock.Look.UNLINKED); // dormant again
            if (formingWormhole != null) setWormholeLook(formingWormhole, WormholeBlock.Look.UNLINKED);
            formingWormhole = null;
            wormholePos = null;
            working = false;
            setChanged();
            syncToClients();
        }
    }

    private @Nullable BlockPos formingWormhole; // server: the wormhole opened while the wave runs

    /** While the blocks switch, the structure's wormhole opens (cage turning) and distortion pours into the core. */
    @Override
    protected void tickForming() {
        if (level == null || level.getGameTime() % 10 != 0 || pattern() == null) return;
        if (formingWormhole == null || !level.getBlockState(formingWormhole).is(ModRegistries.WORMHOLE.get())) {
            formingWormhole = null;
            for (BlockPos pos : pattern().allPositions(worldPosition, rotation())) {
                if (level.getBlockState(pos).is(ModRegistries.WORMHOLE.get())) {
                    formingWormhole = pos.immutable();
                    break;
                }
            }
        }
        if (formingWormhole != null) setWormholeLook(formingWormhole, WormholeBlock.Look.LINKED);
    }

    @Override
    protected void onFormationSettled() {
        formingWormhole = null;
        updateWormholeLook(); // now its real look (linked to a chamber or not)
    }

    /** Finds the Wormhole block of the formed structure (the end of the particle stream). */
    protected void refreshWormhole() {
        wormholePos = null;
        if (level != null && pattern() != null) {
            for (BlockPos pos : pattern().allPositions(worldPosition, rotation())) {
                if (level.getBlockState(pos).is(ModRegistries.WORMHOLE.get())) {
                    wormholePos = pos.immutable();
                    break;
                }
            }
        }
        setChanged();
        syncToClients();
        updateWormholeLook();
    }

    public @Nullable BlockPos getWormholePos() {
        return wormholePos;
    }

    /**
     * Client: particles flowing from the top of the linked distortion chamber to the wormhole, along an arc that
     * goes over any blocks in the way. Fast and bright while working, faint motes while idle; only with a
     * player within PARTICLE_PLAYER_RANGE of either end (the spec's FPS rule).
     */
    @Override
    public void clientTick() {
        if (level != null) siphonTick();
        if (level == null || energySource == null || wormholePos == null || !isFormed()) return;
        Vec3 from = sourceEmitter != null ? Vec3.upFromBottomCenterOf(sourceEmitter, 1.1) : Vec3.atCenterOf(energySource);
        Vec3 to = Vec3.upFromBottomCenterOf(wormholePos, 1.1);
        if (level.getNearestPlayer(to.x, to.y, to.z, PARTICLE_PLAYER_RANGE, false) == null
                && level.getNearestPlayer(from.x, from.y, from.z, PARTICLE_PLAYER_RANGE, false) == null) {
            return;
        }
        // Recompute the arc when the ends move, and every 5 seconds in case blocks were placed in the way
        if (!from.equals(arcFrom) || !to.equals(arcTo) || level.getGameTime() - arcComputedAt > 100) {
            arcFrom = from;
            arcTo = to;
            arcHeight = findClearArcHeight(from, to);
            arcComputedAt = level.getGameTime();
        }

        RandomSource random = level.getRandom();
        double length = from.distanceTo(to);
        int count = working ? (int) Math.min(12, 2 + length / 8) : (random.nextInt(4) == 0 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double t = random.nextDouble();
            Vec3 at = arcPoint(from, to, arcHeight, t);
            Vec3 tangent = arcTangent(from, to, arcHeight, t).normalize();
            if (working) {
                // Short-lived sparks drifting along the arc: together they draw a flowing line. End rods keep 91% of
                // their speed per tick, so a spark travels about speed / 0.09 blocks: near the end, slow it down so
                // it stops at the wormhole instead of drifting past it
                double speed = Math.min(0.08, (1 - t) * length * 0.09);
                level.addParticle(com.lealex.alchymastery.registry.ModParticles.VOID_LINK.get(), at.x, at.y, at.z, tangent.x * speed, tangent.y * speed, tangent.z * speed);
            } else {
                level.addParticle(com.lealex.alchymastery.registry.ModParticles.VOID_LINK.get(), at.x, at.y, at.z, tangent.x * 0.02, tangent.y * 0.02, tangent.z * 0.02);
            }
        }
    }

    // ---- The siphon: distortion drawn from the wormhole down into the core (void_siphon particles) ----

    private @Nullable BlockPos siphonFrom;          // client: the structure's wormhole while it forms
    private long siphonLookedAt = Long.MIN_VALUE / 2; // (MIN_VALUE itself overflows "now - siphonLookedAt")

    /** Ticks the siphon keeps flowing after the machine formed (the nexuses: until their miniatures are out). */
    protected int siphonAfterForming() {
        return 30;
    }

    /** Where the siphon flows into: the core's middle (the rendering cauldron: its crystal). */
    protected Vec3 siphonTarget() {
        return Vec3.atCenterOf(worldPosition);
    }

    /**
     * Client: while the machine forms (its wave), just after, and while it works (draws energy), distortion flows
     * from its wormhole into the core. Only when a wormhole is really there (checked every tick).
     */
    private void siphonTick() {
        long now = level.getGameTime();
        boolean justFormed = isFormed() && getFormedAt() > 0 && now - getFormedAt() < siphonAfterForming();
        if (!isForming() && !justFormed && !(isFormed() && working)) return;
        BlockPos from = isFormed() && wormholePos != null ? wormholePos : siphonFrom;
        if (from == null || !level.getBlockState(from).is(ModRegistries.WORMHOLE.get())) {
            if (now - siphonLookedAt < 20) return; // look for it again now and then (it may be placed later)
            siphonLookedAt = now;
            siphonFrom = null;
            if (pattern() != null) {
                for (BlockPos pos : pattern().allPositions(worldPosition, rotation())) {
                    if (level.getBlockState(pos).is(ModRegistries.WORMHOLE.get())) {
                        siphonFrom = pos.immutable();
                        break;
                    }
                }
            }
            from = siphonFrom;
            if (from == null) return;
        }
        Vec3 start = Vec3.atCenterOf(from), end = siphonTarget();
        if (level.getNearestPlayer(end.x, end.y, end.z, PARTICLE_PLAYER_RANGE, false) == null) return;
        Vec3 path = end.subtract(start);
        double length = path.length();
        if (length < 0.5) return;
        Vec3 dir = path.scale(1 / length);
        RandomSource random = level.getRandom();
        int count = isFormed() && working && !justFormed ? (random.nextInt(2) == 0 ? 1 : 0) : 2;
        for (int i = 0; i < count; i++) {
            double t = random.nextDouble() * 0.85;
            Vec3 at = start.add(path.scale(t));
            // a void_siphon keeps 91% of its speed per tick (travels about speed / 0.09): it stops at the core
            double speed = Math.min(0.09, (1 - t) * length * 0.09);
            level.addParticle(ModParticles.VOID_SIPHON.get(), at.x + (random.nextDouble() - 0.5) * 0.12, at.y,
                    at.z + (random.nextDouble() - 0.5) * 0.12, dir.x * speed, dir.y * speed, dir.z * speed);
        }
    }

    // ---- The particle arc: a parabola from the chamber's top to the wormhole, raised until nothing blocks it ----

    private static final double MAX_ARC_HEIGHT = 48;
    private @Nullable Vec3 arcFrom, arcTo;
    private double arcHeight;
    private long arcComputedAt;

    private static Vec3 arcPoint(Vec3 from, Vec3 to, double height, double t) {
        return from.lerp(to, t).add(0, 4 * height * t * (1 - t), 0);
    }

    private static Vec3 arcTangent(Vec3 from, Vec3 to, double height, double t) {
        return to.subtract(from).add(0, 4 * height * (1 - 2 * t), 0);
    }

    /** The lowest arc height (in steps of 1 block) whose path doesn't cross a block with collision. */
    private double findClearArcHeight(Vec3 from, Vec3 to) {
        if (level == null) return 0;
        int samples = (int) Math.ceil(from.distanceTo(to) * 2) + 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int height = 0; height <= MAX_ARC_HEIGHT; height++) {
            boolean clear = true;
            for (int i = 1; i < samples && clear; i++) {
                Vec3 p = arcPoint(from, to, height, i / (double) samples);
                pos.set(p.x, p.y, p.z);
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) clear = false;
            }
            if (clear) return height;
        }
        return MAX_ARC_HEIGHT;
    }

    /** The linked distortion matrix, if its chunk is loaded and its chamber is formed. */
    public @Nullable DistortionMatrixBlockEntity linkedMatrix() {
        if (energySource == null || level == null || !level.isLoaded(energySource)) return null;
        return level.getBlockEntity(energySource) instanceof DistortionMatrixBlockEntity matrix && matrix.isFormed()
                ? matrix : null;
    }

    /** LINK_NONE (never linked), LINK_OK, or LINK_UNREACHABLE (chamber unloaded, broken or gone). */
    public int linkState() {
        if (energySource == null) return LINK_NONE;
        return linkedMatrix() != null ? LINK_OK : LINK_UNREACHABLE;
    }

    /** Takes up to {@code amount} DE from the linked chamber; returns what it got. */
    protected long drawEnergy(long amount) {
        DistortionMatrixBlockEntity matrix = linkedMatrix();
        return matrix == null ? 0 : matrix.extractEnergy(amount);
    }

    /** Energy stored in the linked chamber (0 if unreachable), for the GUI. */
    public long availableEnergy() {
        DistortionMatrixBlockEntity matrix = linkedMatrix();
        return matrix == null ? 0 : matrix.getEnergy();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (energySource != null) output.store("energySource", BlockPos.CODEC, energySource);
        if (wormholePos != null) output.store("wormholePos", BlockPos.CODEC, wormholePos);
        if (sourceEmitter != null) output.store("sourceEmitter", BlockPos.CODEC, sourceEmitter);
        output.putBoolean("working", working);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energySource = input.read("energySource", BlockPos.CODEC).orElse(null);
        wormholePos = input.read("wormholePos", BlockPos.CODEC).orElse(null);
        sourceEmitter = input.read("sourceEmitter", BlockPos.CODEC).orElse(null);
        working = input.getBooleanOr("working", false);
    }
}
