package com.lealex.alchymastery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * AlchyMastery settings, saved in config/alchymastery-common.toml.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISTORTION_FUELS = BUILDER
            .comment("Items the distortion matrix accepts as fuel, as \"item_id=energy\".",
                    "The energy (in DE) is produced at 5 DE per tick while the item burns,",
                    "so an item worth 900 DE burns for 180 ticks (9 seconds).")
            .defineListAllowEmpty("distortionFuels",
                    List.of("minecraft:lapis_lazuli=100", "minecraft:lapis_block=900"),
                    () -> "minecraft:lapis_lazuli=100",
                    Config::isValidFuelEntry);

    // Declared before COMPOUND_TIERS, which uses it
    private static final List<String> DEFAULT_TIER_LINES = List.of("Abundant=1", "Common=3", "Uncommon=9",
            "Rare=27", "Ancient=81");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> COMPOUND_TIERS = BUILDER
            .comment("Compound tiers, from tier 0 up, as \"Name=value\".",
                    "The value is what one compound of that tier is worth, counted in tier 0 compounds.",
                    "Add lines to add tiers: \"Mythic=162\" makes tier 5 worth 2 Ancient compounds.",
                    "Values should go up with each tier. A tier past the end of the list is worth",
                    "3 times the last one and shows as \"Tier N\".",
                    "The name can be translated with the lang key tier.alchymastery.<tier number>.")
            .defineListAllowEmpty("compoundTiers",
                    DEFAULT_TIER_LINES,
                    () -> "Mythic=243",
                    Config::isValidTierEntry);

    static final ModConfigSpec SPEC = BUILDER.build();

    // ---- Compound tiers ----

    /** One compound tier: its name and its value in tier 0 compounds. */
    public record Tier(String name, long value) {}

    private static final List<Tier> DEFAULT_TIERS = List.of(new Tier("Abundant", 1), new Tier("Common", 3),
            new Tier("Uncommon", 9), new Tier("Rare", 27), new Tier("Ancient", 81));

    private static List<? extends String> cachedTierSource = null;
    private static List<Tier> cachedTiers = DEFAULT_TIERS;

    /** The configured tiers, tier 0 first. */
    public static List<Tier> tiers() {
        List<? extends String> source;
        try {
            source = COMPOUND_TIERS.get();
        } catch (IllegalStateException notLoadedYet) {
            return DEFAULT_TIERS;
        }
        if (source != cachedTierSource) {
            List<Tier> parsed = new java.util.ArrayList<>();
            for (String entry : source) {
                int split = entry.lastIndexOf('=');
                parsed.add(new Tier(entry.substring(0, split).trim(), Long.parseLong(entry.substring(split + 1).trim())));
            }
            cachedTiers = parsed.isEmpty() ? DEFAULT_TIERS : List.copyOf(parsed);
            cachedTierSource = source;
        }
        return cachedTiers;
    }

    /** Value of one compound of this tier, in tier 0 compounds. Past the list: x3 per extra tier. */
    public static long tierValue(int tier) {
        List<Tier> tiers = tiers();
        if (tier <= 0) return tiers.get(0).value();
        if (tier < tiers.size()) return tiers.get(tier).value();
        long value = tiers.get(tiers.size() - 1).value();
        for (int i = tiers.size() - 1; i < tier && value < Long.MAX_VALUE / 3; i++) value *= 3;
        return value;
    }

    /** Display name of this tier: the configured name, or "Tier N" past the list. */
    public static String tierName(int tier) {
        List<Tier> tiers = tiers();
        return tier >= 0 && tier < tiers.size() ? tiers.get(tier).name() : "Tier " + tier;
    }

    /** Accepts "Name=value" with a non-empty name and a positive value. */
    private static boolean isValidTierEntry(Object obj) {
        if (!(obj instanceof String entry)) return false;
        int split = entry.lastIndexOf('=');
        if (split <= 0 || entry.substring(0, split).isBlank()) return false;
        try {
            return Long.parseLong(entry.substring(split + 1).trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ---- Fuel lookup ----

    private static final Map<Item, Long> DEFAULT_FUELS = Map.of(Items.LAPIS_LAZULI, 100L, Items.LAPIS_BLOCK, 900L);

    private static List<? extends String> cachedSource = null;
    private static Map<Item, Long> cachedFuels = DEFAULT_FUELS;

    /** Energy (DE) this item gives when burned in the distortion matrix, or 0 if it isn't a fuel. */
    public static long getFuelEnergy(Item item) {
        Long energy = fuels().get(item);
        return energy == null ? 0L : energy;
    }

    public static long getFuelEnergy(ItemResource resource) {
        for (Map.Entry<Item, Long> entry : fuels().entrySet()) {
            if (resource.is(entry.getKey())) return entry.getValue();
        }
        return 0L;
    }

    private static Map<Item, Long> fuels() {
        List<? extends String> source;
        try {
            source = DISTORTION_FUELS.get();
        } catch (IllegalStateException notLoadedYet) {
            return DEFAULT_FUELS; // config files load a bit after startup
        }
        if (source != cachedSource) { // re-parse only when the config changed
            Map<Item, Long> parsed = new LinkedHashMap<>();
            for (String entry : source) {
                String[] parts = entry.split("=");
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(parts[0].trim()));
                parsed.put(item, Long.parseLong(parts[1].trim()));
            }
            cachedFuels = parsed;
            cachedSource = source;
        }
        return cachedFuels;
    }

    /** Accepts "namespace:item=energy" where the item exists and the energy is a positive number. */
    private static boolean isValidFuelEntry(Object obj) {
        if (!(obj instanceof String entry)) return false;
        String[] parts = entry.split("=");
        if (parts.length != 2) return false;
        Identifier id = Identifier.tryParse(parts[0].trim());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        try {
            return Long.parseLong(parts[1].trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
