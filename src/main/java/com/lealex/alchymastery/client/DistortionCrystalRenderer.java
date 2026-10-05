package com.lealex.alchymastery.client;

import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchymastery.block.entity.DistortionCrystalBlockEntity;
import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The distortion crystal hovering over its pedestal: vanilla's end crystal model (glass frames + heart, no base),
 * at a smaller scale, with textures/entity/distortion_crystal.png (tools/crystal_textures.py). It glows (full
 * bright) and spins; while its chamber burns fuel it spins faster.
 */
public class DistortionCrystalRenderer
        implements BlockEntityRenderer<DistortionCrystalBlockEntity, DistortionCrystalRenderer.State> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Alchymastery.MODID, "textures/entity/distortion_crystal.png");
    private static final float SCALE = 0.85F;   // of vanilla's model (the entity draws it at 2)
    private static final float LIFT = 0.45F;    // the model's origin above the block's bottom; it bobs above the pedestal

    public static class State extends BlockEntityRenderState {
        public final EndCrystalRenderState crystal = new EndCrystalRenderState();
    }

    private final EndCrystalModel model;

    public DistortionCrystalRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new EndCrystalModel(context.bakeLayer(ModelLayers.END_CRYSTAL));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DistortionCrystalBlockEntity crystal, State state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(crystal, state, partialTicks, cameraPosition, breakProgress);
        Level level = crystal.getLevel();
        float time = level == null ? 0 : (level.getGameTime() % 24000L) + partialTicks;
        // the chamber below (core two blocks down) burning: it spins faster, easing between the two speeds
        boolean burning = level != null && level.getBlockEntity(crystal.getBlockPos().below(2)) instanceof DistortionMatrixBlockEntity matrix
                && matrix.isFormed() && matrix.getBurnEndTime() > level.getGameTime();
        state.crystal.ageInTicks = spin(crystal, level == null ? 0 : level.getGameTime() + (double) partialTicks, burning ? 1.6 : 0.8);
        state.crystal.showsBottom = false;
        state.crystal.lightCoords = LightCoordsUtil.FULL_BRIGHT;
    }

    /** Per crystal: its accumulated spin "age", the last time it was advanced, its current speed. */
    private static final java.util.Map<DistortionCrystalBlockEntity, double[]> SPIN = new java.util.WeakHashMap<>();

    /**
     * The crystal's animation age, advanced by its speed each frame instead of computed from the game time: changing
     * speed (a fuel ending and the next starting) never makes the crystal jump. The speed eases toward its target,
     * so a short gap between two fuels isn't even noticed.
     */
    private static float spin(DistortionCrystalBlockEntity crystal, double now, double target) {
        double[] s = SPIN.computeIfAbsent(crystal, c -> new double[]{0, now, target});
        double dt = Math.max(0, Math.min(now - s[1], 10)); // ticks since last frame (capped after a pause)
        s[2] += (target - s[2]) * Math.min(1, dt * 0.05);    // ease the speed (about 1 s to change)
        s[0] = (s[0] + dt * s[2]) % 120000;                  // (wraps on whole turns: 120000 * 3 degrees)
        s[1] = now;
        return (float) s[0];
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5F, LIFT, 0.5F);
        poseStack.scale(SCALE, SCALE, SCALE);
        collector.submitModel(model, state.crystal, poseStack, TEXTURE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0, null);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(DistortionCrystalBlockEntity crystal) {
        return new AABB(crystal.getBlockPos()).expandTowards(0, 1.5, 0).inflate(0.5);
    }
}
