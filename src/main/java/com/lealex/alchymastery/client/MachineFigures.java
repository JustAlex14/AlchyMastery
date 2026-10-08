package com.lealex.alchymastery.client;

import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchymastery.block.WormholeBlock;
import com.lealex.alchymastery.block.entity.BookAnimation;
import com.lealex.alchyx.client.animation.CodedFigure;
import com.lealex.alchyx.client.animation.Figures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The parts of the machines that animation files can place as coded figures ({@code "figure": "alchymastery:..."}),
 * so a miniature of a machine shows them from the machine's file. Each is put with "at" on the block it belongs to:
 * <ul>
 *   <li>{@code alchymastery:book}: the transmutation core's floating book. It turns toward a player standing close,
 *       opens, flips its pages, and stays open while the machine works;</li>
 *   <li>{@code alchymastery:condensator_conduit}: the condensator's conduit, asleep, or awake with its eye open
 *       while the machine works;</li>
 *   <li>{@code alchymastery:crystal}: the rendering cauldron's small crystal, turning. "at" is where its base is.</li>
 * </ul>
 */
public final class MachineFigures {
    private MachineFigures() {}

    /** A book and the last tick it was moved on. */
    private static final class Book {
        final BookAnimation animation = new BookAnimation();
        long lastTick = Long.MIN_VALUE;
    }

    // One book per owner (a miniature, a machine): it remembers how far open it is
    private static final Map<Object, Book> BOOKS = new WeakHashMap<>();

    // Baked on first use (the model layers are loaded by then)
    private static @Nullable BookModel bookModel;
    private static WormholeRenderer.@Nullable Parts conduitParts;
    private static @Nullable EndCrystalModel crystalModel;

    public static void register() {
        Figures.register(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "book"), MachineFigures::book);
        Figures.register(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "condensator_conduit"), MachineFigures::conduit);
        Figures.register(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "crystal"), MachineFigures::crystal);
    }

    /** Back to the plain block frame: these aren't bodies, they don't turn with a heading. */
    private static void upright(CodedFigure.Context context, PoseStack poseStack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(context.yaw() - 180.0F));
    }

    /** The enchanting table's book over the block, like vanilla's EnchantTableRenderer. */
    private static void book(CodedFigure.Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        Book book = BOOKS.computeIfAbsent(context.owner(), key -> new Book());
        long tick = level.getGameTime();
        if (tick != book.lastTick && !minecraft.isPaused()) {
            book.lastTick = tick;
            book.animation.tick(level, BlockPos.containing(context.at()), context.working());
        }
        BookAnimation animation = book.animation;
        float partialTicks = context.partialTicks();
        float time = animation.time + partialTicks;
        float flip = Mth.lerp(partialTicks, animation.oFlip, animation.flip);
        float open = Mth.lerp(partialTicks, animation.oOpen, animation.open);
        float turn = animation.rot - animation.oRot;
        while (turn >= Math.PI) turn -= (float) (Math.PI * 2);
        while (turn < -Math.PI) turn += (float) (Math.PI * 2);
        float yRot = animation.oRot + turn * partialTicks;

        if (bookModel == null) bookModel = new BookModel(minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));
        upright(context, poseStack);
        poseStack.translate(0.0F, 0.85F + Mth.sin(time * 0.1F) * 0.01F, 0.0F);
        poseStack.mulPose(Axis.YP.rotation(-yRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));
        float ff1 = Mth.frac(flip + 0.25F) * 1.6F - 0.3F;
        float ff2 = Mth.frac(flip + 0.75F) * 1.6F - 0.3F;
        BookModel.State state = BookModel.State.forAnimation(time, Mth.clamp(ff1, 0.0F, 1.0F), Mth.clamp(ff2, 0.0F, 1.0F), open);
        collector.submitModel(bookModel, state, poseStack, context.light(), OverlayTexture.NO_OVERLAY, -1,
                EnchantTableRenderer.BOOK_TEXTURE, minecraft.getAtlasManager(), 0, null);
    }

    /** The condensator's conduit in the block (WormholeRenderer with the condensator's skin). */
    private static void conduit(CodedFigure.Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (conduitParts == null) {
            EntityModelSet models = Minecraft.getInstance().getEntityModels();
            conduitParts = new WormholeRenderer.Parts(models.bakeLayer(ModelLayers.CONDUIT_SHELL), models.bakeLayer(ModelLayers.CONDUIT_CAGE),
                    models.bakeLayer(ModelLayers.CONDUIT_WIND), models.bakeLayer(ModelLayers.CONDUIT_EYE));
        }
        upright(context, poseStack);
        poseStack.translate(-0.5F, 0.0F, -0.5F); // the pose is at the middle of the block's floor, the conduit is drawn from its corner
        WormholeRenderer.draw(conduitParts, WormholeRenderer.CONDENSATOR,
                context.working() ? WormholeBlock.Look.ACTIVE : WormholeBlock.Look.UNLINKED,
                context.time(), context.light(), null, poseStack, collector, camera);
    }

    /** The rendering cauldron's crystal, its base at the pose (RenderingCoreRenderer draws the real one the same way). */
    private static void crystal(CodedFigure.Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (crystalModel == null) {
            crystalModel = new EndCrystalModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.END_CRYSTAL));
        }
        EndCrystalRenderState state = new EndCrystalRenderState();
        state.ageInTicks = context.time();
        state.showsBottom = false;
        upright(context, poseStack);
        RenderingCoreRenderer.submitCrystal(crystalModel, state, Vec3.ZERO, poseStack, collector);
    }
}
