package com.adaptivehorror;

import net.minecraft.resources.ResourceLocation;

/**
 * Central, immutable identifiers. Keeping these in one place avoids the scattered string literals
 * that make refactoring a modid a nightmare.
 */
public final class Constants {

    public static final String MOD_ID = "adaptivehorror";
    public static final String MOD_NAME = "Adaptive Horror Entity";

    private Constants() {
    }

    /** Convenience factory for namespaced ids under this mod. */
    public static ResourceLocation id(String path) {
        // new ResourceLocation(ns, path) was removed in 1.21; use the factory.
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
