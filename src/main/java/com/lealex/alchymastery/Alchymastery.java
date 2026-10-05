package com.lealex.alchymastery;

import com.lealex.alchymastery.registry.ModRegistries;
import com.lealex.alchyx.AlchyXApi;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.lealex.alchyx.multiblock.CoreTracker;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.transfer.fluid.DispenseFluidContainer;
import net.minecraft.world.level.block.DispenserBlock;

@Mod(Alchymastery.MODID)
public class Alchymastery {
    public static final String MODID = "alchymastery";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Alchymastery(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.register(modEventBus);

        // The 4 part blocks older versions placed (before shells) still count as machine parts in old worlds
        AlchyXApi.registerLegacyPart(state -> state.is(ModRegistries.CHAMBER_PART.get())
                || state.is(ModRegistries.CHAMBER_PART_STAIRS.get())
                || state.is(ModRegistries.CHAMBER_PART_GLASS.get())
                || state.is(ModRegistries.CHAMBER_PART_GLOW.get()));

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // The condensator core is drawn by its renderer: structure previews (Codex, ghost blocks) show its preview model
        com.lealex.alchyx.multiblock.PreviewLooks.register(ModRegistries.CONDENSATOR_CORE.get(),
                state -> state.setValue(com.lealex.alchymastery.block.CondensatorCoreBlock.PREVIEW, true));
        // Same for the wormhole (a void conduit drawn by its renderer)
        com.lealex.alchyx.multiblock.PreviewLooks.register(ModRegistries.WORMHOLE.get(),
                state -> state.setValue(com.lealex.alchymastery.block.WormholeBlock.PREVIEW, true));
        // And the distortion crystal (its crystal is drawn by its renderer; the preview model adds a still one)
        com.lealex.alchyx.multiblock.PreviewLooks.register(ModRegistries.DISTORTION_CRYSTAL.get(),
                state -> state.setValue(com.lealex.alchymastery.block.DistortionCrystalBlock.PREVIEW, true));
        LOGGER.info("AlchyMastery common setup");
        // Dispensers can pour and pick up distortion fluid with its bucket, like water
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(ModRegistries.DISTORTION_FLUID_BUCKET.get(), DispenseFluidContainer.getInstance());
            DispenserBlock.registerBehavior(ModRegistries.LIQUID_EXPERIENCE_BUCKET.get(), DispenseFluidContainer.getInstance());
        });
        // The nether-brick piston look strikes with its matching head
        AlchyXApi.registerPistonHead(ModRegistries.CHAMBER_PART_PISTON.get(),
                () -> ModRegistries.CHAMBER_PART_PISTON_HEAD.get().defaultBlockState());
    }

    // Lets hoppers and pipes insert lapis into the matrix's fuel slot
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
            com.lealex.alchymastery.compat.curios.CuriosCompat.registerCapabilities(event); // the siphon as a curio
        }
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModRegistries.DISTORTION_MATRIX_BE.get(),
                (matrix, side) -> matrix.getFuelHandler());

        // Every part of a formed machine gives pipes and hoppers access to its core's items (the matrix: its fuel slot).
        event.registerBlock(
                Capabilities.Item.BLOCK,
                (level, pos, state, blockEntity, side) -> {
                    MultiblockCoreBlockEntity core = CoreTracker.formedCoreAt(level, pos);
                    return core != null ? core.getItemHandler() : null;
                },
                ModRegistries.CHAMBER_PART.get(), ModRegistries.CHAMBER_PART_STAIRS.get(),
                ModRegistries.CHAMBER_PART_GLASS.get(), ModRegistries.CHAMBER_PART_GLOW.get()); // shells: AlchyX

        // Fluids (water in, distortion fluid out...) through any part of a formed machine, and through its core
        event.registerBlock(
                Capabilities.Fluid.BLOCK,
                (level, pos, state, blockEntity, side) -> {
                    MultiblockCoreBlockEntity core = CoreTracker.formedCoreAt(level, pos);
                    return core != null ? core.getFluidHandler() : null;
                },
                ModRegistries.CHAMBER_PART.get(), ModRegistries.CHAMBER_PART_STAIRS.get(),
                ModRegistries.CHAMBER_PART_GLASS.get(), ModRegistries.CHAMBER_PART_GLOW.get()); // shells: AlchyX
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                ModRegistries.CONDENSATOR_CORE_BE.get(),
                (core, side) -> core.getFluidHandler());
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                ModRegistries.RECONSTRUCTION_CORE_BE.get(),
                (core, side) -> core.getFluidHandler());
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModRegistries.RECONSTRUCTION_CORE_BE.get(),
                (core, side) -> core.getItemHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModRegistries.RENDERING_CORE_BE.get(), (core, side) -> core.getItemHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModRegistries.RENDERING_CORE_BE.get(), (core, side) -> core.getFluidHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModRegistries.EXPERIENCE_NEXUS_BE.get(), (core, side) -> core.getItemHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModRegistries.EXPERIENCE_NEXUS_BE.get(), (core, side) -> core.getFluidHandler());
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModRegistries.ALCHEMICAL_NEXUS_BE.get(),
                (core, side) -> core.getItemHandler());
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                ModRegistries.ALCHEMICAL_NEXUS_BE.get(),
                (core, side) -> core.getFluidHandler());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("AlchyMastery: server starting");
    }
}