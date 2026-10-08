package com.lealex.alchymastery.client;

import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchyx.client.animation.CodedFigure;
import com.lealex.alchyx.client.animation.Figures;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * The coded figures animation files can use ({@code "figure": "alchymastery:..."}), all drawn as pale blue,
 * see-through, glowing ghosts:
 * <ul>
 *   <li>{@code alchymastery:linked_ghost}: the player in the effect's "display" (a player head, e.g. the rendering
 *       cauldron's "linked_player"), in their own skin: hands held out when idle, the evoker's spell-casting pose
 *       while the machine works; draws nothing without a head;</li>
 *   <li>{@code alchymastery:ghost_witch}: a witch, arms crossed when idle, chanting while the machine works.</li>
 * </ul>
 */
public final class GhostFigures {
    /** See-through and pale blue (ARGB tint multiplied into the texture). */
    public static final int GHOST_TINT = 0x8C9CC8FF;
    public static final Identifier WITCH_TEXTURE = Identifier.withDefaultNamespace("textures/entity/witch/witch.png");
    private static final float PLAYER_SCALE = 0.9375F; // what the player renderer applies

    // Baked on first use (the model layers are loaded by then)
    private static @Nullable CasterModel wide, slim;
    private static @Nullable GhostWitchModel witch;

    private GhostFigures() {}

    public static void register() {
        Figures.register(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "linked_ghost"), GhostFigures::linkedGhost);
        Figures.register(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "ghost_witch"), GhostFigures::witch);
    }

    private static void linkedGhost(CodedFigure.Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        ResolvableProfile profile = context.display().get(DataComponents.PROFILE);
        if (profile == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        PlayerSkin skin = minecraft.playerSkinRenderCache().getOrDefault(profile).playerSkin(); // default skin until loaded
        boolean isSlim = skin.model() == PlayerModelType.SLIM;
        if (wide == null) {
            wide = new CasterModel(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER));
            slim = new CasterModel(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM));
        }
        CasterModel model = isSlim ? slim : wide;
        CasterModel.Pose pose = new CasterModel.Pose();
        pose.age = context.time();
        pose.casting = context.working();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(model, pose, poseStack, model.renderType(skin.body().texturePath()),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, GHOST_TINT, null, 0, null);
    }

    private static void witch(CodedFigure.Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (witch == null) witch = new GhostWitchModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.WITCH));
        GhostWitchModel.State state = new GhostWitchModel.State();
        state.ageInTicks = context.time();
        state.entityId = 7; // nose twitch speed
        state.casting = context.working();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(witch, state, poseStack, RenderTypes.entityTranslucent(WITCH_TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, GHOST_TINT, null, 0, null);
    }
}
