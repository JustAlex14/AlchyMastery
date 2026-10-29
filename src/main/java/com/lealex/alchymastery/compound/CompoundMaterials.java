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
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Loads every data/<namespace>/compound_material/<name>.json (world load and /reload) and answers
 * "what does this item break down into?" for the destructuration chamber.
 *
 * The files are server data, so the server sends them to its players (join and /reload): on a dedicated server the
 * client fills the same list with them, and JEI, screens and tooltips read it like in single player. Until they
 * come, JEI uses the materials that ship with the mod (loadBuiltIn).
 */
@EventBusSubscriber(modid = Alchymastery.MODID)
public final class CompoundMaterials extends SimpleJsonResourceReloadListener<CompoundMaterial> {

    /** What one input item gives: material, form, compounds, DE and ticks. */
    public record Recipe(Identifier material, CompoundMaterial data, CompoundForm form, int compounds, int energy, int ticks) {}

    private static volatile Map<Identifier, CompoundMaterial> materials = Map.of();
    private static volatile int generation = 0; // bumps on every reload, so machines drop cached recipes
    /** Told when a client gets the server's materials: JEI's plugin sets it, nothing else needs it. */
    public static Runnable clientListener = () -> {};

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

    /**
     * The materials that ship with the mod, read straight from its own files (data/alchymastery/compound_material in
     * the mod jar), for a client that has not been sent the server's list yet. Does nothing once a list is loaded.
     */
    public static void loadBuiltIn(net.minecraft.core.HolderLookup.Provider registries) {
        if (!materials.isEmpty()) return;
        Map<Identifier, CompoundMaterial> found = new java.util.HashMap<>();
        try {
            var file = net.neoforged.fml.ModList.get().getModFileById(Alchymastery.MODID);
            if (file == null) return;
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
            file.getFile().getContents().visitContent("data/" + Alchymastery.MODID + "/compound_material", (path, resource) -> {
                if (!path.endsWith(".json")) return;
                String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
                try (java.io.Reader reader = resource.bufferedReader(java.nio.charset.StandardCharsets.UTF_8)) {
                    CompoundMaterial.CODEC.parse(ops, com.google.gson.JsonParser.parseReader(reader))
                            .resultOrPartial(error -> Alchymastery.LOGGER.warn("compound material {}: {}", name, error))
                            .ifPresent(material -> found.put(Identifier.fromNamespaceAndPath(Alchymastery.MODID, name), material));
                } catch (Exception broken) {
                    Alchymastery.LOGGER.warn("compound material {} could not be read", name, broken);
                }
            });
        } catch (Exception failed) {
            Alchymastery.LOGGER.warn("could not read the built-in compound materials", failed);
        }
        if (found.isEmpty() || !materials.isEmpty()) return;
        materials = Map.copyOf(found);
        generation++;
    }

    /** The server's materials, sent to its players. */
    public record Payload(Map<Identifier, CompoundMaterial> materials) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "compound_materials"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistriesTrusted(com.mojang.serialization.Codec.unboundedMap(Identifier.CODEC, CompoundMaterial.CODEC))
                        .map(Payload::new, Payload::materials);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Payload.TYPE, Payload.STREAM_CODEC, (payload, context) -> {
            // single player or the LAN host: the integrated server's list is this same one already
            if (ServerLifecycleHooks.getCurrentServer() != null) return;
            materials = Map.copyOf(payload.materials());
            generation++;
            Alchymastery.LOGGER.info("AlchyMastery: received {} compound material(s) from the server", materials.size());
            clientListener.run(); // (JEI refreshes its pages: compat/jei)
        });
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        Payload payload = new Payload(materials);
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "compound_materials"), new CompoundMaterials());
    }
}
