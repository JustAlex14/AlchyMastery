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

    static final ModConfigSpec SPEC = BUILDER.build();

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
