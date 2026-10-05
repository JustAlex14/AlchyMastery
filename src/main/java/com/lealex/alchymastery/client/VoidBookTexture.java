package com.lealex.alchymastery.client;

import com.lealex.alchymastery.Alchymastery;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.InputStream;
import java.util.Random;

/**
 * The Alchemist's Codex cover and pages, alive: the book sheet Patchouli draws from ({@code book_texture} in
 * book.json) is replaced by a texture repainted every tick while a Patchouli book is open. The pages drift with a
 * slow violet nebula and a few twinkling stars, the cover breathes, a light runs around its rim and the spine's
 * seam pulses. Base colors come from textures/gui/void_book.png (static fallback), which parts move from
 * void_book_mask.png (red: 1 page, 2 cover, 3 seam, 4 rim), both made by tools/book_textures.py.
 * No Patchouli code is touched: it simply finds this texture under the id it already uses.
 */
@EventBusSubscriber(modid = Alchymastery.MODID, value = Dist.CLIENT)
public final class VoidBookTexture {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Alchymastery.MODID, "textures/gui/void_book.png");
    private static final Identifier MASK = Identifier.fromNamespaceAndPath(Alchymastery.MODID, "textures/gui/void_book_mask.png");
    private static final int W = 272, H = 180, NOISE = 64, STARS = 70;

    private static DynamicTexture texture;
    private static int[] base;
    private static byte[] kind;
    private static float[] noiseA, noiseB;
    private static int[] stars; // x, y, phase (ticks), period (ticks)
    private static boolean failed;
    private static int ticks;

    private VoidBookTexture() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (failed) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null || !screen.getClass().getName().startsWith("vazkii.patchouli")) return;
        try {
            if (texture == null) load(mc);
            paint(++ticks);
            texture.upload();
        } catch (Exception e) {
            failed = true;
            Alchymastery.LOGGER.warn("Codex animation disabled, the static void book stays", e);
        }
    }

    private static void load(Minecraft mc) throws Exception {
        NativeImage image;
        try (InputStream in = mc.getResourceManager().open(ID)) {
            image = NativeImage.read(in);
        }
        base = new int[W * H];
        kind = new byte[W * H];
        try (InputStream in = mc.getResourceManager().open(MASK); NativeImage mask = NativeImage.read(in)) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    base[y * W + x] = image.getPixel(x, y);
                    kind[y * W + x] = (byte) ARGB.red(mask.getPixel(x, y));
                }
            }
        }
        Random random = new Random(0x5EED_B00CL);
        noiseA = noise(random);
        noiseB = noise(random);
        stars = new int[STARS * 4];
        for (int i = 0; i < STARS; i++) {
            int x, y, tries = 0;
            do {
                x = random.nextInt(W);
                y = random.nextInt(H);
            } while (kind[y * W + x] != 1 && ++tries < 50);
            stars[i * 4] = x;
            stars[i * 4 + 1] = y;
            stars[i * 4 + 2] = random.nextInt(400);
            stars[i * 4 + 3] = 60 + random.nextInt(140);
        }
        texture = new DynamicTexture(() -> "alchymastery:void_book", image);
        mc.getTextureManager().register(ID, texture);
    }

    /** A tileable grid of random values, smoothed once so sampling it looks like soft clouds. */
    private static float[] noise(Random random) {
        float[] raw = new float[NOISE * NOISE], out = new float[NOISE * NOISE];
        for (int i = 0; i < raw.length; i++) raw[i] = random.nextFloat();
        for (int y = 0; y < NOISE; y++) {
            for (int x = 0; x < NOISE; x++) {
                float sum = 0;
                for (int dy = -2; dy <= 2; dy++)
                    for (int dx = -2; dx <= 2; dx++)
                        sum += raw[Math.floorMod(y + dy, NOISE) * NOISE + Math.floorMod(x + dx, NOISE)];
                out[y * NOISE + x] = sum / 25;
            }
        }
        // stretch the (now narrow) range back to 0..1
        float min = 1, max = 0;
        for (float v : out) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        for (int i = 0; i < out.length; i++) out[i] = (out[i] - min) / (max - min);
        return out;
    }

    private static float sample(float[] field, float x, float y) {
        int x0 = Mth.floor(x), y0 = Mth.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        int ax = Math.floorMod(x0, NOISE), bx = Math.floorMod(x0 + 1, NOISE);
        int ay = Math.floorMod(y0, NOISE) * NOISE, by = Math.floorMod(y0 + 1, NOISE) * NOISE;
        float top = Mth.lerp(fx, field[ay + ax], field[ay + bx]);
        float bottom = Mth.lerp(fx, field[by + ax], field[by + bx]);
        return Mth.lerp(fy, top, bottom);
    }

    private static void paint(int t) {
        NativeImage image = texture.getPixels();
        float rimAngle = t * 0.035f;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x, c = base[i];
                switch (kind[i]) {
                    case 1 -> { // pages: a slow violet nebula with teal wisps
                        float a = sample(noiseA, x * 0.09f + t * 0.012f, y * 0.09f + t * 0.004f);
                        float b = sample(noiseB, x * 0.05f - t * 0.006f, y * 0.05f + t * 0.009f);
                        float violet = smooth(a * 0.65f + b * 0.35f, 0.45f, 1f);
                        float teal = smooth(b, 0.7f, 1f) * (1 - violet);
                        c = add(c, (int) (violet * 30 + teal * 6), (int) (violet * 11 + teal * 20), (int) (violet * 46 + teal * 26));
                    }
                    case 2 -> { // cover: a breath of light running across it
                        float w = 0.5f + 0.5f * Mth.sin((x + y) * 0.07f - t * 0.12f);
                        w = w * w * w;
                        c = add(c, (int) (w * 16), (int) (w * 6), (int) (w * 30));
                    }
                    case 3 -> { // spine seam: a pulse travelling down it
                        float p = 0.55f + 0.45f * Mth.sin(t * 0.16f - y * 0.15f);
                        c = ARGB.color(ARGB.alpha(c), (int) (ARGB.red(c) * p), (int) (ARGB.green(c) * p), Math.min(255, (int) (ARGB.blue(c) * (0.6f + 0.4f * p))));
                    }
                    case 4 -> { // rim: a light circling the book
                        float angle = (float) Math.atan2((y - H / 2f) / H, (x - W / 2f) / W);
                        float h = Mth.cos(angle - rimAngle);
                        h = h > 0 ? h * h * h * h * h * h * h * h : 0;
                        c = ARGB.color(ARGB.alpha(c), mix(ARGB.red(c), 235, h), mix(ARGB.green(c), 200, h), mix(ARGB.blue(c), 255, h));
                    }
                    default -> {
                        continue;
                    }
                }
                image.setPixel(x, y, c);
            }
        }
        for (int s = 0; s < STARS; s++) { // a few stars blinking in the void, never bright enough to hide a word
            int x = stars[s * 4], y = stars[s * 4 + 1], period = stars[s * 4 + 3];
            int i = y * W + x;
            if (kind[i] != 1) continue;
            float phase = ((t + stars[s * 4 + 2]) % period) / (float) period;
            float glow = Mth.sin(phase * Mth.PI);
            glow = glow * glow * glow * glow;
            if (glow < 0.02f) continue;
            image.setPixel(x, y, add(image.getPixel(x, y), (int) (glow * 120), (int) (glow * 95), (int) (glow * 150)));
        }
    }

    private static float smooth(float v, float from, float to) {
        float k = Mth.clamp((v - from) / (to - from), 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static int add(int c, int r, int g, int b) {
        return ARGB.color(ARGB.alpha(c), Math.min(255, ARGB.red(c) + r), Math.min(255, ARGB.green(c) + g), Math.min(255, ARGB.blue(c) + b));
    }

    private static int mix(int from, int to, float k) {
        return (int) (from + (to - from) * k);
    }
}
