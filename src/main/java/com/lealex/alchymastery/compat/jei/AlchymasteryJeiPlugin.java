package com.lealex.alchymastery.compat.jei;

import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchymastery.compound.CompoundForm;
import com.lealex.alchymastery.compound.CompoundMaterial;
import com.lealex.alchymastery.compound.CompoundMaterials;
import com.lealex.alchymastery.compound.Reconstruction;
import com.lealex.alchymastery.compound.Transmutation;
import com.lealex.alchymastery.block.entity.RenderingCoreBlockEntity;
import com.lealex.alchymastery.item.CompoundItem;
import com.lealex.alchymastery.registry.ModRegistries;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JEI integration (only loaded when JEI is installed: JEI finds this class by its @JeiPlugin annotation, nothing else
 * refers to it). Shows every compound (one per material), and recipe pages for the four machines, built from the
 * same material files and rules the machines use. The alchemical nexus is listed as a station for all four.
 *
 * The material files are server data: on a dedicated server JEI first shows the materials that ship with the mod,
 * then swaps in the server's list (datapacks included) when it arrives (CompoundMaterials.clientListener).
 */
@JeiPlugin
public class AlchymasteryJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(Alchymastery.MODID, "jei");

    /** The material pages JEI shows now, so a refresh can hide them before adding the server's. */
    private record Pages(List<JeiDisplays.Destructuration> destructuration, List<JeiDisplays.Transmutation> transmutation,
                         List<JeiDisplays.Reconstruction> reconstruction, List<JeiDisplays.Rendering> rendering) {}

    private static @org.jspecify.annotations.Nullable Pages shown;
    private static final java.util.Set<Identifier> SHOWN_COMPOUNDS = new java.util.HashSet<>();
    private static mezz.jei.api.runtime.@org.jspecify.annotations.Nullable IJeiRuntime runtime;

    /** A client that has no materials yet (dedicated server, before the server's list): the mod's own files. */
    private static void ensureMaterials() {
        if (CompoundMaterials.all().isEmpty() && net.minecraft.client.Minecraft.getInstance().level != null) {
            CompoundMaterials.loadBuiltIn(net.minecraft.client.Minecraft.getInstance().level.registryAccess());
        }
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    // One JEI entry per compound material (the compound item is one item; its MATERIAL component tells them apart)
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerFromDataComponentTypes(ModRegistries.COMPOUND.get(), ModRegistries.MATERIAL.get());
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        ensureMaterials();
        SHOWN_COMPOUNDS.clear();
        List<ItemStack> compounds = new ArrayList<>();
        for (Identifier material : CompoundMaterials.all().keySet()) {
            compounds.add(compound(material, 1));
            SHOWN_COMPOUNDS.add(material);
        }
        registration.addExtraItemStacks(compounds);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new JeiCategories.Destructuration(gui),
                new JeiCategories.Transmutation(gui),
                new JeiCategories.Reconstruction(gui),
                new JeiCategories.Condensation(gui),
                new JeiCategories.Rendering(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ensureMaterials();
        shown = build();
        registration.addRecipes(JeiCategories.DESTRUCTURATION, shown.destructuration());
        registration.addRecipes(JeiCategories.TRANSMUTATION, shown.transmutation());
        registration.addRecipes(JeiCategories.RECONSTRUCTION, shown.reconstruction());
        registration.addRecipes(JeiCategories.RENDERING, shown.rendering());

        // Condensation (same values as the condensator)
        registration.addRecipes(JeiCategories.CONDENSATION, List.of(new JeiDisplays.Condensation(1_000, 500, 200, 100)));

        // A few words on the end-game machine and the wormhole attuner
        registration.addIngredientInfo(ModRegistries.EXPERIENCE_NEXUS_ITEM.get(),
                Component.translatable("jei.alchymastery.info.experience_nexus"));
        registration.addIngredientInfo(ModRegistries.ALCHEMICAL_NEXUS_ITEM.get(),
                Component.translatable("jei.alchymastery.info.alchemical_nexus"));
        registration.addIngredientInfo(ModRegistries.WORMHOLE_ATTUNER.get(),
                Component.translatable("jei.alchymastery.info.wormhole_attuner"));
    }

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        CompoundMaterials.clientListener = AlchymasteryJeiPlugin::refresh;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        CompoundMaterials.clientListener = () -> {};
    }

    /** The server sent its materials (join, /reload): the pages show those in place of the ones shown. */
    private static void refresh() {
        if (runtime == null) return;
        var recipes = runtime.getRecipeManager();
        if (shown != null) {
            recipes.hideRecipes(JeiCategories.DESTRUCTURATION, shown.destructuration());
            recipes.hideRecipes(JeiCategories.TRANSMUTATION, shown.transmutation());
            recipes.hideRecipes(JeiCategories.RECONSTRUCTION, shown.reconstruction());
            recipes.hideRecipes(JeiCategories.RENDERING, shown.rendering());
        }
        shown = build(); // new display objects, so none of them is hidden
        recipes.addRecipes(JeiCategories.DESTRUCTURATION, shown.destructuration());
        recipes.addRecipes(JeiCategories.TRANSMUTATION, shown.transmutation());
        recipes.addRecipes(JeiCategories.RECONSTRUCTION, shown.reconstruction());
        recipes.addRecipes(JeiCategories.RENDERING, shown.rendering());

        // compounds of materials JEI did not list yet (datapack materials)
        List<ItemStack> fresh = new ArrayList<>();
        for (Identifier material : CompoundMaterials.all().keySet()) {
            if (SHOWN_COMPOUNDS.add(material)) fresh.add(compound(material, 1));
        }
        if (!fresh.isEmpty()) runtime.getIngredientManager().addIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, fresh);
    }

    /** The material pages, from the materials loaded on this side. */
    private static Pages build() {
        Map<Identifier, CompoundMaterial> materials = CompoundMaterials.all();

        // Destructuration: every form of every material
        List<JeiDisplays.Destructuration> destructuration = new ArrayList<>();
        for (Map.Entry<Identifier, CompoundMaterial> entry : materials.entrySet()) {
            for (Map.Entry<CompoundForm, Ingredient> form : entry.getValue().forms().entrySet()) {
                List<ItemStack> inputs = new ArrayList<>();
                for (Holder<Item> item : form.getValue().items().toList()) inputs.add(new ItemStack(item));
                if (inputs.isEmpty()) continue;
                CompoundForm f = form.getKey();
                destructuration.add(new JeiDisplays.Destructuration(inputs,
                        compound(entry.getKey(), entry.getValue().compoundsFor(f)), f.energy, f.ticks));
            }
        }

        // Transmutation: every pair of materials, as the smallest whole exchange
        List<JeiDisplays.Transmutation> transmutation = new ArrayList<>();
        for (Identifier from : materials.keySet()) {
            for (Identifier to : materials.keySet()) {
                if (from.equals(to) || !Transmutation.compatible(from, to)) continue; // same family only
                long fromValue = Transmutation.valueOf(from), toValue = Transmutation.valueOf(to);
                if (fromValue <= 0 || toValue <= 0) continue;
                long each = Transmutation.effectiveValue(fromValue, toValue);
                int taken, made;
                if (each >= toValue) {          // a richer compound makes several poorer ones
                    taken = 1;
                    made = (int) (each / toValue);
                } else {                        // several poorer compounds make one richer one
                    taken = (int) ((toValue + each - 1) / each);
                    made = 1;
                }
                if (taken > 64 || made > 64) continue;
                long energy = Transmutation.ENERGY_PER_VALUE * (toValue / Transmutation.UNIT) * made;
                transmutation.add(new JeiDisplays.Transmutation(compound(from, taken), compound(to, made), energy));
            }
        }

        // Reconstruction: what each base block rebuilds, per material
        List<JeiDisplays.Reconstruction> reconstruction = new ArrayList<>();
        for (Reconstruction.Listing listing : Reconstruction.allRecipes()) {
            Reconstruction.Recipe recipe = listing.recipe();
            List<ItemStack> bases = new ArrayList<>();
            for (Item base : listing.bases()) bases.add(new ItemStack(base));
            reconstruction.add(new JeiDisplays.Reconstruction(compound(recipe.material(), recipe.compounds()), bases,
                    recipe.fluid(), new ItemStack(recipe.output()), recipe.energy(), recipe.ticks()));
        }

        // Rendering: every mob essence (same values as the rendering cauldron)
        List<JeiDisplays.Rendering> rendering = new ArrayList<>();
        for (Map.Entry<Identifier, CompoundMaterial> entry : materials.entrySet()) {
            if (!CompoundMaterial.MOB.equals(entry.getValue().family())) continue;
            ItemStack essence = compound(entry.getKey(), 1);
            int points = RenderingCoreBlockEntity.pointsFor(essence);
            rendering.add(new JeiDisplays.Rendering(essence, RenderingCoreBlockEntity.FLUID_PER_ESSENCE,
                    points * ModRegistries.MB_PER_XP, points, RenderingCoreBlockEntity.ENERGY_PER_ESSENCE, RenderingCoreBlockEntity.TICKS_PER_ESSENCE));
        }
        return new Pages(destructuration, transmutation, reconstruction, rendering);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(JeiCategories.DESTRUCTURATION, ModRegistries.DESTRUCTURATION_CORE_ITEM.get(), ModRegistries.ALCHEMICAL_NEXUS_ITEM.get(), ModRegistries.EXPERIENCE_NEXUS_ITEM.get());
        registration.addCraftingStation(JeiCategories.TRANSMUTATION, ModRegistries.TRANSMUTATION_CORE_ITEM.get(), ModRegistries.ALCHEMICAL_NEXUS_ITEM.get());
        registration.addCraftingStation(JeiCategories.RECONSTRUCTION, ModRegistries.RECONSTRUCTION_CORE_ITEM.get(), ModRegistries.ALCHEMICAL_NEXUS_ITEM.get());
        registration.addCraftingStation(JeiCategories.RENDERING, ModRegistries.RENDERING_CORE_ITEM.get(), ModRegistries.EXPERIENCE_NEXUS_ITEM.get());
        registration.addCraftingStation(JeiCategories.CONDENSATION, ModRegistries.CONDENSATOR_CORE_ITEM.get(), ModRegistries.ALCHEMICAL_NEXUS_ITEM.get(), ModRegistries.EXPERIENCE_NEXUS_ITEM.get());
    }

    private static ItemStack compound(Identifier material, int count) {
        CompoundMaterial data = CompoundMaterials.get(material);
        return data == null ? ItemStack.EMPTY : CompoundItem.create(material, data, count);
    }
}
