package com.lealex.alchymastery;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side settings (visuals only), saved in config/alchymastery-client.toml. Read through the getters: they
 * fall back to the defaults while the file isn't loaded yet.
 */
public final class ClientConfig {
    private ClientConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Rift Genesis: what a nexus plays when it forms. Its blocks shatter and turn see-through;",
                        "each machine core in turn tears a rift open and its miniature chamber comes out of it;",
                        "then every block of the nexus comes back through a rift of its own, from the middle",
                        "outward; then glyphs bind the miniatures to the nexus. Times are in ticks (20 per second).")
                .push("nexusFormation");
    }

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Play the animation at all (off: everything simply appears).")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue STAGGER = BUILDER
            .comment("Ticks between one machine core opening its rift and the next.")
            .defineInRange("stagger", 48, 0, 200);
    public static final ModConfigSpec.IntValue RIFT_OPEN_TICKS = BUILDER
            .comment("A core's rift tearing open.")
            .defineInRange("riftOpenTicks", 22, 1, 200);
    public static final ModConfigSpec.IntValue EMERGE_TICKS = BUILDER
            .comment("The miniature chamber coming out of the rift.")
            .defineInRange("emergeTicks", 50, 1, 200);
    public static final ModConfigSpec.IntValue RIFT_CLOSE_TICKS = BUILDER
            .comment("The rift closing behind it.")
            .defineInRange("riftCloseTicks", 18, 1, 200);
    public static final ModConfigSpec.DoubleValue EMERGE_SPIN = BUILDER
            .comment("Turns the miniature spins as it comes out.")
            .defineInRange("emergeSpin", 0.75, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue RIFT_SIZE = BUILDER
            .comment("Size of a core's rift, in blocks.")
            .defineInRange("riftSize", 1.6, 0.5, 4.0);
    public static final ModConfigSpec.IntValue REVEAL_DELAY = BUILDER
            .comment("Ticks between the last miniature and the first block coming back.")
            .defineInRange("revealDelay", 16, 0, 200);
    public static final ModConfigSpec.DoubleValue REVEAL_SPREAD = BUILDER
            .comment("Ticks per block of distance from the nexus: how fast the blocks come back outward.")
            .defineInRange("revealSpread", 3.0, 0.0, 40.0);
    public static final ModConfigSpec.IntValue REVEAL_RIFT_TICKS = BUILDER
            .comment("A block's own rift: it opens, the block comes back halfway, it closes.")
            .defineInRange("revealRiftTicks", 24, 2, 200);
    public static final ModConfigSpec.DoubleValue VEIL_OPACITY = BUILDER
            .comment("How visible the see-through blocks are while they wait (0: invisible).")
            .defineInRange("veilOpacity", 0.3, 0.0, 1.0);
    public static final ModConfigSpec.IntValue BIND_TICKS = BUILDER
            .comment("The finale: glyphs streaming from the miniatures into the nexus (0: no finale).")
            .defineInRange("bindTicks", 80, 0, 400);
    public static final ModConfigSpec.DoubleValue PARTICLES = BUILDER
            .comment("Particle amount (0: none, 1: normal, 2: double).")
            .defineInRange("particles", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.BooleanValue SOUNDS = BUILDER
            .comment("Play the animation's sounds.")
            .define("sounds", true);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    /** A snapshot of the Rift Genesis settings (defaults while the config isn't loaded). */
    public record Genesis(boolean enabled, int stagger, int riftOpen, int emerge, int riftClose, float emergeSpin,
                          float riftSize, int revealDelay, float revealSpread, int revealRift, float veilOpacity,
                          int bind, float particles, boolean sounds) {
        public static final Genesis DEFAULT = new Genesis(true, 48, 22, 50, 18, 0.75F, 1.6F, 16, 3F, 24, 0.3F, 80, 1F, true);

        /** The nexus core charging before it blasts a machine core's rift open (warden style). */
        public static final int CHARGE = 16;

        /** Ticks from the nexus core charging for a machine to the rift closed behind its miniature. */
        public int machineTicks() {
            return CHARGE + riftOpen + emerge + riftClose;
        }

        /**
         * The whole animation's length (the server waits that long before the nexus counts as formed), for this
         * many machines and the farthest converted block's distance from the nexus.
         */
        public int totalTicks(int machines, double farthest) {
            return revealStart(machines) + (int) Math.round(farthest * revealSpread) + 3 + revealRift + bind;
        }

        /** When the blocks start coming back, for this many machines. */
        public int revealStart(int machines) {
            return Math.max(0, machines - 1) * stagger + machineTicks() + revealDelay;
        }
    }

    public static Genesis genesis() {
        try {
            return new Genesis(ENABLED.get(), STAGGER.get(), RIFT_OPEN_TICKS.get(), EMERGE_TICKS.get(), RIFT_CLOSE_TICKS.get(),
                    EMERGE_SPIN.get().floatValue(), RIFT_SIZE.get().floatValue(), REVEAL_DELAY.get(),
                    REVEAL_SPREAD.get().floatValue(), REVEAL_RIFT_TICKS.get(), VEIL_OPACITY.get().floatValue(),
                    BIND_TICKS.get(), PARTICLES.get().floatValue(), SOUNDS.get());
        } catch (IllegalStateException notLoadedYet) {
            return Genesis.DEFAULT;
        }
    }
}
