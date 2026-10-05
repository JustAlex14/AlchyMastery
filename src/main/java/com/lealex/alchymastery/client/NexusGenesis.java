package com.lealex.alchymastery.client;

import com.lealex.alchymastery.ClientConfig;
import com.lealex.alchymastery.block.entity.NexusCoreBlockEntity;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.client.WaveEffects;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * "Rift Genesis": what a nexus plays when it forms (client only; timings in alchymastery-client.toml, see
 * ClientConfig.Genesis). The wave's blocks shattered and stayed invisible (the nexus veils its wave, see AlchyX
 * MultiblockCoreBlockEntity#veilsWave); they wait as see-through veils. Then:
 * <ol>
 *   <li>each machine core in turn ({@code stagger} apart) tears a rift open over its pedestal, its miniature chamber
 *       comes out of it (growing and spinning, fragments of its blocks flying out), and the rift closes behind it
 *       with a thump, a flash and a ring of sparks;</li>
 *   <li>the nexus's blocks come back, each through a small rift of its own, from the middle outward;</li>
 *   <li>glyphs stream from the miniatures into the nexus, a last flash.</li>
 * </ol>
 * Timing starts at getFormedAt (synced), so everyone nearby sees the same moment. Veiled blocks are always revealed:
 * at once when the animation is off, over, or the player arrived late.
 */
public final class NexusGenesis {
    private NexusGenesis() {}

    public static final int GLOW = 0xB98CFF;
    public static final double RIFT_HEIGHT = 0.75; // a core's rift: its middle above the pedestal top
    private static final int SETTLE_TICKS = 10;

    // ---- The miniatures ----

    /** Where a miniature is drawn: scale, extra turn (degrees), lift (blocks). */
    public record Pose(float scale, float yaw, float lift) {}

    /** Miniature {@code index}'s pose {@code t} ticks after forming; null while it hasn't come out yet. */
    public static @Nullable Pose pose(ClientConfig.Genesis c, float t, int index, float base) {
        float local = t - index * c.stagger() - ClientConfig.Genesis.CHARGE - c.riftOpen();
        if (local < 0) return null;
        if (local < c.emerge()) { // out of the rift: growing, spinning, sinking onto the pedestal
            float x = local / c.emerge();
            return new Pose(Math.max(0.001F, base * easeOutBack(x)), 360F * c.emergeSpin() * (1 - x) * (1 - x),
                    (float) RIFT_HEIGHT * (1 - x) * (1 - x));
        }
        local -= c.emerge();
        if (local < SETTLE_TICKS) {
            float x = local / SETTLE_TICKS;
            return new Pose(base * (1 + 0.18F * (1 - x) * Mth.sin(x * Mth.PI * 3)), 0, 0);
        }
        return new Pose(base, 0, 0);
    }

    /** How open miniature {@code index}'s rift is (0 closed / none, 1 torn open). */
    public static float coreRift(ClientConfig.Genesis c, float t, int index) {
        float local = t - index * c.stagger() - ClientConfig.Genesis.CHARGE;
        if (local < 0) return 0;
        if (local < c.riftOpen()) return local / c.riftOpen();
        local -= c.riftOpen();
        if (local < c.emerge()) return 1;
        local -= c.emerge();
        return local < c.riftClose() ? 1 - local / c.riftClose() : 0;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F, c3 = c1 + 1, y = x - 1;
        return 1 + c3 * y * y * y + c1 * y * y;
    }

    // ---- The veiled blocks ----

    /**
     * A veiled block of the nexus: where (relative to the nexus), what it looks like, when it comes back, and when
     * it phased out (game time: it fades from solid to see-through over PHASE_TICKS).
     */
    public record Veiled(BlockPos pos, BlockPos relative, BlockState look, int revealAt, int variant, long phasedAt) {}

    public static final float PHASE_TICKS = 24;

    /** The veil's opacity now: solid when it just phased out, fading to the configured see-through. */
    public static float veilAlpha(ClientConfig.Genesis c, Veiled v, float now) {
        float x = Mth.clamp((now - v.phasedAt()) / PHASE_TICKS, 0, 1);
        float settled = c.veilOpacity() * (0.75F + 0.25F * Mth.sin(now * 0.15F));
        return Mth.lerp(x * x * (3 - 2 * x), 1F, settled);
    }

    private static final Map<MultiblockCoreBlockEntity, java.util.Optional<BlockPos>> WORMHOLES = new WeakHashMap<>();

    /** The nexus's wormhole (the block energy comes in by), found in its pattern; null if none. */
    public static @Nullable BlockPos wormhole(ClientLevel level, MultiblockCoreBlockEntity nexus) {
        return WORMHOLES.computeIfAbsent(nexus, n -> {
            MultiblockPattern pattern = n.pattern();
            if (pattern == null) return java.util.Optional.empty();
            for (BlockPos pos : pattern.allPositions(n.getBlockPos(), n.rotation())) {
                if (level.getBlockState(pos).getBlock() instanceof com.lealex.alchymastery.block.WormholeBlock) return java.util.Optional.of(pos.immutable());
            }
            return java.util.Optional.empty();
        }).orElse(null);
    }


    /** The plan for one formation: its veiled blocks (in reveal order), when it's all over. */
    public record Plan(long formedAt, List<Veiled> veiled, int revealEnd, int total) {}

    private static final Map<MultiblockCoreBlockEntity, Plan> PLANS = new WeakHashMap<>();

    /** The plan for this nexus's last formation (veiled blocks found in its pattern, reveal times from the config). */
    public static Plan plan(ClientLevel level, MultiblockCoreBlockEntity nexus, ClientConfig.Genesis c, int machines) {
        Plan plan = PLANS.get(nexus);
        if (plan != null && plan.formedAt == nexus.getFormedAt()) return plan;
        List<Veiled> veiled = new ArrayList<>();
        MultiblockPattern pattern = nexus.pattern();
        BlockPos origin = nexus.getBlockPos();
        int start = c.revealStart(machines);
        if (pattern != null) {
            for (MultiblockPattern.Conversion conversion : pattern.conversions(origin, nexus.rotation())) {
                BlockPos pos = conversion.pos();
                if (!(level.getBlockEntity(pos) instanceof ChamberShellBlockEntity shell) || shell.getVeiledBy() != origin.asLong()
                        || shell.isHidden()) continue; // invisible parts (the absorbed machine cores) have nothing to reveal
                double distance = Math.sqrt(pos.distSqr(origin));
                long hash = pos.asLong() * 0x9E3779B97F4A7C15L;
                int at = start + (int) Math.round(distance * c.revealSpread()) + (int) ((hash >>> 60) & 3);
                veiled.add(new Veiled(pos.immutable(), pos.subtract(origin), shell.getDisguise(), at, (int) ((hash >>> 56) & 3),
                        shell.getConvertedAt()));
            }
        }
        veiled.sort(Comparator.comparingInt(Veiled::revealAt));
        int revealEnd = veiled.isEmpty() ? start : veiled.getLast().revealAt() + c.revealRift();
        plan = new Plan(nexus.getFormedAt(), List.copyOf(veiled), revealEnd, revealEnd + c.bind());
        PLANS.put(nexus, plan);
        return plan;
    }

    /** How open a veiled block's rift is at {@code t} (0 none): opens, the block comes back halfway, closes. */
    public static float blockRift(ClientConfig.Genesis c, float t, Veiled v) {
        float x = (t - v.revealAt()) / c.revealRift();
        return x <= 0 || x >= 1 ? 0 : Mth.sin(x * Mth.PI);
    }

    /** True once a veiled block should be visible again. */
    public static boolean revealed(ClientConfig.Genesis c, float t, Veiled v) {
        return t >= v.revealAt() + c.revealRift() / 2F;
    }

    /**
     * Brings back every veiled block whose time has come (all of them when {@code all}): unhidden, with its
     * materializing sparks and a chime.
     */
    public static void reveal(ClientLevel level, Plan plan, ClientConfig.Genesis c, float t, boolean all) {
        for (Veiled v : plan.veiled()) {
            if (!all && !revealed(c, t, v)) break; // in reveal order
            if (level.getBlockEntity(v.pos()) instanceof ChamberShellBlockEntity shell && shell.isClientHidden()) {
                shell.setClientHidden(false);
                if (!all) {
                    WaveEffects.materialize(level, v.pos(), v.look(), false);
                    Vec3 at = Vec3.atCenterOf(v.pos());
                    sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.8F + level.getRandom().nextFloat() * 0.6F, c);
                }
            }
        }
    }

    /** True if one of the plan's blocks is still hidden (needs revealing). */
    public static boolean anyHidden(ClientLevel level, Plan plan) {
        for (Veiled v : plan.veiled()) {
            if (level.getBlockEntity(v.pos()) instanceof ChamberShellBlockEntity shell && shell.isClientHidden()) return true;
        }
        return false;
    }

    // ---- Particles and sounds, once per tick (caught up when frames skip ticks) ----

    private static final Map<BlockPos, Long> LAST_TICK = new HashMap<>();

    public static void effects(ClientLevel level, BlockPos nexus, List<NexusCoreRenderer.Mini> minis, ClientConfig.Genesis c,
                               Plan plan) {
        long now = level.getGameTime() - plan.formedAt();
        Long last = LAST_TICK.get(nexus);
        long from = last == null || last > now || now - last > 10 ? now : last + 1;
        for (long tick = from; tick <= now; tick++) tick(level, nexus, minis, c, plan, (int) tick);
        LAST_TICK.put(nexus.immutable(), now);
        if (LAST_TICK.size() > 64) LAST_TICK.clear();
    }

    private static void tick(ClientLevel level, BlockPos nexus, List<NexusCoreRenderer.Mini> minis, ClientConfig.Genesis c,
                             Plan plan, int t) {
        RandomSource random = level.getRandom();
        float density = c.particles();
        for (int i = 0; i < minis.size(); i++) {
            NexusCoreRenderer.Mini mini = minis.get(i);
            Vec3 rift = Vec3.atLowerCornerOf(nexus).add(mini.offset.x + 0.5, NexusCoreBlockEntity.MINIATURE_HEIGHT + RIFT_HEIGHT, mini.offset.z + 0.5);
            int local = t - i * c.stagger() - ClientConfig.Genesis.CHARGE; // 0 = the blast that tears the rift open
            int emergeAt = c.riftOpen(), closeAt = emergeAt + c.emerge(), doneAt = closeAt + c.riftClose();
            // the nexus core charges (energy drawn into it), then blasts the rift open like a warden's sonic boom
            Vec3 core = Vec3.atCenterOf(nexus).add(0, 0.3, 0);
            if (local == -ClientConfig.Genesis.CHARGE) sound(level, core, SoundEvents.WARDEN_SONIC_CHARGE, 0.7F, 1.3F, c);
            if (local >= -ClientConfig.Genesis.CHARGE && local < 0) {
                for (int k = 0; k < count(3, density, random); k++) {
                    double angle = random.nextDouble() * Math.PI * 2, r = 1.2 + random.nextDouble() * 0.6;
                    // an enchant glyph travels from (position + velocity) to the position: into the core
                    level.addParticle(ParticleTypes.ENCHANT, core.x, core.y, core.z, Math.cos(angle) * r, random.nextDouble() - 0.3, Math.sin(angle) * r);
                }
            }
            if (local == 0) { // the machine core, waiting on its pedestal, bursts apart into the rift
                Vec3 at = Vec3.atCenterOf(nexus).add(mini.offset.x, mini.offset.y, mini.offset.z);
                for (var cell : mini.cells) {
                    if (!cell.offset().equals(BlockPos.ZERO) || cell.state().isAir()) continue;
                    for (int k = 0; k < count(24, density, random); k++) {
                        Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6 + 0.2, random.nextGaussian()).normalize().scale(0.1 + random.nextDouble() * 0.2);
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, com.lealex.alchyx.multiblock.PreviewLooks.apply(cell.state())), at.x, at.y, at.z, v.x, v.y, v.z);
                    }
                }
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.4F, c);
            }
            if (local == 0) { // the blast: a line of sonic booms from the core to the rift
                sound(level, core, SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 1.2F, c);
                Vec3 path = rift.subtract(core);
                int steps = Math.max(1, (int) Math.ceil(path.length() / 0.6));
                if (density > 0) {
                    for (int k = 1; k <= steps; k++) {
                        Vec3 p = core.add(path.scale(k / (double) steps));
                        level.addParticle(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 0, 0, 0);
                    }
                }
            }
            if (local < 0 || local > doneAt) continue;
            if (local >= 0 && local < emergeAt) { // tearing open: distortion spirals into the rift
                for (int k = 0; k < count(3, density, random); k++) {
                    double angle = (t * 0.35 + k * 2.1) % (Math.PI * 2) + random.nextDouble() * 0.6;
                    double r = 1.8 + random.nextDouble();
                    Vec3 p = rift.add(Math.cos(angle) * r, random.nextDouble() * 1.6 - 0.8, Math.sin(angle) * r);
                    level.addParticle(ParticleTypes.PORTAL, p.x, p.y, p.z, (rift.x - p.x) * 0.5, (rift.y - p.y) * 0.5, (rift.z - p.z) * 0.5);
                }
            }
            if (local == emergeAt) {
                sound(level, rift, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F, c);
                sound(level, rift, SoundEvents.END_PORTAL_FRAME_FILL, 1.0F, 0.7F, c);
                burst(level, rift, ParticleTypes.REVERSE_PORTAL, count(30, density, random), 0.3, random);
            }
            if (local >= emergeAt && local < closeAt && !mini.cells.isEmpty()) { // coming out: fragments of its blocks fly off
                float x = (local - emergeAt) / (float) Math.max(1, c.emerge());
                for (int k = 0; k < count(Math.round(6 * (1 - x)) + 1, density, random); k++) {
                    var cell = mini.cells.get(random.nextInt(mini.cells.size()));
                    if (cell.state().isAir() || !cell.state().getFluidState().isEmpty()) continue;
                    Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5 + 0.3, random.nextGaussian()).normalize().scale(0.15 + random.nextDouble() * 0.15);
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, cell.state()), rift.x, rift.y, rift.z, v.x, v.y, v.z);
                    if (random.nextFloat() < 0.5F) level.addParticle(ParticleTypes.REVERSE_PORTAL, rift.x, rift.y, rift.z, v.x * 0.5, v.y * 0.5, v.z * 0.5);
                }
            }
            if (local == doneAt) { // the machine's own void particles burst out as it settles
                net.minecraft.core.particles.ParticleOptions themed = themed(mini.machine);
                if (themed != null) burst(level, rift.add(0, -0.4, 0), themed, count(14, density, random), 0.12, random);
            }
            if (local == doneAt) { // the rift seals: a resonance, a flash, a ring of sparks
                sound(level, rift, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.4F, c);
                if (density > 0) level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GLOW), rift.x, rift.y, rift.z, 0, 0, 0);
                int ring = count(24, density, random);
                for (int k = 0; k < ring; k++) {
                    double angle = k * Math.PI * 2 / Math.max(1, ring);
                    level.addParticle(ParticleTypes.END_ROD, rift.x, rift.y, rift.z, Math.cos(angle) * 0.16, 0.01, Math.sin(angle) * 0.16);
                }
            }
        }
        // the blocks' rifts: a little void as each opens
        for (Veiled v : plan.veiled()) {
            if (t == v.revealAt() && density > 0) {
                Vec3 at = Vec3.atCenterOf(v.pos());
                burst(level, at, ParticleTypes.REVERSE_PORTAL, count(5, density, random), 0.1, random);
            }
            if (v.revealAt() > t) break;
        }
        // the finale
        int start = plan.revealEnd();
        if (c.bind() <= 0 || t < start || t >= start + c.bind()) return;
        Vec3 center = Vec3.atLowerCornerOf(nexus).add(0.5, 1.2, 0.5);
        if (t == start) sound(level, center, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.4F, c);
        for (NexusCoreRenderer.Mini mini : minis) {
            Vec3 from = Vec3.atLowerCornerOf(nexus).add(mini.offset.x + 0.5, NexusCoreBlockEntity.MINIATURE_HEIGHT + 0.8, mini.offset.z + 0.5);
            for (int k = 0; k < count(2, density, random); k++) {
                level.addParticle(ParticleTypes.ENCHANT, center.x, center.y, center.z,
                        from.x - center.x + (random.nextDouble() - 0.5) * 0.4, from.y - center.y, from.z - center.z + (random.nextDouble() - 0.5) * 0.4);
            }
        }
        if (t == start + c.bind() - 1) {
            sound(level, center, SoundEvents.END_PORTAL_SPAWN, 0.5F, 1.2F, c);
            if (density > 0) {
                level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GLOW), center.x, center.y, center.z, 0, 0, 0);
                level.addParticle(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 0, 0, 0);
            }
            burst(level, center, ParticleTypes.REVERSE_PORTAL, count(80, density, random), 0.5, random);
        }
    }

    /** A machine's own void particle (registry/ModParticles), by its miniature's name. */
    private static net.minecraft.core.particles.@Nullable ParticleOptions themed(String machine) {
        return switch (machine) {
            case "destructuration" -> com.lealex.alchymastery.registry.ModParticles.VOID_EMBER.get();
            case "transmutation" -> com.lealex.alchymastery.registry.ModParticles.VOID_GLYPH.get();
            case "condensator" -> com.lealex.alchymastery.registry.ModParticles.VOID_DROPLET.get();
            case "reconstruction" -> com.lealex.alchymastery.registry.ModParticles.VOID_POLLEN.get();
            case "rendering" -> com.lealex.alchymastery.registry.ModParticles.VOID_WISP.get();
            default -> null;
        };
    }

    private static int count(int base, float density, RandomSource random) {
        float n = base * density;
        return (int) n + (random.nextFloat() < n - (int) n ? 1 : 0);
    }

    private static void burst(ClientLevel level, Vec3 at, ParticleOptions particle, int count, double speed, RandomSource random) {
        for (int k = 0; k < count; k++) {
            Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize().scale(speed * random.nextDouble());
            level.addParticle(particle, at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    private static void sound(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch, ClientConfig.Genesis c) {
        if (c.sounds()) level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false);
    }
}
