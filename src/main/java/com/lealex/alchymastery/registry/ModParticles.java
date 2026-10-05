package com.lealex.alchymastery.registry;

import com.lealex.alchymastery.Alchymastery;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Void-themed particles (client/VoidParticle draws them; sprites in textures/particle, made by
 * tools/particle_textures.py). Each machine has its own, shown while it works and while its chamber forms:
 * void_ember (destructuration: nether embers with a void heart), void_glyph (transmutation: corrupted runes),
 * void_droplet (condensator), void_pollen (reconstruction), void_wisp (rendering: soul wisps), void_shard
 * (the distortion chamber: amethyst shards); void_link carries distortion energy from a chamber to a machine.
 */
public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, Alchymastery.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_LINK = simple("void_link");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_EMBER = simple("void_ember");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_GLYPH = simple("void_glyph");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_DROPLET = simple("void_droplet");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_POLLEN = simple("void_pollen");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_WISP = simple("void_wisp");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_SHARD = simple("void_shard");
    /** Distortion drawn from a machine's wormhole into its core (while it forms and while it works). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_SIPHON = simple("void_siphon");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name) {
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
