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

    private static final List<String> DEFAULT_FAMILY_TIER_LINES = List.of(
            "mineral:Abundant=1", "mineral:Common=3", "mineral:Uncommon=9", "mineral:Rare=27", "mineral:Ancient=81",
            "mob:Weak essence=1", "mob:Strong essence=3", "mob:Rare essence=9", "mob:Mythic essence=27",
            "food:Raw food=1", "food:Exotic food=4", "food:Golden food=16", "food:Enchanted meal=64");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FAMILY_TIERS = BUILDER
            .comment("Tiers for one family only, as \"family:Name=value\". A family listed here uses these tiers",
                    "instead of compoundTiers: its tier 0 is its first line, tier 1 its second line, and so on.",
                    "Families never transmute into each other, so their values don't need to match other families.",
                    "Families not listed here (a datapack's new families) use compoundTiers.",
                    "The name can be translated with the lang key tier.alchymastery.<family>.<tier number>.")
            .defineListAllowEmpty("familyTiers",
                    DEFAULT_FAMILY_TIER_LINES,
                    () -> "food:Raw food=1",
                    Config::isValidFamilyTierEntry);

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

    private static List<? extends String> cachedFamilySource = null;
    private static Map<String, List<Tier>> cachedFamilyTiers = Map.of();

    /** Families with their own tier list (familyTiers config), each list tier 0 first. */
    public static Map<String, List<Tier>> familyTiers() {
        List<? extends String> source;
        try {
            source = FAMILY_TIERS.get();
        } catch (IllegalStateException notLoadedYet) {
            return Map.of();
        }
        if (source != cachedFamilySource) {
            Map<String, List<Tier>> parsed = new LinkedHashMap<>();
            for (String entry : source) {
                int colon = entry.indexOf(':'), split = entry.lastIndexOf('=');
                String family = entry.substring(0, colon).trim();
                parsed.computeIfAbsent(family, f -> new java.util.ArrayList<>()).add(new Tier(
                        entry.substring(colon + 1, split).trim(), Long.parseLong(entry.substring(split + 1).trim())));
            }
            Map<String, List<Tier>> frozen = new LinkedHashMap<>();
            parsed.forEach((family, list) -> frozen.put(family, List.copyOf(list)));
            cachedFamilyTiers = Map.copyOf(frozen);
            cachedFamilySource = source;
        }
        return cachedFamilyTiers;
    }

    /** Whether this family has its own tiers in familyTiers. */
    public static boolean hasOwnTiers(String family) {
        return family != null && familyTiers().containsKey(family);
    }

    /** The tiers this family uses: its own from familyTiers, else compoundTiers. */
    public static List<Tier> tiersFor(String family) {
        List<Tier> own = family == null ? null : familyTiers().get(family);
        return own != null ? own : tiers();
    }

    /** Value of one compound of this tier in the global list, in tier 0 compounds. */
    public static long tierValue(int tier) {
        return tierValue(tiers(), tier);
    }

    /** Value of one compound of this family and tier, in tier 0 compounds. Past the list: x3 per extra tier. */
    public static long tierValue(String family, int tier) {
        return tierValue(tiersFor(family), tier);
    }

    private static long tierValue(List<Tier> tiers, int tier) {
        if (tier <= 0) return tiers.get(0).value();
        if (tier < tiers.size()) return tiers.get(tier).value();
        long value = tiers.get(tiers.size() - 1).value();
        for (int i = tiers.size() - 1; i < tier && value < Long.MAX_VALUE / 3; i++) value *= 3;
        return value;
    }

    /** Display name of this tier in the global list, or "Tier N" past the list. */
    public static String tierName(int tier) {
        return tierName(tiers(), tier);
    }

    /** Display name of this family's tier, or "Tier N" past the list. */
    public static String tierName(String family, int tier) {
        return tierName(tiersFor(family), tier);
    }

    private static String tierName(List<Tier> tiers, int tier) {
        return tier >= 0 && tier < tiers.size() ? tiers.get(tier).name() : "Tier " + tier;
    }

    /** Accepts "family:Name=value" with a lowercase family, a non-empty name and a positive value. */
    private static boolean isValidFamilyTierEntry(Object obj) {
        if (!(obj instanceof String entry)) return false;
        int colon = entry.indexOf(':');
        if (colon <= 0 || !entry.substring(0, colon).trim().matches("[a-z0-9_]+")) return false;
        return isValidTierEntry(entry.substring(colon + 1));
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
