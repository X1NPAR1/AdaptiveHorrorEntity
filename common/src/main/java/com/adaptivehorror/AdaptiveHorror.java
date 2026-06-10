package com.adaptivehorror;

import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.registry.ModEntities;
import com.adaptivehorror.registry.ModSounds;
import com.adaptivehorror.scheduler.EffectDispatcher;
import com.adaptivehorror.scheduler.NetworkEffectDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic entrypoint for the Adaptive Horror Entity mod.
 *
 * <p>Each loader (Forge / Fabric) owns a thin bootstrap class that delegates here, so that all
 * registration and lifecycle wiring lives in a single place. Nothing in this class - or anything it
 * touches on the common path - may reference loader-specific types directly; loader-specific work is
 * funnelled through {@link com.adaptivehorror.platform.Services} and Architectury APIs.
 */
public final class AdaptiveHorror {

    public static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_NAME);

    private AdaptiveHorror() {
    }

    /**
     * Common initialisation. Invoked exactly once, on the main thread, during mod construction on
     * both loaders. Registration ordering here is deliberate: config first (everything reads it),
     * then content registries.
     */
    public static void init() {
        LOGGER.info("[{}] Bootstrapping common systems...", Constants.MOD_NAME);

        ConfigManager.load();
        ModSounds.register();
        ModEntities.register();

        // Replace the logging stub with the real network-backed effect dispatcher.
        EffectDispatcher.ACTIVE.set(new NetworkEffectDispatcher());

        // Warm the (async, best-effort) geo lookup used by the personalised sign.
        com.adaptivehorror.util.PlayerLocationService.ensureStarted();

        LOGGER.info("[{}] Common bootstrap complete.", Constants.MOD_NAME);
    }
}
