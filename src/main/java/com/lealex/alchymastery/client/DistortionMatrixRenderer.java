package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the matrix's fuel floating around it, in the air ring of the middle layer.
 * One copy per 16 items, up to 4, orbiting slowly and bobbing, plus the item currently burning.
 *
 * 26.1 renderers work in two steps: extractRenderState copies what's needed from the block entity,
 * then submit draws from that copy (it never touches the block entity itself).
 */
public class DistortionMatrixRenderer
        implements BlockEntityRenderer<DistortionMatrixBlockEntity, DistortionMatrixRenderer.State> {

    private static final int MAX_COPIES = 4;
    private static final int ITEMS_PER_COPY = 16;
    private static final double ORBIT_RADIUS = 1.0;      // blocks from the matrix's center
    private static final float ORBIT_SPEED = 1.5F;       // degrees per tick
    private static final float SCALE = 0.75F;

    public static class State extends BlockEntityRenderState {
        public List<ItemStackRenderState> items = List.of();
        public float time;
        // The item currently being absorbed
        public @Nullable ItemStackRenderState burning;
    }

    private final ItemModelResolver itemModelResolver;

    public DistortionMatrixRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DistortionMatrixBlockEntity matrix, State state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(matrix, state, partialTicks, cameraPosition, breakProgress);
        Level level = matrix.getLevel();
        state.time = level == null ? 0 : (level.getGameTime() % 24000L) + partialTicks;
        state.items = new ArrayList<>();
        state.burning = null;

        ItemStack burningItem = matrix.getBurningItem();
        if (matrix.isFormed() && !burningItem.isEmpty() && level != null) {
            state.burning = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(state.burning, burningItem, ItemDisplayContext.GROUND, level, null,
                    (int) matrix.getBlockPos().asLong() - 1);
        }

        ItemStack fuel = matrix.getFuelStack();
        if (!matrix.isFormed() || fuel.isEmpty()) return;

        int copies = Math.min(MAX_COPIES, (fuel.getCount() + ITEMS_PER_COPY - 1) / ITEMS_PER_COPY);
        int seed = (int) matrix.getBlockPos().asLong();
        for (int i = 0; i < copies; i++) {
            ItemStackRenderState itemState = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(itemState, fuel, ItemDisplayContext.GROUND, level, null, seed + i);
            state.items.add(itemState);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        // The burning item and the floating copies share the ring, evenly spaced: the burning item takes slot 0
        List<ItemStackRenderState> ring = new ArrayList<>();
        if (state.burning != null) ring.add(state.burning);
        ring.addAll(state.items);

        int count = ring.size();
        for (int i = 0; i < count; i++) {
            float angle = state.time * ORBIT_SPEED + i * (360.0F / count);
            double radians = Math.toRadians(angle);
            float bob = (float) Math.sin((state.time + i * 15) / 12.0) * 0.08F;

            poseStack.pushPose();
            // (0.5, 0.5, 0.5) is the matrix's center; the items circle around it in the empty ring
            poseStack.translate(0.5 + Math.cos(radians) * ORBIT_RADIUS, 0.25 + bob, 0.5 + Math.sin(radians) * ORBIT_RADIUS);
            poseStack.mulPose(Axis.YP.rotationDegrees(-angle * 2.0F));
            poseStack.scale(SCALE, SCALE, SCALE);
            // Full brightness: the fuel glows inside the chamber
            ring.get(i).submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    // The items orbit outside the matrix block: cull with a box that covers the orbit (instead of drawing it even
    // when the whole chamber is off screen)
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity matrix) {
        return new net.minecraft.world.phys.AABB(matrix.getBlockPos()).inflate(ORBIT_RADIUS + 0.5);
    }
}
