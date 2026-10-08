package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.CondensatorCoreBlock;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchymastery.block.entity.MiniatureHost;
import com.lealex.alchymastery.block.entity.NexusCoreBlockEntity;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.client.miniature.BoxMesh;
import com.lealex.alchyx.client.miniature.Miniatures;
import com.lealex.alchyx.miniature.BlockBox;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import com.lealex.alchymastery.registry.ModRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchyx.animation.ClientAnimations;
import com.lealex.alchyx.animation.ResolvedAnimation;
import com.lealex.alchyx.client.animation.MiniatureAnimation;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/**
 * Draws a nexus's miniatures (the alchemical nexus, the experience nexus: any MiniatureHost): over each machine core, a small copy of that machine's formed chamber
 * (from its own pattern file, so a datapack change shows here too), animated when its stage works. The blocks that
 * never change are one cached mesh per miniature (AlchyX's miniature kit). What moves comes from each machine's own
 * animation file, played in the miniature (AlchyX's MiniatureAnimation): the pistons striking, the book, the
 * conduit waking up, the stand's bottles filling, the hut's crystal, ghost and cat. This renderer itself only adds:
 * <ul>
 *   <li>the items floating over each miniature, larger than life so they can be seen (submitItems);</li>
 *   <li>the miniature wormholes, whose look follows the nexus's own link;</li>
 *   <li>Rift Genesis, the formation.</li>
 * </ul>
 */
public class NexusCoreRenderer<T extends MultiblockCoreBlockEntity & MiniatureHost>
        implements BlockEntityRenderer<T, NexusCoreRenderer.State> {
    public static final float SCALE = 0.25F;       // a 5-wide chamber becomes 1.25 blocks wide

    /** One miniature, ready to draw. */
    public static class Mini {
        public String machine = "";
        public Vec3 offset = Vec3.ZERO;               // machine core position relative to the nexus
        public List<MultiblockPattern.LookCell> cells = List.of();
        public int minY;
        public boolean working;
        public float progress;
        // The blocks that never change, worked out once (AlchyX BoxMesh): drawn at meshAt, lit with light
        public @Nullable BoxMesh mesh;
        public Vec3 meshAt = Vec3.ZERO;
        public int light;
        // What the machine's own animation file shows right now (AlchyX MiniatureAnimation): pistons striking, bottles
        // filling, the book, the conduit, the crystal, figures
        public MiniatureAnimation.@Nullable Frame frame;
        public final List<Vec3> wormholeCells = new ArrayList<>(); // drawn by WormholeRenderer.draw
        public Vec3 focus = Vec3.ZERO; // where the items float (miniature units)
        public double centerX = 0.5, centerZ = 0.5;   // middle of the miniature's footprint (it's centered on the pedestal)
        public @Nullable ItemStackRenderState itemA, itemB;
    }

    public static class State extends BlockEntityRenderState {
        public final List<Mini> minis = new ArrayList<>();
        public float time;
        public boolean linked; // the nexus's wormhole link (its miniature wormholes show it)
        // Rift Genesis (NexusGenesis): ticks since the nexus formed while it plays, -1 otherwise
        public float genesisT = -1;
        public com.lealex.alchymastery.ClientConfig.Genesis genesis = com.lealex.alchymastery.ClientConfig.Genesis.DEFAULT;
        public NexusGenesis.@Nullable Plan plan;
        public final List<NexusGenesis.Veiled> veils = new ArrayList<>(); // still see-through, drawn as veils
        public float now; // game time + partial tick (veils fade from solid as they phase out)
        // The absorbed machine cores, still shown on their pedestals until the warden blast bursts them into a rift
        public final List<MovingBlockRenderState> waitingCores = new ArrayList<>();
        public final List<Vec3> waitingAt = new ArrayList<>();

    }

    /**
     * A miniature's fixed blocks as a mesh and its machine's animation file ready to play in it, kept until its cells
     * change, the game's models are reloaded or new animation files arrive. The owner is what the animation's
     * figures are kept under (their stand-in entities, the book's state).
     */
    private record CachedMesh(List<MultiblockPattern.LookCell> cells, BoxMesh mesh, Vec3 at,
                              @Nullable MiniatureAnimation animation, int animations, Object owner) {}

    /** The pattern (and so the animation file) of a miniature's machine. */
    private static @Nullable Identifier patternOf(String machine) {
        return switch (machine) {
            case "destructuration" -> ModPatterns.DESTRUCTURATION_CHAMBER;
            case "transmutation" -> ModPatterns.TRANSMUTATION_CHAMBER;
            case "reconstruction" -> ModPatterns.RECONSTRUCTION_CHAMBER;
            case "condensator" -> ModPatterns.DISTORTION_CONDENSATOR;
            case "rendering" -> ModPatterns.RENDERING_CAULDRON;
            default -> null;
        };
    }

    private static final Map<MultiblockCoreBlockEntity, Map<String, CachedMesh>> MESHES = new java.util.WeakHashMap<>();

    /**
     * What a cell shows in the mesh: its look, lava's look-alike block, or nothing (drawn another way, or not at all).
     * The cells an animation file changes (pistons, the stand's bottles) are left out by the animation itself.
     */
    private static BlockState fixedLook(BlockState look) {
        if (look.getBlock() instanceof CondensatorCoreBlock || look.getBlock() instanceof com.lealex.alchymastery.block.WormholeBlock) {
            return Blocks.AIR.defaultBlockState(); // no block model: the conduit is a figure of the file, the wormhole is drawn here
        }
        if (look.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return look.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                    ? ModRegistries.MINIATURE_LAVA.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return look;
    }

    private static CachedMesh mesh(MultiblockCoreBlockEntity nexus, ClientLevel level, NexusCoreBlockEntity.Miniature miniature,
                                   BlockPos lightPos) {
        String machine = miniature.machine();
        List<MultiblockPattern.LookCell> cells = miniature.cells();
        Map<String, CachedMesh> byMachine = MESHES.computeIfAbsent(nexus, key -> new java.util.HashMap<>());
        CachedMesh cached = byMachine.get(machine);
        if (cached != null && cached.cells == cells && !cached.mesh.isStale() && cached.animations == ClientAnimations.generation()) return cached;

        // The machine's animation file plays in the miniature: its block moves (pistons), block states (bottles) and
        // figures (the book, the conduit, the hut's crystal, ghost and cat). Floating items stay this renderer's: they
        // are drawn larger than life here, to be seen. Particles and sounds are left off: the nexus has its own (set
        // the last two to true to hear and see the files').
        Identifier pattern = patternOf(machine);
        ResolvedAnimation definition = pattern == null ? null : ClientAnimations.get(pattern);
        MiniatureAnimation animation = null;
        if (definition != null) {
            Map<BlockPos, BlockState> all = new java.util.HashMap<>();
            for (MultiblockPattern.LookCell cell : cells) all.put(cell.offset(), cell.state());
            animation = new MiniatureAnimation(definition, all, miniature.anchors(),
                    Rotation.values()[Math.floorMod(miniature.turn(), 4)], new MiniatureAnimation.Plays(true, false, true, false, false));
        }
        java.util.Set<BlockPos> animated = animation == null ? java.util.Set.of() : animation.changingCells();

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Map<BlockPos, BlockState> looks = new java.util.HashMap<>();
        for (MultiblockPattern.LookCell cell : cells) {
            BlockPos at = cell.offset();
            looks.put(at, animated.contains(at) ? Blocks.AIR.defaultBlockState() : fixedLook(cell.state()));
            minX = Math.min(minX, at.getX()); maxX = Math.max(maxX, at.getX());
            minY = Math.min(minY, at.getY()); maxY = Math.max(maxY, at.getY());
            minZ = Math.min(minZ, at.getZ()); maxZ = Math.max(maxZ, at.getZ());
        }
        if (looks.isEmpty()) {
            minX = minY = minZ = maxX = maxY = maxZ = 0;
        }
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockBox box = BlockBox.of(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, false,
                (x, y, z) -> looks.getOrDefault(min.offset(x, y, z), Blocks.AIR.defaultBlockState()));
        // Cauldrons of distortion fluid (the condensator's ring, the reconstruction's cauldron) show it purple, like
        // the real chambers; the condensator's basin under its core stays water
        BoxMesh.Tints tints = (state, x, y, z) -> state.is(Blocks.WATER_CAULDRON)
                && (machine.equals("reconstruction") || (machine.equals("condensator") && !min.offset(x, y, z).equals(BlockPos.ZERO.below())))
                ? ModRegistries.DISTORTION_FLUID_COLOR : 0;
        cached = new CachedMesh(cells, BoxMesh.build(level, lightPos, box, Integer.MAX_VALUE, tints), Vec3.atLowerCornerOf(min),
                animation, ClientAnimations.generation(), new Object());
        byMachine.put(machine, cached);
        return cached;
    }

    /** What a machine's animation file is told about the miniature of that machine. */
    private static <N extends MultiblockCoreBlockEntity & MiniatureHost> MiniatureAnimation.State animationState(N nexus, String machine) {
        return new MiniatureAnimation.State() {
            @Override
            public boolean working() {
                return nexus.isMachineWorking(machine);
            }

            @Override
            public float progress() {
                return nexus.getMachineProgress(machine);
            }

            // The nexus's own displays (its "linked_player" decides which ghost stands in the hut)
            @Override
            public ItemStack display(String name) {
                return nexus.getAnimationDisplay(name);
            }

            @Override
            public int level(String name) {
                return nexus.getAnimationLevel(name);
            }
        };
    }

    private final ItemModelResolver itemModelResolver;
    private final WormholeRenderer.Parts wormholeParts;

    public NexusCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
        this.wormholeParts = WormholeRenderer.Parts.bake(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T nexus, State state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(nexus, state, partialTicks, cameraPosition, breakProgress);
        state.minis.clear();
        state.veils.clear();
        state.waitingCores.clear();
        state.waitingAt.clear();
        state.plan = null;
        state.genesisT = -1;
        if (!(nexus.getLevel() instanceof ClientLevel level)) return;
        if (!nexus.isFormed()) { // the wave is running: the blocks it veiled wait as see-through veils
            state.genesis = com.lealex.alchymastery.ClientConfig.genesis();
            state.time = (level.getGameTime() % 24000L) + partialTicks;
            state.now = level.getGameTime() + partialTicks;
            MultiblockPattern pattern = nexus.pattern();
            if (pattern == null) return;
            for (MultiblockPattern.Conversion conversion : pattern.conversions(nexus.getBlockPos(), nexus.rotation())) {
                if (level.getBlockEntity(conversion.pos()) instanceof ChamberShellBlockEntity shell && shell.isClientHidden()
                        && shell.getVeiledBy() == nexus.getBlockPos().asLong()) {
                    state.veils.add(new NexusGenesis.Veiled(conversion.pos().immutable(), conversion.pos().subtract(nexus.getBlockPos()),
                            shell.getDisguise(), 0, 0, shell.getConvertedAt()));
                }
                if (level.getBlockEntity(conversion.pos()) instanceof ChamberShellBlockEntity core && core.isHidden() && core.isRestore()) {
                    // an absorbed machine core: it stays on its pedestal until the blast
                    state.waitingCores.add(moving(level, com.lealex.alchyx.multiblock.PreviewLooks.apply(core.getDisguise()), conversion.pos()));
                    state.waitingAt.add(Vec3.atLowerCornerOf(conversion.pos().subtract(nexus.getBlockPos())));
                }
            }
            return;
        }
        long gameTime = level.getGameTime();
        state.time = (gameTime % 24000L) + partialTicks;
        state.now = gameTime + partialTicks;
        int seed = (int) nexus.getBlockPos().asLong();
        state.linked = nexus instanceof com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity powered
                && powered.getEnergySource() != null;

        for (NexusCoreBlockEntity.Miniature miniature : nexus.getMiniatures()) {
            Mini mini = new Mini();
            mini.machine = miniature.machine();
            mini.offset = Vec3.atLowerCornerOf(miniature.offset());
            mini.cells = miniature.cells();
            mini.minY = mini.cells.stream().mapToInt(c -> c.offset().getY()).min().orElse(0);
            mini.focus = Vec3.atLowerCornerOf(miniature.focus());
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (MultiblockPattern.LookCell cell : mini.cells) {
                minX = Math.min(minX, cell.offset().getX()); maxX = Math.max(maxX, cell.offset().getX());
                minZ = Math.min(minZ, cell.offset().getZ()); maxZ = Math.max(maxZ, cell.offset().getZ());
            }
            if (minX <= maxX) {
                mini.centerX = (minX + maxX + 1) / 2.0;
                mini.centerZ = (minZ + maxZ + 1) / 2.0;
            }
            mini.working = nexus.isMachineWorking(mini.machine);
            mini.progress = nexus.getMachineProgress(mini.machine);
            // Lit like the space the miniature stands in (where its invisible machine core is)
            BlockPos lightPos = nexus.getBlockPos().offset(miniature.offset().getX(), 1, miniature.offset().getZ());

            CachedMesh fixed = mesh(nexus, level, miniature, lightPos);
            mini.mesh = fixed.mesh();
            mini.meshAt = fixed.at();
            mini.light = net.minecraft.client.renderer.LevelRenderer.getLightCoords(level, lightPos);
            mini.frame = fixed.animation() == null ? null
                    : fixed.animation().prepare(level, fixed.owner(), animationState(nexus, mini.machine), lightPos, state.time, partialTicks);

            for (MultiblockPattern.LookCell cell : mini.cells) {
                if (cell.state().getBlock() instanceof com.lealex.alchymastery.block.WormholeBlock) {
                    mini.wormholeCells.add(Vec3.atLowerCornerOf(cell.offset()));
                }
            }

            // Items over the miniature's core (or its focus cell)
            mini.itemA = item(nexus.miniatureItemA(mini.machine), level, seed + mini.machine.hashCode());
            mini.itemB = item(nexus.miniatureItemB(mini.machine), level, seed + mini.machine.hashCode() + 1);
            state.minis.add(mini);
        }

        // Rift Genesis, while it plays (from the synced formation time); veiled blocks always end up revealed
        state.genesis = com.lealex.alchymastery.ClientConfig.genesis();
        state.veils.clear();
        state.plan = null;
        long formedAt = nexus.getFormedAt();
        if (formedAt > 0) {
            NexusGenesis.Plan plan = NexusGenesis.plan(level, nexus, state.genesis, state.minis.size());
            float sinceFormed = (gameTime - formedAt) + partialTicks;
            boolean playing = state.genesis.enabled() && sinceFormed >= 0 && sinceFormed < plan.total();
            state.genesisT = playing ? sinceFormed : -1;
            if (playing) {
                state.plan = plan;
                NexusGenesis.reveal(level, plan, state.genesis, sinceFormed, false);
                NexusGenesis.effects(level, nexus.getBlockPos(), state.minis, state.genesis, plan);
                for (NexusGenesis.Veiled v : plan.veiled()) {
                    if (level.getBlockEntity(v.pos()) instanceof ChamberShellBlockEntity shell && shell.isClientHidden()) state.veils.add(v);
                }
                // the machine cores wait on their pedestals (trembling while the nexus charges) until the blast
                for (int index = 0; index < state.minis.size(); index++) {
                    Mini mini = state.minis.get(index);
                    float local = sinceFormed - index * state.genesis.stagger() - com.lealex.alchymastery.ClientConfig.Genesis.CHARGE;
                    if (local >= 0) continue;
                    BlockPos corePos = nexus.getBlockPos().offset((int) mini.offset.x, (int) mini.offset.y, (int) mini.offset.z);
                    if (!(level.getBlockEntity(corePos) instanceof ChamberShellBlockEntity core)) continue;
                    // still until the nexus starts charging, then trembling harder and harder as the blast nears
                    float shake = 0.05F * Mth.clamp(1 + local / com.lealex.alchymastery.ClientConfig.Genesis.CHARGE, 0F, 1F);
                    state.waitingCores.add(moving(level, com.lealex.alchyx.multiblock.PreviewLooks.apply(core.getDisguise()), corePos));
                    state.waitingAt.add(mini.offset.add(shake * Mth.sin(sinceFormed * 13.1F + index), 0, shake * Mth.sin(sinceFormed * 11.7F + index * 2)));
                }
            } else if (NexusGenesis.anyHidden(level, plan)) {
                NexusGenesis.reveal(level, plan, state.genesis, sinceFormed, true); // off, over, or arrived late
            }
        } else {
            state.genesisT = -1;
        }
    }

    private static MovingBlockRenderState moving(ClientLevel level, BlockState state, BlockPos lightPos) {
        return fill(new MovingBlockRenderState(), level, state, lightPos);
    }

    private static MovingBlockRenderState fill(MovingBlockRenderState moving, ClientLevel level, BlockState state, BlockPos lightPos) {
        moving.randomSeedPos = lightPos;
        moving.blockPos = lightPos;
        moving.blockState = state;
        moving.biome = level.getBiome(lightPos);
        moving.cardinalLighting = level.cardinalLighting();
        moving.lightEngine = level.getLightEngine();
        return moving;
    }

    private @Nullable ItemStackRenderState item(ItemStack stack, ClientLevel level, int seed) {
        if (stack.isEmpty()) return null;
        ItemStackRenderState renderState = new ItemStackRenderState();
        itemModelResolver.updateForTopItem(renderState, stack.copyWithCount(1), ItemDisplayContext.GROUND, level, null, seed);
        return renderState;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        NexusGenesis.Plan plan = state.plan;
        float t = state.genesisT;

        // the absorbed machine cores, waiting on their pedestals for the blast (in their preview look: cores drawn by
        // their own renderer, like the condensator's conduit, have an empty block model)
        for (int i = 0; i < state.waitingCores.size(); i++) {
            Vec3 at = state.waitingAt.get(i);
            poseStack.pushPose();
            poseStack.translate(at.x, at.y, at.z);
            collector.submitMovingBlock(poseStack, state.waitingCores.get(i));
            poseStack.popPose();
        }
        // the waiting blocks (during the wave and until each comes back): see-through veils, breathing
        net.minecraft.client.multiplayer.ClientLevel veilLevel = net.minecraft.client.Minecraft.getInstance().level;
        for (NexusGenesis.Veiled v : state.veils) {
            if (veilLevel == null) break;
            int veilAlpha = (int) (255 * NexusGenesis.veilAlpha(state.genesis, v, state.now));
            if (veilAlpha <= 0) continue;
            poseStack.pushPose();
            poseStack.translate(v.relative().getX(), v.relative().getY(), v.relative().getZ());
            com.lealex.alchyx.client.Veil.submit(poseStack, collector, veilLevel, v.pos(), v.look(), veilAlpha << 24 | 0xD8C4FF);
            poseStack.popPose();
        }
        if (t >= 0 && plan != null) {
            // the blocks' own rifts
            for (NexusGenesis.Veiled v : plan.veiled()) {
                if (v.revealAt() > t) break;
                float open = NexusGenesis.blockRift(state.genesis, t, v);
                if (open <= 0) continue;
                poseStack.pushPose();
                poseStack.translate(v.relative().getX() + 0.5, v.relative().getY() + 0.5, v.relative().getZ() + 0.5);
                com.lealex.alchyx.client.RiftDecal.submit(poseStack, collector, camera, 1.1F, v.variant(), open, NexusGenesis.GLOW);
                poseStack.popPose();
            }
            // each core's rift
            for (int index = 0; index < state.minis.size(); index++) {
                float open = NexusGenesis.coreRift(state.genesis, t, index);
                if (open <= 0) continue;
                Mini mini = state.minis.get(index);
                poseStack.pushPose();
                poseStack.translate(mini.offset.x + 0.5, NexusCoreBlockEntity.MINIATURE_HEIGHT + NexusGenesis.RIFT_HEIGHT, mini.offset.z + 0.5);
                com.lealex.alchyx.client.RiftDecal.submit(poseStack, collector, camera, state.genesis.riftSize(), index, open, NexusGenesis.GLOW);
                poseStack.popPose();
            }
        }
        for (int index = 0; index < state.minis.size(); index++) {
            Mini mini = state.minis.get(index);
            // Rift Genesis: the miniature comes out of its core's rift
            NexusGenesis.Pose pose = t >= 0 ? NexusGenesis.pose(state.genesis, t, index, SCALE) : new NexusGenesis.Pose(SCALE, 0, 0);
            if (pose == null) continue; // still behind its rift
            poseStack.pushPose();
            // Miniature space: 1 unit = 1 block of the real chamber; its core cell at (0, 0, 0)
            // On the pedestal, in place of its (now invisible) machine core
            poseStack.translate(mini.offset.x + 0.5, NexusCoreBlockEntity.MINIATURE_HEIGHT + pose.lift(), mini.offset.z + 0.5);
            if (pose.yaw() != 0) poseStack.mulPose(Axis.YP.rotationDegrees(pose.yaw()));
            poseStack.scale(pose.scale(), pose.scale(), pose.scale());
            poseStack.translate(-mini.centerX, -mini.minY, -mini.centerZ); // centered on the pedestal

            if (mini.mesh != null) {
                poseStack.pushPose();
                poseStack.translate(mini.meshAt.x, mini.meshAt.y, mini.meshAt.z);
                Miniatures.submit(poseStack, collector, camera, mini.mesh, mini.light, List.of());
                poseStack.popPose();
            }
            if (mini.frame != null) MiniatureAnimation.submit(mini.frame, poseStack, collector, camera);
            if (!mini.wormholeCells.isEmpty()) {
                com.lealex.alchymastery.block.WormholeBlock.Look look = mini.working
                        ? com.lealex.alchymastery.block.WormholeBlock.Look.ACTIVE
                        : state.linked ? com.lealex.alchymastery.block.WormholeBlock.Look.LINKED
                        : com.lealex.alchymastery.block.WormholeBlock.Look.UNLINKED;
                for (Vec3 cell : mini.wormholeCells) {
                    poseStack.pushPose();
                    poseStack.translate(cell.x, cell.y, cell.z);
                    WormholeRenderer.draw(wormholeParts, WormholeRenderer.WORMHOLE, look, state.time, state.lightCoords, null, poseStack, collector, camera);
                    poseStack.popPose();
                }
            }
            poseStack.pushPose();
            poseStack.translate(mini.focus.x, mini.focus.y, mini.focus.z);
            submitItems(state, mini, poseStack, collector);
            poseStack.popPose();
            poseStack.popPose();
        }
    }

    /** Items over the miniature's core, in miniature units (scale 1 = a full-size item). */
    private static void submitItems(State state, Mini mini, PoseStack poseStack, SubmitNodeCollector collector) {
        float bob = (float) Math.sin(state.time / 10.0) * 0.1F;
        switch (mini.machine) {
            case "destructuration" -> submitItem(mini.itemA, poseStack, collector, 0, 1.4 + bob, 0,
                    state.time * (mini.working ? 3F : 0.8F), 1.6F);
            case "transmutation" -> {
                if (mini.working) {
                    boolean transformed = mini.progress >= 0.5F;
                    submitItem(transformed ? mini.itemB : mini.itemA, poseStack, collector, 0, 1.8 + bob, 0, state.time * 12F, 1.4F);
                } else {
                    submitItem(mini.itemB, poseStack, collector, 0, 1.8 + bob, 0, state.time * 2F, 1.4F);
                }
            }
            case "reconstruction" -> {
                if (mini.working && mini.progress < 0.8F) {
                    float t = mini.progress / 0.8F;
                    double angle = Math.toRadians(state.time * (6 + 18 * t));
                    double radius = 0.8 * (1 - t);
                    submitItem(mini.itemA, poseStack, collector, Math.cos(angle) * radius, 1.3 + bob, Math.sin(angle) * radius,
                            state.time * 10, 1.2F * (1 - 0.3F * t));
                } else if (mini.working) {
                    submitItem(mini.itemB, poseStack, collector, 0, 1.3 + bob, 0, state.time * 4, 1.6F * (mini.progress - 0.8F) / 0.2F);
                } else {
                    submitItem(mini.itemB, poseStack, collector, 0, 1.3 + bob, 0, state.time * 1.5F, 1.6F);
                }
            }
            // The essence being rendered, spinning fast over the hut's pedestal (where the end crystal is)
            case "rendering" -> {
                if (mini.working) submitItem(mini.itemA, poseStack, collector, 0, 2.0 + bob, 0, state.time * 8F, 1.2F); // above the crystal
            }
            default -> {}
        }
    }

    private static void submitItem(@Nullable ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector,
                                   double x, double y, double z, float spinDegrees, float scale) {
        if (item == null || scale <= 0.001F) return;
        poseStack.pushPose();
        poseStack.translate(0.5 + x, y, 0.5 + z);
        poseStack.mulPose(Axis.YP.rotationDegrees(spinDegrees));
        poseStack.scale(scale, scale, scale);
        item.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    // The miniatures float over the machine cores, 2 blocks around the nexus; while forming, their chambers unfold
    // and their rifts (NexusGenesis)
    @Override
    public AABB getRenderBoundingBox(T nexus) {
        return new AABB(nexus.getBlockPos()).inflate(6, 1, 6).expandTowards(0, 6, 0);
    }
}
