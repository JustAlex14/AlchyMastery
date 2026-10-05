package com.lealex.alchymastery.multiblock;

import com.lealex.alchymastery.Alchymastery;
import net.minecraft.resources.Identifier;

/** Ids of AlchyMastery's machine pattern files (data/alchymastery/multiblock/<name>.json, loaded by AlchyX). */
public final class ModPatterns {
    private ModPatterns() {}

    public static final Identifier DISTORTION_CHAMBER = id("distortion_chamber");
    public static final Identifier DESTRUCTURATION_CHAMBER = id("destructuration_chamber");
    public static final Identifier TRANSMUTATION_CHAMBER = id("transmutation_chamber");
    public static final Identifier RECONSTRUCTION_CHAMBER = id("reconstruction_chamber");
    public static final Identifier ALCHEMICAL_NEXUS = id("alchemical_nexus");
    public static final Identifier DISTORTION_CONDENSATOR = id("distortion_condensator");
    public static final Identifier RENDERING_CAULDRON = id("rendering_cauldron");
    public static final Identifier EXPERIENCE_NEXUS = id("experience_nexus");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Alchymastery.MODID, path);
    }
}
