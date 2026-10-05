package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.ReconstructionCoreBlockEntity;
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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Items over the reconstruction chamber's brewing stand. While rebuilding: the compound and the base block circle
 * each other, closing in, then merge into the result, which grows in their place. When idle: the finished items
 * float there if any are waiting, otherwise the loaded inputs hover side by side.
 */
public class ReconstructionCoreRenderer
        implements BlockEntityRenderer<ReconstructionCoreBlockEntity, ReconstructionCoreRenderer.State> {

    private static final float MERGE_AT = 0.8F; // fraction of the operation when the inputs become the result

    public static class State extends BlockEntityRenderState {
        public @Nullable ItemStackRenderState compound, base, result;
        public float time, progress;
        public boolean working, showingOutput;
    }

    private final ItemModelResolver itemModelResolver;

    public ReconstructionCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ReconstructionCoreBlockEntity core, State state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(core, state, partialTicks, cameraPosition, breakProgress);
        state.compound = state.base = state.result = null;
        Level level = core.getLevel();
        if (level == null || !core.isFormed()) return;
        int seed = (int) core.getBlockPos().asLong();
        state.time = (level.getGameTime() % 24000L) + partialTicks;
        state.working = core.isWorking();
        state.progress = core.getDisplayProgress(partialTicks);
        ItemStack output = core.getStack(ReconstructionCoreBlockEntity.OUTPUT);
        state.showingOutput = !state.working && !output.isEmpty();

        if (state.working) {
            state.compound = item(core.getStack(ReconstructionCoreBlockEntity.COMPOUNDS), level, seed);
            state.base = item(core.getStack(ReconstructionCoreBlockEntity.BASE), level, seed + 1);
            state.result = item(core.getDisplayResult(), level, seed + 2);
        } else if (state.showingOutput) {
            state.result = item(output, level, seed + 2);
        } else {
            state.compound = item(core.getStack(ReconstructionCoreBlockEntity.COMPOUNDS), level, seed);
            state.base = item(core.getStack(ReconstructionCoreBlockEntity.BASE), level, seed + 1);
        }
    }

    private @Nullable ItemStackRenderState item(ItemStack stack, Level level, int seed) {
        if (stack.isEmpty()) return null;
        ItemStackRenderState renderState = new ItemStackRenderState();
        itemModelResolver.updateForTopItem(renderState, stack.copyWithCount(1), ItemDisplayContext.GROUND, level, null, seed);
        return renderState;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        double height = ReconstructionCoreBlockEntity.ITEM_HEIGHT;
        float bob = (float) Math.sin(state.time / 10.0) * 0.04F;

        if (state.working) {
            float p = state.progress;
            if (p < MERGE_AT) {
                // Circling each other, closing in, spinning faster
                float t = p / MERGE_AT;
                double radius = 0.35 * (1 - t);
                double angle = Math.toRadians(state.time * (6 + 18 * t));
                float scale = 0.35F * (1 - 0.3F * t);
                submitItem(state.compound, poseStack, collector, Math.cos(angle) * radius, height + bob, Math.sin(angle) * radius, state.time * 10, scale);
                submitItem(state.base, poseStack, collector, -Math.cos(angle) * radius, height + bob, -Math.sin(angle) * radius, state.time * 10, scale);
            } else {
                // The result grows where they met
                float t = (p - MERGE_AT) / (1 - MERGE_AT);
                submitItem(state.result, poseStack, collector, 0, height + bob, 0, state.time * 4, 0.45F * t);
            }
        } else if (state.showingOutput) {
            submitItem(state.result, poseStack, collector, 0, height + bob, 0, state.time * 1.5F, 0.45F);
        } else {
            // Inputs waiting: side by side, turning slowly
            submitItem(state.compound, poseStack, collector, 0.2, height + bob, 0, state.time * 1.5F, 0.3F);
            submitItem(state.base, poseStack, collector, -0.2, height - bob, 0, state.time * 1.5F, 0.3F);
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

    @Override
    public AABB getRenderBoundingBox(ReconstructionCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).expandTowards(0, 2, 0).inflate(0.5, 0, 0.5);
    }
}
