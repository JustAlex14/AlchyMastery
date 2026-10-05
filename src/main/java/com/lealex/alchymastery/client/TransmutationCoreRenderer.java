package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.BookAnimation;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.util.Mth;
import com.lealex.alchymastery.block.entity.TransmutationCoreBlockEntity;
import com.lealex.alchymastery.compound.Transmutation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The transmutation chamber's displays: the input compounds on the left shelf, the outputs on the right shelf
 * (laid out like vanilla shelf items), the target compound floating over the core, and during an operation the
 * taken compounds flying from the left shelf to the core, spinning, turning into the target, and flying to the
 * right shelf (as many items as were taken / made, up to 6 each).
 */
public class TransmutationCoreRenderer
        implements BlockEntityRenderer<TransmutationCoreBlockEntity, TransmutationCoreRenderer.State> {

    private static final int MAX_SHELF_ITEMS = 3;   // a shelf has 3 slots
    private static final int MAX_FLYING = 6;
    private static final double TARGET_HEIGHT = TransmutationCoreBlockEntity.RING_HEIGHT; // above the core's bottom
    // Operation phases (fractions of the operation): fly in, transform at the core, fly out
    private static final float FLY_IN_END = 0.35F, TRANSFORM_AT = 0.5F, FLY_OUT_START = 0.65F;

    public static class State extends BlockEntityRenderState {
        public float time;
        public @Nullable ItemStackRenderState target;
        public @Nullable ItemStackRenderState inputShelfItem, outputShelfItem;
        public int inputShelfCount, outputShelfCount;
        public @Nullable Vec3 inputShelf, outputShelf;       // shelf block corner, relative to the core
        public Direction inputFacing = Direction.NORTH, outputFacing = Direction.NORTH;
        // Operation in progress (progress < 0: none)
        public float progress = -1;
        public @Nullable ItemStackRenderState opInput, opResult;
        public int taken, made;
        // The floating book (vanilla enchanting table animation)
        public float bookTime, bookYRot, bookFlip, bookOpen;
    }

    private final ItemModelResolver itemModelResolver;
    private final SpriteGetter sprites;
    private final BookModel bookModel;

    public TransmutationCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
        this.sprites = context.sprites();
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TransmutationCoreBlockEntity core, State state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(core, state, partialTicks, cameraPosition, breakProgress);
        Level level = core.getLevel();
        state.target = state.inputShelfItem = state.outputShelfItem = state.opInput = state.opResult = null;
        state.inputShelf = state.outputShelf = null;
        state.progress = -1;
        // The book is always there, like on an enchanting table
        BookAnimation book = core.book;
        state.bookTime = book.time + partialTicks;
        state.bookFlip = Mth.lerp(partialTicks, book.oFlip, book.flip);
        state.bookOpen = Mth.lerp(partialTicks, book.oOpen, book.open);
        float turn = book.rot - book.oRot;
        while (turn >= Math.PI) turn -= (float) (Math.PI * 2);
        while (turn < -Math.PI) turn += (float) (Math.PI * 2);
        state.bookYRot = book.oRot + turn * partialTicks;

        if (level == null || !core.isFormed()) return;
        state.time = (level.getGameTime() % 24000L) + partialTicks;
        int seed = (int) core.getBlockPos().asLong();
        BlockPos corePos = core.getBlockPos();

        state.target = item(core.getTargetStack(), ItemDisplayContext.GROUND, level, seed);

        BlockPos inShelf = core.getInputShelf(), outShelf = core.getOutputShelf();
        if (inShelf != null) {
            state.inputShelf = Vec3.atLowerCornerOf(inShelf.subtract(corePos));
            state.inputFacing = shelfFacing(level, inShelf);
            ItemStack input = core.getInputStack();
            state.inputShelfItem = item(input, ItemDisplayContext.ON_SHELF, level, seed + 1);
            state.inputShelfCount = Math.min(MAX_SHELF_ITEMS, input.getCount());
        }
        if (outShelf != null) {
            state.outputShelf = Vec3.atLowerCornerOf(outShelf.subtract(corePos));
            state.outputFacing = shelfFacing(level, outShelf);
            ItemStack output = core.getOutputStack();
            state.outputShelfItem = item(output, ItemDisplayContext.ON_SHELF, level, seed + 2);
            state.outputShelfCount = Math.min(MAX_SHELF_ITEMS, output.getCount());
        }

        if (core.getOpStart() >= 0) {
            state.progress = Math.min(1F, (level.getGameTime() - core.getOpStart() + partialTicks) / core.getOpTicks());
            state.opInput = item(core.getOpInput(), ItemDisplayContext.GROUND, level, seed + 3);
            state.opResult = item(core.getOpResult(), ItemDisplayContext.GROUND, level, seed + 4);
            state.taken = Math.min(MAX_FLYING, core.getOpTaken());
            state.made = Math.min(MAX_FLYING, core.getOpMade());
        }
    }

    private @Nullable ItemStackRenderState item(ItemStack stack, ItemDisplayContext context, Level level, int seed) {
        if (stack.isEmpty()) return null;
        ItemStackRenderState renderState = new ItemStackRenderState();
        itemModelResolver.updateForTopItem(renderState, stack, context, level, null, seed);
        return renderState;
    }

    /** The facing of the shelf at pos: a chamber shell disguised as a shelf, or a real shelf. */
    private static Direction shelfFacing(Level level, BlockPos pos) {
        BlockState state = level.getBlockEntity(pos) instanceof ChamberShellBlockEntity shell ? shell.getDisguise() : level.getBlockState(pos);
        return state.hasProperty(ShelfBlock.FACING) ? state.getValue(ShelfBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        submitBook(state, poseStack, collector);
        Vec3 center = new Vec3(0.5, TARGET_HEIGHT, 0.5);

        // Target floating over the core
        if (state.target != null) {
            float bob = (float) Math.sin(state.time / 10.0) * 0.04F;
            poseStack.pushPose();
            poseStack.translate(center.x, center.y + 0.15 + bob, center.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.time * 2.0F));
            poseStack.scale(0.5F, 0.5F, 0.5F);
            state.target.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // Shelves
        if (state.inputShelf != null && state.inputShelfItem != null) {
            for (int slot = 0; slot < state.inputShelfCount; slot++) {
                submitOnShelf(state, state.inputShelfItem, state.inputShelf, state.inputFacing, slot, poseStack, collector);
            }
        }
        if (state.outputShelf != null && state.outputShelfItem != null) {
            for (int slot = 0; slot < state.outputShelfCount; slot++) {
                submitOnShelf(state, state.outputShelfItem, state.outputShelf, state.outputFacing, slot, poseStack, collector);
            }
        }

        // The operation: left shelf -> core (spin, transform) -> right shelf
        if (state.progress < 0 || state.inputShelf == null || state.outputShelf == null) return;
        float p = state.progress;
        boolean transformed = p >= TRANSFORM_AT;
        ItemStackRenderState flying = transformed ? state.opResult : state.opInput;
        int count = transformed ? state.made : state.taken;
        if (flying == null || count == 0) return;

        for (int i = 0; i < count; i++) {
            Vec3 at;
            double ringAngle = Math.toRadians(state.time * 12.0 + i * (360.0 / count));
            if (p < FLY_IN_END) {
                double t = ease(p / FLY_IN_END);
                Vec3 from = shelfSlot(state.inputShelf, state.inputFacing, i % MAX_SHELF_ITEMS);
                Vec3 to = center.add(Math.cos(ringAngle) * 0.3, 0, Math.sin(ringAngle) * 0.3);
                at = from.lerp(to, t).add(0, Math.sin(t * Math.PI) * 0.4, 0); // a little hop
            } else if (p < FLY_OUT_START) {
                // Spinning ring around the core that tightens, then opens up again after the transformation
                double t = (p - FLY_IN_END) / (FLY_OUT_START - FLY_IN_END);
                double radius = 0.05 + 0.25 * Math.abs(1 - 2 * t);
                at = center.add(Math.cos(ringAngle) * radius, 0, Math.sin(ringAngle) * radius);
            } else {
                double t = ease((p - FLY_OUT_START) / (1 - FLY_OUT_START));
                Vec3 from = center.add(Math.cos(ringAngle) * 0.3, 0, Math.sin(ringAngle) * 0.3);
                Vec3 to = shelfSlot(state.outputShelf, state.outputFacing, i % MAX_SHELF_ITEMS);
                at = from.lerp(to, t).add(0, Math.sin(t * Math.PI) * 0.4, 0);
            }
            poseStack.pushPose();
            poseStack.translate(at.x, at.y, at.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.time * 18.0F + i * 40));
            poseStack.scale(0.35F, 0.35F, 0.35F);
            flying.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /** The enchanting table's book, drawn exactly like vanilla's EnchantTableRenderer. */
    private void submitBook(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.75F, 0.5F);
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

    private static double ease(double t) {
        return t * t * (3 - 2 * t); // smoothstep
    }

    /** Center of a shelf slot (0-2), relative to the core, using vanilla's shelf layout. */
    private static Vec3 shelfSlot(Vec3 shelf, Direction facing, int slot) {
        Vector3f offset = new Vector3f((slot - 1) * 0.3125F, 0, -0.25F);
        new Quaternionf().rotationY((float) Math.toRadians(-facing.toYRot())).transform(offset);
        return shelf.add(0.5 + offset.x, 0.5, 0.5 + offset.z);
    }

    /** One item on a shelf, placed and sized like vanilla's shelf renderer does. */
    private static void submitOnShelf(State state, ItemStackRenderState item, Vec3 shelf, Direction facing, int slot,
                                      PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.translate(shelf.x + 0.5, shelf.y + 0.5, shelf.z + 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate((slot - 1) * 0.3125F, 0, -0.25F);
        poseStack.scale(0.25F, 0.25F, 0.25F);
        AABB box = item.getModelBoundingBox();
        poseStack.translate(0, -box.minY - (box.maxY - box.minY) / 2.0, 0);
        item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TransmutationCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(3.0);
    }
}
