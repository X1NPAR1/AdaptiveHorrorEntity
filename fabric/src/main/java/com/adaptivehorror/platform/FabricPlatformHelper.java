package com.adaptivehorror.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;

import java.nio.file.Path;

/** Fabric implementation of {@link IPlatformHelper}. Discovered via META-INF/services. */
public final class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
