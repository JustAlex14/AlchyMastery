package com.lealex.alchymastery.compound;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Reconstruction rules (design spec): compounds of a material + a base block (the "catalyst") + distortion fluid
 * rebuild one item of that material. The base block chooses what comes out:
 * stone -> raw ore, cobblestone -> ingot (or gem), deepslate -> ore block (or gem ore).
 * A material file can replace the base block for a form ("catalysts") and name the result ("outputs"); netherite
 * uses netherrack -> netherite ingot. Compounds used = the form's destructuration value ("costs" overrides apply).
 * Mob drops ("drop" form) are rebuilt on soul sand or soul soil.
 * Storage blocks are not rebuilt (open question in the spec). Values are config defaults to tune.
 */
public final class Reconstruction {
    private Reconstruction() {}

    /** What one operation takes and makes. */
    public record Recipe(Identifier material, CompoundForm form, Item output, int compounds, int energy, int ticks, int fluid) {}

    /** DE, ticks and distortion fluid (mB) per rebuilt form. */
    private record Cost(int energy, int ticks, int fluid) {}

    private static final Map<CompoundForm, Cost> COSTS = Map.of(
            CompoundForm.RAW, new Cost(30, 100, 100),
            CompoundForm.INGOT, new Cost(15, 60, 50),
            CompoundForm.GEM, new Cost(15, 60, 50),
            CompoundForm.ORE, new Cost(60, 160, 200),
            CompoundForm.GEM_ORE, new Cost(60, 160, 200),
            CompoundForm.DROP, new Cost(15, 60, 50));

    /** Default base blocks and the forms they rebuild, in order of preference. */
    private static final Map<Item, List<CompoundForm>> DEFAULT_CATALYSTS = Map.of(
            Items.STONE, List.of(CompoundForm.RAW),
            Items.COBBLESTONE, List.of(CompoundForm.INGOT, CompoundForm.GEM),
            Items.DEEPSLATE, List.of(CompoundForm.ORE, CompoundForm.GEM_ORE),
            Items.COBBLED_DEEPSLATE, List.of(CompoundForm.ORE, CompoundForm.GEM_ORE),
            Items.SOUL_SAND, List.of(CompoundForm.DROP),
            Items.SOUL_SOIL, List.of(CompoundForm.DROP));

    /** The recipe for compounds of this material on this base block, or null if they don't make anything. */
    public static @Nullable Recipe find(@Nullable Identifier materialId, ItemResource base) {
        if (materialId == null || base.isEmpty()) return null;
        CompoundMaterial material = CompoundMaterials.get(materialId);
        if (material == null) return null;

        CompoundForm form = null;
        // The material's own catalysts first (netherrack for netherite)
        for (Map.Entry<CompoundForm, Ingredient> catalyst : material.catalysts().entrySet()) {
            if (base.test(catalyst.getValue()) && COSTS.containsKey(catalyst.getKey())) {
                form = catalyst.getKey();
                break;
            }
        }
        // Then the default base blocks, for forms the material doesn't give its own catalyst
        if (form == null) {
            for (CompoundForm candidate : DEFAULT_CATALYSTS.getOrDefault(base.getItem(), List.of())) {
                if (!material.catalysts().containsKey(candidate) && outputFor(material, candidate, base) != null) {
                    form = candidate;
                    break;
                }
            }
        }
        if (form == null) return null;
        Item output = outputFor(material, form, base);
        if (output == null) return null;
        Cost cost = COSTS.get(form);
        return new Recipe(materialId, form, output, material.compoundsFor(form), cost.energy, cost.ticks, cost.fluid);
    }

    /** One rebuildable item for recipe viewers (JEI): its recipe and every base block that makes it. */
    public record Listing(Recipe recipe, List<Item> bases) {}

    private static final List<CompoundForm> FORM_ORDER =
            List.of(CompoundForm.RAW, CompoundForm.INGOT, CompoundForm.GEM, CompoundForm.ORE, CompoundForm.GEM_ORE, CompoundForm.DROP);
    private static final List<Item> BASE_ORDER = List.of(Items.STONE, Items.COBBLESTONE, Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, Items.SOUL_SAND, Items.SOUL_SOIL);

    /** Every recipe the reconstruction chamber knows, for recipe viewers. */
    public static List<Listing> allRecipes() {
        List<Listing> listings = new java.util.ArrayList<>();
        for (Map.Entry<Identifier, CompoundMaterial> entry : CompoundMaterials.all().entrySet()) {
            for (CompoundForm form : FORM_ORDER) {
                List<Item> candidates = new java.util.ArrayList<>();
                Ingredient own = entry.getValue().catalysts().get(form);
                if (own != null) own.items().forEach(holder -> candidates.add(holder.value()));
                else candidates.addAll(BASE_ORDER);
                List<Item> bases = new java.util.ArrayList<>();
                Recipe first = null;
                for (Item base : candidates) {
                    Recipe recipe = find(entry.getKey(), ItemResource.of(base));
                    if (recipe != null && recipe.form() == form) {
                        bases.add(base);
                        if (first == null) first = recipe;
                    }
                }
                if (first != null) listings.add(new Listing(first, bases));
            }
        }
        return listings;
    }

    /**
     * The recipe that rebuilds exactly this item (a target chosen by the player, e.g. an iron ingot or raw gold):
     * the material and form it belongs to, with that form's costs. Null if no material rebuilds it.
     * Used by the alchemical nexus, which needs no base block.
     */
    public static @Nullable Recipe findTarget(ItemResource target) {
        if (target.isEmpty()) return null;
        for (Map.Entry<Identifier, CompoundMaterial> entry : CompoundMaterials.all().entrySet()) {
            CompoundMaterial material = entry.getValue();
            for (CompoundForm form : COSTS.keySet()) {
                Item explicit = material.outputs().get(form);
                Ingredient ingredient = material.forms().get(form);
                boolean matches = explicit != null ? target.is(explicit) : ingredient != null && target.test(ingredient);
                if (matches) {
                    Cost cost = COSTS.get(form);
                    return new Recipe(entry.getKey(), form, target.getItem(), material.compoundsFor(form), cost.energy, cost.ticks, cost.fluid);
                }
            }
        }
        return null;
    }

    /**
     * The item a form rebuilds into: the material's "outputs" entry, or a pick from the form's ingredient
     * (vanilla items first; deepslate variants when the base block is deepslate).
     */
    private static @Nullable Item outputFor(CompoundMaterial material, CompoundForm form, ItemResource base) {
        Item explicit = material.outputs().get(form);
        if (explicit != null) return explicit;
        Ingredient ingredient = material.forms().get(form);
        if (ingredient == null) return null;
        boolean deepslate = base.is(Items.DEEPSLATE) || base.is(Items.COBBLED_DEEPSLATE);
        Item best = null;
        int bestScore = -1;
        for (Holder<Item> holder : ingredient.items().toList()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(holder.value());
            int score = 0;
            if (id.getNamespace().equals("minecraft")) score += 2;
            if (deepslate == id.getPath().contains("deepslate")) score += 1;
            if (score > bestScore) {
                best = holder.value();
                bestScore = score;
            }
        }
        return best;
    }
}
