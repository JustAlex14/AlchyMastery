package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.CondensatorCoreBlock;
import com.lealex.alchymastery.block.ReconstructionCoreBlock;
import com.lealex.alchymastery.block.TransmutationCoreBlock;
import com.lealex.alchymastery.block.entity.BookAnimation;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchymastery.block.entity.MiniatureHost;
import com.lealex.alchymastery.block.entity.NexusCoreBlockEntity;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import com.lealex.alchymastery.registry.ModRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.ConduitRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import com.lealex.alchymastery.multiblock.ModPatterns;
import com.lealex.alchyx.animation.ClientAnimations;
import com.lealex.alchyx.animation.Effect;
import com.lealex.alchyx.animation.ResolvedAnimation;
import com.lealex.alchyx.animation.Target;
import com.lealex.alchyx.client.animation.AnimationEngine;
import com.lealex.alchyx.client.animation.FigureDrawer;
import net.minecraft.world.level.block.Rotation;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/**
 * Draws a nexus's miniatures (the alchemical nexus, the experience nexus: any MiniatureHost): over each machine core, a small copy of that machine's formed chamber
 * (from its own pattern file, so a datapack change shows here too), animated when its stage works:
 * <ul>
 *   <li>destructuration: the pistons strike and the input item spins over the core;</li>
 *   <li>transmutation: the book opens and flips, a compound spins and turns into the target's compound;</li>
 *   <li>condensator: the conduit wakes up;</li>
 *   <li>reconstruction: the bottles fill and the target's compound turns into the target item;</li>
 *   <li>rendering: the essence being rendered spins over the hut's pedestal.</li>
 * </ul>
 */
public class NexusCoreRenderer<T extends MultiblockCoreBlockEntity & MiniatureHost>
        implements BlockEntityRenderer<T, NexusCoreRenderer.State> {
    public static final float SCALE = 0.25F;       // a 5-wide chamber becomes 1.25 blocks wide
    private static final int STRIKE_INTERVAL = 20; // ticks between miniature piston strikes

    /** One miniature, ready to draw. */
    public static class Mini {
        public String machine = "";
        public Vec3 offset = Vec3.ZERO;               // machine core position relative to the nexus
        public List<MultiblockPattern.LookCell> cells = List.of();
        public int minY;
        public boolean working;
        public float progress;
        public final List<MovingBlockRenderState> blocks = new ArrayList<>();
        public final List<Vec3> blockOffsets = new ArrayList<>();
        public final List<@Nullable MovingBlockRenderState> heads = new ArrayList<>(); // piston heads (or null)
        public final List<Vec3> headShift = new ArrayList<>();
        public @Nullable Vec3 conduitCell, bookCell;
        public final List<Vec3> wormholeCells = new ArrayList<>(); // drawn by WormholeRenderer.draw
        public Vec3 focus = Vec3.ZERO; // where the items float (miniature units)
        public double centerX = 0.5, centerZ = 0.5;   // middle of the miniature's footprint (it's centered on the pedestal)
        // The rendering hut: its crystal (pedestal + wormhole anchors) and the figures of its animation file
        public @Nullable Vec3 pedestal, wormhole;
        public final List<Vec3> figureFeet = new ArrayList<>();
        public final List<FigureDrawer.Prepared> figures = new ArrayList<>();
        public @Nullable ItemStackRenderState itemA, itemB;
    }

    public static class State extends BlockEntityRenderState {
        public final List<Mini> minis = new ArrayList<>();
        public float time;
        public boolean linked; // the nexus's wormhole link (its miniature wormholes show it)
        public float bookTime, bookYRot, bookFlip, bookOpen;
        public final ConduitRenderState conduit = new ConduitRenderState();
        public final EndCrystalRenderState crystal = new EndCrystalRenderState(); // the rendering hut's
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

    private final ItemModelResolver itemModelResolver;
    private final ConduitRenderer conduitRenderer;
    private final BookModel bookModel;
    private final SpriteGetter sprites;
    private final EndCrystalModel crystalModel;
    private final WormholeRenderer.Parts wormholeParts;

    public NexusCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
        this.conduitRenderer = new ConduitRenderer(context);
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
        this.sprites = context.sprites();
        this.crystalModel = new EndCrystalModel(context.bakeLayer(ModelLayers.END_CRYSTAL));
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

        BookAnimation book = nexus.book();
        if (book != null) {
            state.bookTime = book.time + partialTicks;
            state.bookFlip = Mth.lerp(partialTicks, book.oFlip, book.flip);
            state.bookOpen = Mth.lerp(partialTicks, book.oOpen, book.open);
            float turn = book.rot - book.oRot;
            while (turn >= Math.PI) turn -= (float) (Math.PI * 2);
            while (turn < -Math.PI) turn += (float) (Math.PI * 2);
            state.bookYRot = book.oRot + turn * partialTicks;
        }

        boolean condensing = nexus.isMachineWorking("condensator");
        ConduitRenderState conduit = state.conduit;
        conduit.isActive = condensing;
        conduit.isHunting = condensing;
        conduit.activeRotation = condensing ? state.time * -0.0375F : 0;
        conduit.animTime = state.time;
        conduit.animationPhase = (int) (gameTime / 66 % 3);
        conduit.lightCoords = state.lightCoords;
        conduit.breakProgress = null;

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

            float strike = mini.working ? ChamberShellBlockEntity.strikeExtension((gameTime % STRIKE_INTERVAL) + partialTicks) : 0;
            for (MultiblockPattern.LookCell cell : mini.cells) {
                BlockState look = cell.state();
                Vec3 at = Vec3.atLowerCornerOf(cell.offset());
                if (look.getBlock() instanceof CondensatorCoreBlock) {
                    mini.conduitCell = at; // the conduit is drawn by its own renderer
                    continue;
                }
                if (look.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                    // Liquids have no block model: lava is shown with its look-alike block, other liquids skipped
                    if (!look.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) continue;
                    look = ModRegistries.MINIATURE_LAVA.get().defaultBlockState();
                }
                if (look.getBlock() instanceof com.lealex.alchymastery.block.WormholeBlock) {
                    mini.wormholeCells.add(at); // so is the wormhole
                    continue;
                }
                if (look.getBlock() instanceof TransmutationCoreBlock && book != null) mini.bookCell = at;
                if (look.getBlock() instanceof ReconstructionCoreBlock && look.hasProperty(BrewingStandBlock.HAS_BOTTLE[0])) {
                    int bottles = mini.working ? 1 + Math.min(2, (int) (mini.progress * 3)) : 0;
                    for (int i = 0; i < 3; i++) look = look.setValue(BrewingStandBlock.HAS_BOTTLE[i], i < bottles);
                }
                MovingBlockRenderState head = null;
                Vec3 shift = Vec3.ZERO;
                if (look.getBlock() instanceof PistonBaseBlock && strike > 0) {
                    Direction facing = look.getValue(PistonBaseBlock.FACING);
                    look = look.setValue(PistonBaseBlock.EXTENDED, true);
                    BlockState headState = (look.is(ModRegistries.CHAMBER_PART_PISTON.get())
                            ? ModRegistries.CHAMBER_PART_PISTON_HEAD.get() : Blocks.PISTON_HEAD).defaultBlockState()
                            .setValue(PistonHeadBlock.FACING, facing)
                            .setValue(PistonHeadBlock.TYPE, PistonType.DEFAULT)
                            .setValue(PistonHeadBlock.SHORT, strike < 0.5F);
                    head = moving(level, headState, lightPos);
                    shift = new Vec3(facing.getStepX() * strike, facing.getStepY() * strike, facing.getStepZ() * strike);
                }
                // Cauldrons of distortion fluid (the condensator's ring, the reconstruction's cauldron) show it
                // purple, like the real chambers; the condensator's basin under its core stays water
                boolean distortion = look.is(Blocks.WATER_CAULDRON)
                        && (mini.machine.equals("reconstruction")
                            || (mini.machine.equals("condensator") && !cell.offset().equals(BlockPos.ZERO.below())));
                MovingBlockRenderState block = distortion ? new Tinted(ModRegistries.DISTORTION_FLUID_COLOR) : new MovingBlockRenderState();
                mini.blocks.add(fill(block, level, look, lightPos));
                mini.blockOffsets.add(at);
                mini.heads.add(head);
                mini.headShift.add(shift);
            }

            // Items over the miniature's core (or its focus cell)
            mini.itemA = item(nexus.miniatureItemA(mini.machine), level, seed + mini.machine.hashCode());
            mini.itemB = item(nexus.miniatureItemB(mini.machine), level, seed + mini.machine.hashCode() + 1);
            if (mini.machine.equals("rendering")) extractHut(nexus, state, level, mini, miniature, partialTicks, cameraPosition);
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

    /**
     * The rendering hut miniature: its crystal, and the "figure" effects of the rendering cauldron's animation file
     * (the ghost, the cat...) placed with the hut pattern's anchors, turned like the miniature. The nexus's own
     * displays (its "linked_player") decide which ghost shows.
     */
    private void extractHut(T nexus, State state, ClientLevel level, Mini mini, NexusCoreBlockEntity.Miniature miniature,
                            float partialTicks, Vec3 camera) {
        Map<String, BlockPos> anchors = miniature.anchors();
        BlockPos pedestal = anchors.get("pedestal"), wormhole = anchors.get("wormhole");
        mini.pedestal = pedestal == null ? null : Vec3.atLowerCornerOf(pedestal);
        mini.wormhole = wormhole == null ? null : Vec3.atLowerCornerOf(wormhole);
        state.crystal.ageInTicks = state.time;
        state.crystal.showsBottom = false;

        ResolvedAnimation hut = ClientAnimations.get(ModPatterns.RENDERING_CAULDRON);
        if (hut == null) return;
        Rotation turn = Rotation.values()[Math.floorMod(miniature.turn(), 4)];
        Vec3 world = Vec3.atLowerCornerOf(nexus.getBlockPos().offset(miniature.offset()))
                .add(0.5, NexusCoreBlockEntity.MINIATURE_HEIGHT, 0.5); // roughly where the miniature stands
        List<Effect> effects = hut.effects();
        for (int i = 0; i < effects.size(); i++) {
            if (!(effects.get(i) instanceof Effect.Figure figure)) continue;
            Vec3 at = cellCenter(figure.at(), anchors, turn);
            if (at == null) continue;
            Vec3 feet = at.add(0, -0.5, 0);
            float yaw;
            Vec3 face = figure.face().map(target -> cellCenter(target, anchors, turn)).orElse(null);
            if (face != null) {
                Vec3 to = face.subtract(feet);
                yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            } else {
                yaw = AnimationEngine.turnYaw(figure.yaw().orElse(0.0F), turn);
            }
            FigureDrawer.Prepared prepared = FigureDrawer.prepare(nexus, 1000 + i, level, figure, world, yaw, mini.working,
                    nexus::getAnimationDisplay, state.time, partialTicks, null); // no head turning: a miniature isn't where its figures look from
            if (prepared != null) {
                mini.figureFeet.add(feet);
                mini.figures.add(prepared);
            }
        }
    }

    /** Center of a target's cell in miniature units (anchors as the miniature carries them; offsets turned). */
    private static @Nullable Vec3 cellCenter(Target target, Map<String, BlockPos> anchors, Rotation turn) {
        String name = target.anchor().orElse("core");
        BlockPos cell = name.equals("core") ? BlockPos.ZERO : anchors.get(name);
        if (cell == null) return null;
        Vec3 o = target.offset();
        Vec3 offset = switch (turn) {
            case CLOCKWISE_90 -> new Vec3(-o.z, o.y, o.x);
            case CLOCKWISE_180 -> new Vec3(-o.x, o.y, -o.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(o.z, o.y, -o.x);
            default -> o;
        };
        return Vec3.atCenterOf(cell).add(offset);
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

    /** A block drawn with one color in place of the biome's (water in a cauldron becomes distortion fluid). */
    private static class Tinted extends MovingBlockRenderState {
        private final int color;

        Tinted(int color) {
            this.color = color;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return color;
        }
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

            for (int i = 0; i < mini.blocks.size(); i++) {
                Vec3 at = mini.blockOffsets.get(i);
                poseStack.pushPose();
                poseStack.translate(at.x, at.y, at.z);
                collector.submitMovingBlock(poseStack, mini.blocks.get(i));
                MovingBlockRenderState head = mini.heads.get(i);
                if (head != null) {
                    Vec3 shift = mini.headShift.get(i);
                    poseStack.translate(shift.x, shift.y, shift.z);
                    collector.submitMovingBlock(poseStack, head);
                }
                poseStack.popPose();
            }
            if (mini.conduitCell != null) {
                poseStack.pushPose();
                poseStack.translate(mini.conduitCell.x, mini.conduitCell.y, mini.conduitCell.z);
                WormholeRenderer.draw(wormholeParts, WormholeRenderer.CONDENSATOR,
                        state.conduit.isActive ? com.lealex.alchymastery.block.WormholeBlock.Look.ACTIVE : com.lealex.alchymastery.block.WormholeBlock.Look.UNLINKED,
                        state.time, state.lightCoords, null, poseStack, collector, camera);
                poseStack.popPose();
            }
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
            if (mini.bookCell != null) submitBook(state, mini.bookCell, poseStack, collector);
            if (mini.machine.equals("rendering")) submitHutFigures(state, mini, poseStack, collector, camera);
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

    /** In miniature units (1 = one block of the hut): the hut's crystal and figures, smaller. */
    private void submitHutFigures(State state, Mini mini, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (mini.pedestal != null) {
            Vec3 base = mini.pedestal.add(0.5, 1.0, 0.5);
            RenderingCoreRenderer.submitCrystal(crystalModel, state.crystal, base, poseStack, collector);
        }
        for (int i = 0; i < mini.figures.size(); i++) {
            Vec3 feet = mini.figureFeet.get(i);
            poseStack.pushPose();
            poseStack.translate(feet.x, feet.y, feet.z);
            FigureDrawer.submit(mini.figures.get(i), poseStack, collector, camera);
            poseStack.popPose();
        }
    }

    /** The enchanting table's book on the miniature transmutation core, like vanilla's EnchantTableRenderer. */
    private void submitBook(State state, Vec3 cell, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.translate(cell.x + 0.5F, cell.y + 0.75F, cell.z + 0.5F);
        poseStack.translate(0.0F, 0.1F + Mth.sin(state.bookTime * 0.1F) * 0.01F, 0.0F);
        poseStack.mulPose(Axis.YP.rotation(-state.bookYRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));
        float ff1 = Mth.frac(state.bookFlip + 0.25F) * 1.6F - 0.3F;
        float ff2 = Mth.frac(state.bookFlip + 0.75F) * 1.6F - 0.3F;
        BookModel.State bookState = BookModel.State.forAnimation(state.bookTime, Mth.clamp(ff1, 0.0F, 1.0F),
                Mth.clamp(ff2, 0.0F, 1.0F), state.bookOpen);
        collector.submitModel(bookModel, bookState, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
                EnchantTableRenderer.BOOK_TEXTURE, sprites, 0, state.breakProgress);
        poseStack.popPose();
    }

    // The miniatures float over the machine cores, 2 blocks around the nexus; while forming, their chambers unfold
    // and their rifts (NexusGenesis)
    @Override
    public AABB getRenderBoundingBox(T nexus) {
        return new AABB(nexus.getBlockPos()).inflate(6, 1, 6).expandTowards(0, 6, 0);
    }
}
