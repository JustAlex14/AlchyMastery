package com.lealex.alchymastery.client;

import com.lealex.alchymastery.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The void-themed particles (registry/ModParticles): one class, a Style per kind. Animated through their sprites by
 * age (or one random sprite each, for the glyphs), glowing in the dark, fading and shrinking at the end of their life.
 */
public class VoidParticle extends SingleQuadParticle {

    /**
     * How a kind moves and looks. gravity (+ falls, - rises), friction (speed kept per tick), lifetime range, size
     * range (blocks), spin (radians per tick, random direction), sway (sideways wobble), animate (sprites by age, or
     * one random sprite), alpha at full life.
     */
    public record Style(float gravity, float friction, int minLife, int maxLife, float minSize, float maxSize,
                        float spin, float sway, boolean animate, float alpha) {}

    public static final Style LINK = new Style(0F, 0.91F, 14, 22, 0.06F, 0.1F, 0F, 0F, true, 1F);
    public static final Style EMBER = new Style(-0.12F, 0.96F, 20, 36, 0.05F, 0.09F, 0F, 0.012F, true, 1F);
    public static final Style GLYPH = new Style(-0.05F, 0.94F, 30, 50, 0.07F, 0.11F, 0.04F, 0.02F, false, 0.95F);
    public static final Style DROPLET = new Style(0.25F, 0.98F, 18, 30, 0.05F, 0.08F, 0F, 0F, true, 0.9F);
    public static final Style POLLEN = new Style(-0.02F, 0.95F, 40, 70, 0.04F, 0.07F, 0.02F, 0.025F, true, 0.95F);
    public static final Style WISP = new Style(-0.08F, 0.95F, 24, 40, 0.08F, 0.13F, 0F, 0.02F, true, 0.85F);
    public static final Style SHARD = new Style(0.02F, 0.93F, 20, 34, 0.06F, 0.1F, 0.25F, 0F, true, 1F);
    public static final Style SIPHON = new Style(0F, 0.91F, 16, 24, 0.07F, 0.11F, 0.15F, 0F, true, 1F);

    private final SpriteSet sprites;
    private final Style style;
    private final float baseSize, spin, phase;

    protected VoidParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                           SpriteSet sprites, Style style) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.style = style;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = style.gravity();
        this.friction = style.friction();
        this.hasPhysics = false;
        this.lifetime = style.minLife() + random.nextInt(Math.max(1, style.maxLife() - style.minLife() + 1));
        this.baseSize = style.minSize() + random.nextFloat() * (style.maxSize() - style.minSize());
        this.quadSize = baseSize;
        this.spin = style.spin() * (random.nextBoolean() ? 1 : -1) * (0.5F + random.nextFloat());
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI * (style.spin() > 0 ? 1 : 0);
        this.alpha = style.alpha();
        if (style.animate()) setSpriteFromAge(sprites);
        else setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        oRoll = roll;
        roll += spin;
        if (style.sway() > 0) {
            xd += Mth.sin(age * 0.3F + phase) * style.sway() * 0.1;
            zd += Mth.cos(age * 0.27F + phase) * style.sway() * 0.1;
        }
        if (style.animate()) setSpriteFromAge(sprites);
        float life = (float) age / lifetime;
        alpha = style.alpha() * (life < 0.7F ? 1F : 1F - (life - 0.7F) / 0.3F); // fade at the end
    }

    @Override
    public float getQuadSize(float partial) {
        float life = (age + partial) / lifetime;
        float grow = Mth.clamp(life * 6F, 0F, 1F);            // pops in
        float shrink = life < 0.6F ? 1F : 1F - (life - 0.6F) / 0.4F * 0.7F; // shrinks away
        return baseSize * grow * shrink;
    }

    @Override
    public int getLightCoords(float partial) {
        return LightCoordsUtil.FULL_BRIGHT; // void glows in the dark
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    /** One provider per kind. */
    public record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new VoidParticle(level, x, y, z, xd, yd, zd, sprites, style);
        }
    }

    /** Registers every void particle's provider (AlchymasteryClient). */
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.VOID_LINK.get(), sprites -> new Provider(sprites, LINK));
        event.registerSpriteSet(ModParticles.VOID_EMBER.get(), sprites -> new Provider(sprites, EMBER));
        event.registerSpriteSet(ModParticles.VOID_GLYPH.get(), sprites -> new Provider(sprites, GLYPH));
        event.registerSpriteSet(ModParticles.VOID_DROPLET.get(), sprites -> new Provider(sprites, DROPLET));
        event.registerSpriteSet(ModParticles.VOID_POLLEN.get(), sprites -> new Provider(sprites, POLLEN));
        event.registerSpriteSet(ModParticles.VOID_WISP.get(), sprites -> new Provider(sprites, WISP));
        event.registerSpriteSet(ModParticles.VOID_SHARD.get(), sprites -> new Provider(sprites, SHARD));
        event.registerSpriteSet(ModParticles.VOID_SIPHON.get(), sprites -> new Provider(sprites, SIPHON));
    }
}
