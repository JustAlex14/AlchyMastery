package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.entity.RenderingCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The mini end crystal floating over the rendering cauldron's obsidian pedestal; while an essence renders, the
 * wormhole beams into it. (The ghost and the cat are "figure" effects of the animation file
 * data/alchymastery/machine_animation/rendering_cauldron.json, drawn by AlchyX.)
 */
public class RenderingCoreRenderer implements BlockEntityRenderer<RenderingCoreBlockEntity, RenderingCoreRenderer.State> {
    public static final Identifier CRYSTAL_TEXTURE = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    public static final float CRYSTAL_SCALE = 0.6F; // the vanilla renderer uses 2

    public static class State extends BlockEntityRenderState {
        public boolean crystalVisible, beam;
        public double crystalX, crystalY, crystalZ;   // the crystal's base point, relative to the core
        public double beamX, beamY, beamZ;            // the wormhole's bottom, relative to the core
        public final EndCrystalRenderState crystal = new EndCrystalRenderState();
    }

    private final EndCrystalModel crystalModel;

    public RenderingCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.crystalModel = new EndCrystalModel(context.bakeLayer(ModelLayers.END_CRYSTAL));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RenderingCoreBlockEntity core, State state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(core, state, partialTicks, cameraPosition, breakProgress);
        state.crystalVisible = false;
        Level level = core.getLevel();
        if (level == null || !core.isFormed()) return;
        BlockPos pedestal = core.pedestalPos();
        if (pedestal == null) return;
        BlockPos origin = core.getBlockPos();
        state.crystalVisible = true;
        state.crystalX = pedestal.getX() + 0.5 - origin.getX();
        state.crystalY = pedestal.getY() + 1.0 - origin.getY();
        state.crystalZ = pedestal.getZ() + 0.5 - origin.getZ();
        state.crystal.ageInTicks = (level.getGameTime() % 24000L) + partialTicks;
        state.crystal.showsBottom = false;
        BlockPos wormhole = core.getWormholePos();
        state.beam = wormhole != null && core.isAnimationWorking();
        if (state.beam) {
            state.beamX = wormhole.getX() + 0.5 - origin.getX();
            state.beamY = wormhole.getY() - origin.getY();
            state.beamZ = wormhole.getZ() + 0.5 - origin.getZ();
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.crystalVisible) return;
        submitCrystal(crystalModel, state.crystal, new Vec3(state.crystalX, state.crystalY, state.crystalZ), poseStack, collector);
    }

    /**
     * The mini crystal with its base at {@code base} (its energy arrives as void_siphon particles from the wormhole,
     * PoweredCoreBlockEntity). Also used for the experience nexus's miniature hut.
     */
    public static void submitCrystal(EndCrystalModel model, EndCrystalRenderState crystal, Vec3 base,
                                     PoseStack poseStack, SubmitNodeCollector collector) {
        float age = crystal.ageInTicks;
        poseStack.pushPose();
        // Like EndCrystalRenderer, smaller: scale, then down half a model unit
        poseStack.translate(base.x, base.y, base.z);
        poseStack.scale(CRYSTAL_SCALE, CRYSTAL_SCALE, CRYSTAL_SCALE);
        poseStack.translate(0.0F, -0.5F, 0.0F);
        collector.submitModel(model, crystal, poseStack, model.renderType(CRYSTAL_TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0, null);
        poseStack.popPose();

    }

    @Override
    public AABB getRenderBoundingBox(RenderingCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(4); // the crystal stands inside the hut
    }

    // Past 48 blocks the crystal isn't drawn
    @Override
    public int getViewDistance() {
        return 48;
    }
}
