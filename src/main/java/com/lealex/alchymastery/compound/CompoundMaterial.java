package com.lealex.alchymastery.compound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * One material, loaded from data/<namespace>/compound_material/<name>.json (the file name is the material id,
 * e.g. alchymastery:iron). Example:
 * <pre>
 * {
 *   "color": "#D9D9D9",
 *   "tier": 1,
 *   "forms": { "raw": "#c:raw_materials/iron", "ingot": "#c:ingots/iron", "ore": "#c:ores/iron" },
 *   "costs": { "ore": 6 },
 *   "worth": { "of": "alchymastery:diamond", "count": 5 }   (optional, special value)
 *   "catalysts": { "ingot": "minecraft:netherrack" }         (optional, reconstruction base block per form)
 *   "outputs": { "ingot": "minecraft:netherite_ingot" }       (optional, reconstruction result per form)
 *   "family": "mob"                                           (optional, default "mineral")
 * }
 * </pre>
 * Forms take an item id, a #tag or a list (like a vanilla recipe ingredient). The reconstruction chamber rebuilds
 * a form from compounds + a base block (see {@link Reconstruction}): "catalysts" replaces the default base block
 * for a form, "outputs" names the item it makes (default: an item from the form's ingredient; lets a material
 * rebuild something it can't be broken down from, like netherite ingots).
 * <p>
 * "family" keeps unrelated matter apart: compounds only transmute into materials of the same family, so iron can
 * become gold but never rotten flesh. Defaults: "mineral" (ores, ingots, gems) and "mob" (mob drops, shown as
 * essences); a datapack can invent more (any lowercase name).
 */
public record CompoundMaterial(int color, int tier, Optional<Worth> worth, Map<CompoundForm, Ingredient> forms,
                               Map<CompoundForm, Integer> costs, Map<CompoundForm, Ingredient> catalysts,
                               Map<CompoundForm, Item> outputs, String family) {

    public static final String MINERAL = "mineral";
    public static final String MOB = "mob";

    /**
     * A special value instead of the tier's: worth {@code count} of another material (design spec: netherite is
     * worth 5 diamonds). It still shows its own tier; the value follows the chain.
     */
    public record Worth(Identifier of, int count) {
        public static final Codec<Worth> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("of").forGetter(Worth::of),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Worth::count)
        ).apply(instance, Worth::new));
    }

    public static final Codec<CompoundMaterial> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.STRING_RGB_COLOR.fieldOf("color").forGetter(CompoundMaterial::color),
            Codec.INT.optionalFieldOf("tier", 1).forGetter(CompoundMaterial::tier),
            Worth.CODEC.optionalFieldOf("worth").forGetter(CompoundMaterial::worth),
            Codec.unboundedMap(CompoundForm.CODEC, Ingredient.CODEC).fieldOf("forms").forGetter(CompoundMaterial::forms),
            Codec.unboundedMap(CompoundForm.CODEC, ExtraCodecs.POSITIVE_INT).optionalFieldOf("costs", Map.of()).forGetter(CompoundMaterial::costs),
            Codec.unboundedMap(CompoundForm.CODEC, Ingredient.CODEC).optionalFieldOf("catalysts", Map.of()).forGetter(CompoundMaterial::catalysts),
            Codec.unboundedMap(CompoundForm.CODEC, BuiltInRegistries.ITEM.byNameCodec()).optionalFieldOf("outputs", Map.of()).forGetter(CompoundMaterial::outputs),
            Codec.STRING.validate(CompoundMaterial::validateFamily).optionalFieldOf("family", MINERAL).forGetter(CompoundMaterial::family)
    ).apply(instance, CompoundMaterial::new));

    private static com.mojang.serialization.DataResult<String> validateFamily(String family) {
        return family.matches("[a-z0-9_]+") ? com.mojang.serialization.DataResult.success(family)
                : com.mojang.serialization.DataResult.error(() -> "family must be a lowercase name, got: " + family);
    }

    /** Compounds one item of this form gives. */
    public int compoundsFor(CompoundForm form) {
        return costs.getOrDefault(form, form.defaultCompounds);
    }
}
