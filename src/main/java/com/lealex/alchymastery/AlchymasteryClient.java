package com.lealex.alchymastery;

import com.lealex.alchymastery.client.CondensatorScreen;
import com.lealex.alchymastery.client.ReconstructionScreen;
import com.lealex.alchymastery.client.NexusScreen;
import com.lealex.alchymastery.client.RenderingScreen;
import com.lealex.alchymastery.client.NexusCoreRenderer;
import com.lealex.alchymastery.client.RenderingCoreRenderer;
import com.lealex.alchymastery.client.ReconstructionCoreRenderer;
import com.lealex.alchymastery.client.CondensatorCoreRenderer;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import com.lealex.alchymastery.client.DestructurationScreen;
import com.lealex.alchymastery.client.TransmutationCoreRenderer;
import com.lealex.alchymastery.client.TransmutationScreen;
import com.lealex.alchymastery.client.DistortionMatrixRenderer;
import com.lealex.alchymastery.client.DistortionMatrixScreen;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class only loads on the client, never on a dedicated server.
@Mod(value = Alchymastery.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Alchymastery.MODID, value = Dist.CLIENT)
public class AlchymasteryClient {
    // The void particles (client/VoidParticle)
    @SubscribeEvent
    static void registerParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        com.lealex.alchymastery.client.VoidParticle.registerProviders(event);
    }

    public AlchymasteryClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // The Codex's paper: old at first, the void's by the end (AlchyX BookPapers; a no-op without Patchouli)
        com.lealex.alchymastery.client.VoidBookTexture.register();
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Alchymastery.LOGGER.info("HELLO FROM CLIENT SETUP");
        Alchymastery.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        // The ghost figures the animation files can use ("figure": "alchymastery:linked_ghost" / "ghost_witch")
        event.enqueueWork(com.lealex.alchymastery.client.GhostFigures::register);
        // The machine parts they can place (the book, the condensator's conduit, the crystal): what miniatures show
        event.enqueueWork(com.lealex.alchymastery.client.MachineFigures::register);
    }

    // Tells the client which screen to show for each menu type
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistries.DISTORTION_MATRIX_MENU.get(), DistortionMatrixScreen::new);
        event.register(ModRegistries.DESTRUCTURATION_MENU.get(), DestructurationScreen::new);
        event.register(ModRegistries.TRANSMUTATION_MENU.get(), TransmutationScreen::new);
        event.register(ModRegistries.CONDENSATOR_MENU.get(), CondensatorScreen::new);
        event.register(ModRegistries.RECONSTRUCTION_MENU.get(), ReconstructionScreen::new);
        event.register(ModRegistries.ALCHEMICAL_NEXUS_MENU.get(), NexusScreen::new);
        event.register(ModRegistries.RENDERING_MENU.get(), RenderingScreen::new);
        event.register(ModRegistries.EXPERIENCE_NEXUS_MENU.get(), com.lealex.alchymastery.client.ExperienceNexusScreen::new);
    }

    // How distortion fluid looks in the world: vanilla's grey water textures, tinted purple
    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(
                        new Material(Identifier.withDefaultNamespace("block/water_still")),
                        new Material(Identifier.withDefaultNamespace("block/water_flow")),
                        new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                        FluidTintSources.constant(ModRegistries.DISTORTION_FLUID_COLOR)),
                ModRegistries.DISTORTION_FLUID, ModRegistries.DISTORTION_FLUID_FLOWING);
        // Liquid experience: the same water textures, tinted lime green
        event.register(new FluidModel.Unbaked(
                        new Material(Identifier.withDefaultNamespace("block/water_still")),
                        new Material(Identifier.withDefaultNamespace("block/water_flow")),
                        new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                        FluidTintSources.constant(ModRegistries.LIQUID_EXPERIENCE_COLOR)),
                ModRegistries.LIQUID_EXPERIENCE, ModRegistries.LIQUID_EXPERIENCE_FLOWING);
    }

    // Draws the floating fuel around the matrix
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.DISTORTION_MATRIX_BE.get(), DistortionMatrixRenderer::new);
        // (the destructuration chamber's floating item, pistons and particles: data/alchymastery/machine_animation)
        // Shelves, target and the transformation flight of the transmutation chamber
        event.registerBlockEntityRenderer(ModRegistries.TRANSMUTATION_CORE_BE.get(), TransmutationCoreRenderer::new);
        // The condensator's conduit (awake while working)
        event.registerBlockEntityRenderer(ModRegistries.CONDENSATOR_CORE_BE.get(), CondensatorCoreRenderer::new);
        // Inputs merging into the result over the reconstruction chamber's brewing stand
        event.registerBlockEntityRenderer(ModRegistries.RECONSTRUCTION_CORE_BE.get(), ReconstructionCoreRenderer::new);
        // The nexus's animated miniatures of the four machines
        event.registerBlockEntityRenderer(ModRegistries.ALCHEMICAL_NEXUS_BE.get(), context -> new NexusCoreRenderer<>(context));
        // The experience nexus's miniatures (destructuration, condensator, rendering hut)
        event.registerBlockEntityRenderer(ModRegistries.EXPERIENCE_NEXUS_BE.get(), context -> new NexusCoreRenderer<>(context));
        // The linked player (or an evoker) brewing at the rendering cauldron
        event.registerBlockEntityRenderer(ModRegistries.RENDERING_CORE_BE.get(), RenderingCoreRenderer::new);
        // The wormholes: void conduits (sealed / turning / eye open, from their block state)
        event.registerBlockEntityRenderer(ModRegistries.WORMHOLE_BE.get(), com.lealex.alchymastery.client.WormholeRenderer::new);
        event.registerBlockEntityRenderer(ModRegistries.DISTORTION_CRYSTAL_BE.get(), com.lealex.alchymastery.client.DistortionCrystalRenderer::new);
    }
}
