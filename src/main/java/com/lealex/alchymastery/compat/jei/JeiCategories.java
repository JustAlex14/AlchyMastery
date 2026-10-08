package com.lealex.alchymastery.compat.jei;

import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchymastery.registry.ModRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;

/**
 * The four JEI pages (recipe categories): destructuration, transmutation, reconstruction, condensation.
 * Each draws slots, an arrow, and its energy / time / fluid costs as text.
 */
public final class JeiCategories {
    private JeiCategories() {}

    public static final IRecipeType<JeiDisplays.Destructuration> DESTRUCTURATION =
            IRecipeType.create(Alchymastery.MODID, "destructuration", JeiDisplays.Destructuration.class);
    public static final IRecipeType<JeiDisplays.Transmutation> TRANSMUTATION =
            IRecipeType.create(Alchymastery.MODID, "transmutation", JeiDisplays.Transmutation.class);
    public static final IRecipeType<JeiDisplays.Reconstruction> RECONSTRUCTION =
            IRecipeType.create(Alchymastery.MODID, "reconstruction", JeiDisplays.Reconstruction.class);
    public static final IRecipeType<JeiDisplays.Condensation> CONDENSATION =
            IRecipeType.create(Alchymastery.MODID, "condensation", JeiDisplays.Condensation.class);

    public static final IRecipeType<JeiDisplays.Rendering> RENDERING =
            IRecipeType.create(Alchymastery.MODID, "rendering", JeiDisplays.Rendering.class);

    private static final int TEXT = 0xFF404040;

    private static void text(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(Minecraft.getInstance().font, text, x, y, TEXT, false);
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? ticks / 20 + " s" : String.format("%.1f s", ticks / 20.0);
    }

    // ---- Destructuration: item -> compounds ----

    public static class Destructuration extends AbstractRecipeCategory<JeiDisplays.Destructuration> {
        public Destructuration(IGuiHelper gui) {
            super(DESTRUCTURATION, Component.translatable("jei.alchymastery.destructuration"),
                    gui.createDrawableItemLike(ModRegistries.DESTRUCTURATION_CORE_ITEM.get()), 120, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, JeiDisplays.Destructuration recipe, IFocusGroup focuses) {
            builder.addInputSlot(10, 4).setStandardSlotBackground().addItemStacks(recipe.inputs());
            builder.addOutputSlot(90, 4).setOutputSlotBackground().add(recipe.compounds());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, JeiDisplays.Destructuration recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(46, 4);
        }

        @Override
        public void draw(JeiDisplays.Destructuration recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, recipe.energy() + " DE, " + seconds(recipe.ticks()), 10, 28);
        }
    }

    // ---- Transmutation: compounds -> other compounds ----

    public static class Transmutation extends AbstractRecipeCategory<JeiDisplays.Transmutation> {
        public Transmutation(IGuiHelper gui) {
            super(TRANSMUTATION, Component.translatable("jei.alchymastery.transmutation"),
                    gui.createDrawableItemLike(ModRegistries.TRANSMUTATION_CORE_ITEM.get()), 120, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, JeiDisplays.Transmutation recipe, IFocusGroup focuses) {
            builder.addInputSlot(10, 4).setStandardSlotBackground().add(recipe.input());
            builder.addOutputSlot(90, 4).setOutputSlotBackground().add(recipe.output());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, JeiDisplays.Transmutation recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(46, 4);
        }

        @Override
        public void draw(JeiDisplays.Transmutation recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, recipe.energy() + " DE, 2 s", 10, 28);
        }
    }

    // ---- Reconstruction: compounds + base block + fluid -> item ----

    public static class Reconstruction extends AbstractRecipeCategory<JeiDisplays.Reconstruction> {
        public Reconstruction(IGuiHelper gui) {
            super(RECONSTRUCTION, Component.translatable("jei.alchymastery.reconstruction"),
                    gui.createDrawableItemLike(ModRegistries.RECONSTRUCTION_CORE_ITEM.get()), 130, 60);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, JeiDisplays.Reconstruction recipe, IFocusGroup focuses) {
            builder.addInputSlot(6, 4).setStandardSlotBackground().add(recipe.compounds());
            builder.addInputSlot(6, 24).setStandardSlotBackground().addItemStacks(recipe.bases());
            builder.addInputSlot(30, 14).setStandardSlotBackground()
                    .add(ModRegistries.DISTORTION_FLUID.get(), recipe.fluid())
                    .setFluidRenderer(recipe.fluid(), false, 16, 16);
            builder.addOutputSlot(100, 14).setOutputSlotBackground().add(recipe.output());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, JeiDisplays.Reconstruction recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(62, 14);
        }

        @Override
        public void draw(JeiDisplays.Reconstruction recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, recipe.fluid() + " mB, " + recipe.energy() + " DE, " + seconds(recipe.ticks()), 6, 48);
        }
    }

    // ---- Rendering: mob essence + distortion fluid -> liquid experience ----

    public static class Rendering extends AbstractRecipeCategory<JeiDisplays.Rendering> {
        public Rendering(IGuiHelper gui) {
            super(RENDERING, Component.translatable("jei.alchymastery.rendering"),
                    gui.createDrawableItemLike(ModRegistries.RENDERING_CORE_ITEM.get()), 120, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, JeiDisplays.Rendering recipe, IFocusGroup focuses) {
            builder.addInputSlot(6, 4).setStandardSlotBackground().add(recipe.essence());
            builder.addInputSlot(26, 4).setStandardSlotBackground()
                    .add(ModRegistries.DISTORTION_FLUID.get(), recipe.fluid()).setFluidRenderer(recipe.fluid(), false, 16, 16);
            builder.addOutputSlot(96, 4).setOutputSlotBackground()
                    .add(ModRegistries.LIQUID_EXPERIENCE.get(), recipe.experience()).setFluidRenderer(recipe.experience(), false, 16, 16);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, JeiDisplays.Rendering recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(58, 4);
        }

        @Override
        public void draw(JeiDisplays.Rendering recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, recipe.points() + " XP, " + recipe.energy() + " DE, " + seconds(recipe.ticks()), 6, 28);
        }
    }

    // ---- Condensation: water -> distortion fluid ----

    public static class Condensation extends AbstractRecipeCategory<JeiDisplays.Condensation> {
        public Condensation(IGuiHelper gui) {
            super(CONDENSATION, Component.translatable("jei.alchymastery.condensation"),
                    gui.createDrawableItemLike(ModRegistries.CONDENSATOR_CORE_ITEM.get()), 120, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, JeiDisplays.Condensation recipe, IFocusGroup focuses) {
            builder.addInputSlot(10, 4).setStandardSlotBackground()
                    .add(Fluids.WATER, recipe.water()).setFluidRenderer(recipe.water(), false, 16, 16);
            builder.addOutputSlot(90, 4).setOutputSlotBackground()
                    .add(ModRegistries.DISTORTION_FLUID.get(), recipe.fluid()).setFluidRenderer(recipe.fluid(), false, 16, 16);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, JeiDisplays.Condensation recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(46, 4);
        }

        @Override
        public void draw(JeiDisplays.Condensation recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, recipe.energy() + " DE, " + seconds(recipe.ticks()), 10, 28);
        }
    }
}
