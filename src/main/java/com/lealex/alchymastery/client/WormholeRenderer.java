package com.lealex.alchymastery.client;

import com.lealex.alchymastery.block.WormholeBlock;
import com.lealex.alchymastery.block.entity.WormholeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the wormhole as a void conduit: vanilla's conduit model parts (shell, cage, wind, eye) with the wormhole's
 * own textures (textures/entity/wormhole, tools/wormhole_textures.py), staged by the block's look:
 * <ul>
 *   <li>unlinked: the sealed obsidian shell, still;</li>
 *   <li>linked: the cage turns slowly and bobs inside a violet vortex, the eye closed;</li>
 *   <li>active (receiving energy): the cage spins three times faster, the vortex too, and the eye opens, glowing.</li>
 * </ul>
 * {@link #draw} is also used for the wormholes of the nexus miniatures.
 */
public class WormholeRenderer implements BlockEntityRenderer<WormholeBlockEntity, WormholeRenderer.State> {
    private static final Vector3f CAGE_AXIS = new Vector3f(0.5F, 1.0F, 0.5F).normalize();

    /** A themed conduit's textures (textures/entity/<folder>/, drawn by tools/wormhole_textures.py). */
    public record Skin(Identifier shell, Identifier cage, Identifier wind, Identifier eyeClosed, Identifier[] eyeOpen) {
        public static Skin of(String folder) {
            java.util.function.Function<String, Identifier> t =
                    name -> Identifier.fromNamespaceAndPath("alchymastery", "textures/entity/" + folder + "/" + name + ".png");
            return new Skin(t.apply("shell"), t.apply("cage"), t.apply("wind"), t.apply("eye_closed"),
                    new Identifier[]{t.apply("eye_open_0"), t.apply("eye_open_1"), t.apply("eye_open_2"), t.apply("eye_open_3")});
        }
    }

    /** The wormhole: obsidian and amethyst, a violet vortex. */
    public static final Skin WORMHOLE = Skin.of("wormhole");
    /** The condensator core: prismarine, a teal vortex flecked with distortion, an eye going from blue to purple. */
    public static final Skin CONDENSATOR = Skin.of("condensator");

    public static class State extends BlockEntityRenderState {
        public WormholeBlock.Look look = WormholeBlock.Look.UNLINKED;
        public float time;
    }

    /** The conduit's model parts (vanilla's layers), shared with the miniatures. */
    public record Parts(ModelPart shell, ModelPart cage, ModelPart wind, ModelPart eye) {
        public static Parts bake(BlockEntityRendererProvider.Context context) {
            return new Parts(context.bakeLayer(ModelLayers.CONDUIT_SHELL), context.bakeLayer(ModelLayers.CONDUIT_CAGE),
                    context.bakeLayer(ModelLayers.CONDUIT_WIND), context.bakeLayer(ModelLayers.CONDUIT_EYE));
        }
    }

    private final Parts parts;

    public WormholeRenderer(BlockEntityRendererProvider.Context context) {
        this.parts = Parts.bake(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WormholeBlockEntity wormhole, State state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(wormhole, state, partialTicks, cameraPosition, breakProgress);
        state.look = wormhole.getBlockState().getValue(WormholeBlock.LOOK);
        Level level = wormhole.getLevel();
        state.time = (level == null ? 0 : level.getGameTime() % 24000L) + partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        draw(parts, WORMHOLE, state.look, state.time, state.lightCoords, state.breakProgress, poseStack, collector, camera);
    }


    /**
     * A themed conduit in the block at the pose's origin: the still shell (UNLINKED), the turning cage with a closed
     * eye (LINKED), or everything three times faster with the eye open (ACTIVE).
     */
    public static void draw(Parts parts, Skin skin, WormholeBlock.Look look, float time, int light,
                            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress,
                            PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (look == WormholeBlock.Look.UNLINKED) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            collector.submitModelPart(parts.shell(), poseStack, RenderTypes.entitySolid(skin.shell()), light,
                    OverlayTexture.NO_OVERLAY, null, -1, breakProgress);
            poseStack.popPose();
            return;
        }
        boolean active = look == WormholeBlock.Look.ACTIVE;
        float speed = active ? 3.0F : 1.0F;
        float turn = time * -0.0375F * speed;               // radians, like the conduit's active rotation
        float hh = Mth.sin(time * 0.1F) / 2.0F + 0.5F;      // the conduit's bob
        hh = hh * hh + hh;

        // The cage, turning around a tilted axis
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.3F + hh * 0.2F, 0.5F);
        poseStack.mulPose(new Quaternionf().rotationAxis(turn, CAGE_AXIS));
        collector.submitModelPart(parts.cage(), poseStack, RenderTypes.entityCutout(skin.cage()), light,
                OverlayTexture.NO_OVERLAY, null, -1, breakProgress);
        poseStack.popPose();

        // The vortex: two glowing layers turning opposite ways
        for (int layer = 0; layer < 2; layer++) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            float scale = layer == 0 ? 1.0F : 0.875F;
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(new Quaternionf().rotationY((layer == 0 ? 1 : -1) * time * 0.02F * speed)
                    .rotateX(layer == 0 ? 0 : (float) Math.PI));
            collector.submitModelPart(parts.wind(), poseStack, RenderTypes.entityTranslucentEmissive(skin.wind()),
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            poseStack.popPose();
        }

        // The eye, facing the camera: closed while linked, open and glowing while energy flows in
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.3F + hh * 0.2F, 0.5F);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(camera.orientation);
        poseStack.mulPose(new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.PI));
        poseStack.scale(1.3333334F, 1.3333334F, 1.3333334F);
        if (active) {
            Identifier eye = skin.eyeOpen()[(int) (time / 3) & 3];
            collector.submitModelPart(parts.eye(), poseStack, RenderTypes.eyes(eye), LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, null);
        } else {
            collector.submitModelPart(parts.eye(), poseStack, RenderTypes.entityCutout(skin.eyeClosed()), light,
                    OverlayTexture.NO_OVERLAY, null);
        }
        poseStack.popPose();
    }

    // The awake wormhole bobs up a little, and its vortex fills the block
    @Override
    public AABB getRenderBoundingBox(WormholeBlockEntity wormhole) {
        return new AABB(wormhole.getBlockPos()).inflate(0.0, 0.25, 0.0);
    }
}
