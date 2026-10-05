package com.lealex.alchymastery.compound;

import com.lealex.alchymastery.Alchymastery;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Loads every data/<namespace>/compound_material/<name>.json (world load and /reload) and answers
 * "what does this item break down into?" for the destructuration chamber.
 */
@EventBusSubscriber(modid = Alchymastery.MODID)
public final class CompoundMaterials extends SimpleJsonResourceReloadListener<CompoundMaterial> {

    /** What one input item gives: material, form, compounds, DE and ticks. */
    public record Recipe(Identifier material, CompoundMaterial data, CompoundForm form, int compounds, int energy, int ticks) {}

    private static volatile Map<Identifier, CompoundMaterial> materials = Map.of();
    private static volatile int generation = 0; // bumps on every reload, so machines drop cached recipes

    private CompoundMaterials() {
        super(CompoundMaterial.CODEC, FileToIdConverter.json("compound_material"));
    }

    /** The material with this id, or null if no file defines it. */
    public static @Nullable CompoundMaterial get(Identifier id) {
        return materials.get(id);
    }

    /** Every loaded material, by id. */
    public static Map<Identifier, CompoundMaterial> all() {
        return materials;
    }

    public static int generation() {
        return generation;
    }

    /** The recipe for this item, or null if no material accepts it. */
    public static @Nullable Recipe find(ItemResource item) {
        if (item.isEmpty()) return null;
        for (Map.Entry<Identifier, CompoundMaterial> material : materials.entrySet()) {
            for (Map.Entry<CompoundForm, Ingredient> form : material.getValue().forms().entrySet()) {
                if (item.test(form.getValue())) {
                    CompoundForm f = form.getKey();
                    return new Recipe(material.getKey(), material.getValue(), f,
                            material.getValue().compoundsFor(f), f.energy, f.ticks);
                }
            }
        }
        return null;
    }

    @Override
    protected void apply(Map<Identifier, CompoundMaterial> loaded, ResourceManager manager, ProfilerFiller profiler) {
        materials = Map.copyOf(loaded);
        generation++;
        Alchymastery.LOGGER.info("AlchyMastery: loaded {} compound material(s): {}", loaded.size(), loaded.keySet());
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "compound_materials"), new CompoundMaterials());
    }
}
