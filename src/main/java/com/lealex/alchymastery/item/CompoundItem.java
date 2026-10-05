package com.lealex.alchymastery.item;

import com.lealex.alchymastery.compound.CompoundMaterial;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/**
 * The single compound item. Its material is a data component (like a potion's effect), so a new material file
 * gives a new compound with no code. The color is stored in the stack's custom model data, which the item model
 * uses as a tint, so the client needs no material list.
 */
public class CompoundItem extends Item {

    public CompoundItem(Properties properties) {
        super(properties);
    }

    /** Tier names from the design spec; tiers past the list (from config) show as "Tier N". */
    private static final String[] TIER_NAMES = {"Abundant", "Common", "Uncommon", "Rare", "Ancient"};

    /** A stack of {@code count} compounds of this material (color, tier and family from its file). */
    public static ItemStack create(Identifier material, CompoundMaterial data, int count) {
        return create(material, data.color(), data.tier(), data.family(), count);
    }

    /** A stack of {@code count} mineral compounds of this material, tinted {@code color}. */
    public static ItemStack create(Identifier material, int color, int tier, int count) {
        return create(material, color, tier, CompoundMaterial.MINERAL, count);
    }

    /**
     * The family goes into the custom model data strings (index 0): the item model picks the essence look for
     * "mob" and the name says "Essence", with no material list needed on the client.
     */
    public static ItemStack create(Identifier material, int color, int tier, String family, int count) {
        ItemStack stack = new ItemStack(ModRegistries.COMPOUND.get(), count);
        stack.set(ModRegistries.MATERIAL.get(), material);
        stack.set(ModRegistries.TIER.get(), tier);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(family), List.of(color)));
        return stack;
    }

    /** The compound's family (mineral, mob...), read from the stack. */
    public static String familyOf(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.strings().isEmpty() ? CompoundMaterial.MINERAL : data.strings().get(0);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Integer tier = stack.get(ModRegistries.TIER.get());
        if (tier != null) {
            String name = tier >= 0 && tier < TIER_NAMES.length ? TIER_NAMES[tier] : "Tier " + tier;
            tooltip.accept(Component.literal("Tier: " + name).withStyle(ChatFormatting.GRAY));
        }
        String family = familyOf(stack);
        tooltip.accept(Component.translatableWithFallback("family.alchymastery." + family,
                Character.toUpperCase(family.charAt(0)) + family.substring(1)).withStyle(ChatFormatting.DARK_GRAY));
    }

    // "Iron Compound": the material's name comes from the lang file (material.<namespace>.<name>),
    // or from the file name if there's no translation
    @Override
    public Component getName(ItemStack stack) {
        Identifier material = stack.get(ModRegistries.MATERIAL.get());
        if (material == null) return super.getName(stack);
        String path = material.getPath();
        String fallback = Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
        Component materialName = Component.translatableWithFallback(
                "material." + material.getNamespace() + "." + path, fallback);
        // "Iron Compound", "Rotten Flesh Essence": item.alchymastery.compound.of.<family>, else the compound name
        String family = familyOf(stack);
        String key = CompoundMaterial.MINERAL.equals(family) ? "item.alchymastery.compound.of" : "item.alchymastery.compound.of." + family;
        return Component.translatableWithFallback(key, "%s Compound", materialName);
    }
}
