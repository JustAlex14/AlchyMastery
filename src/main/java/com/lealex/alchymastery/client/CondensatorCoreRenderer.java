package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.WormholeBlock;
import com.lealex.alchymastery.block.entity.CondensatorCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the condensator core, a conduit in its prismarine theme (its block model is empty): vanilla's conduit model
 * parts with the condensator's textures (WormholeRenderer.CONDENSATOR skin). A closed shell while idle; while the
 * machine works, the cage spins in a teal vortex and the eye opens on a swirl from water blue to distortion purple.
 */
public class CondensatorCoreRenderer implements BlockEntityRenderer<CondensatorCoreBlockEntity, CondensatorCoreRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public boolean working;
        public float time;
    }

    private final WormholeRenderer.Parts parts;

    public CondensatorCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.parts = WormholeRenderer.Parts.bake(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CondensatorCoreBlockEntity core, State state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(core, state, partialTicks, cameraPosition, breakProgress);
        Level level = core.getLevel();
        state.working = core.isFormed() && core.isWorking();
        state.time = (level == null ? 0 : level.getGameTime() % 24000L) + partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        WormholeRenderer.draw(parts, WormholeRenderer.CONDENSATOR,
                state.working ? WormholeBlock.Look.ACTIVE : WormholeBlock.Look.UNLINKED,
                state.time, state.lightCoords, state.breakProgress, poseStack, collector, camera);
    }

    // The awake conduit bobs up a little: like vanilla's conduit bounds
    @Override
    public AABB getRenderBoundingBox(CondensatorCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(0.0, 0.25, 0.0);
    }
}
