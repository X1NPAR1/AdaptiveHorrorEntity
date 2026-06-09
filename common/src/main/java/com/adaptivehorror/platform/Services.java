package com.adaptivehorror.platform;

import com.adaptivehorror.AdaptiveHorror;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Service locator for loader-specific implementations.
 *
 * <p>Each loader module ships a {@code META-INF/services} entry pointing at its concrete
 * implementation of the interfaces below. At runtime the JVM's {@link ServiceLoader} resolves the
 * single implementation present on the classpath. This is the "MultiLoader" abstraction pattern: the
 * common code is written against interfaces only, and never imports a Forge or Fabric type.
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final IRegistryHelper REGISTRY = load(IRegistryHelper.class);
    public static final INetworkHelper NETWORK = load(INetworkHelper.class);

    private Services() {
    }

    private static <T> T load(Class<T> clazz) {
        // ServiceLoader.findFirst() is Java 9+; we target Java 8 (Forge 1.16.5), so iterate manually.
        final Iterator<T> it = ServiceLoader.load(clazz).iterator();
        if (!it.hasNext()) {
            throw new IllegalStateException("No loader service found for " + clazz.getName()
                    + " - is the platform module's META-INF/services entry present?");
        }
        final T loaded = it.next();
        AdaptiveHorror.LOGGER.debug("Loaded platform service {} -> {}",
                clazz.getSimpleName(), loaded.getClass().getName());
        return loaded;
    }
}
