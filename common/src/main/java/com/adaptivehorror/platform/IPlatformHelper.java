package com.adaptivehorror.platform;

import java.nio.file.Path;

/** Loader-specific environment queries. */
public interface IPlatformHelper {

    /** Human-readable loader name, e.g. {@code "Forge"} or {@code "Fabric"}. */
    String getPlatformName();

    /** Whether another mod with the given id is present. */
    boolean isModLoaded(String modId);

    /** True in a development (deobfuscated) workspace - used to gate verbose debug behaviour. */
    boolean isDevelopmentEnvironment();

    /** Whether this JVM is running a physical client (vs. a dedicated server). */
    boolean isClient();

    /** The platform config directory, e.g. {@code .minecraft/config}. */
    Path getConfigDirectory();
}
