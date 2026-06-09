package com.adaptivehorror.config;

import com.adaptivehorror.AdaptiveHorror;
import com.adaptivehorror.Constants;
import com.adaptivehorror.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads, persists and exposes the singleton {@link HorrorConfig}.
 *
 * <p>The config is read once at startup and held in memory; logic classes call {@link #get()} on the
 * hot path (no file I/O per tick). Writes only happen when the file is created or migrated, so this
 * stays cheap. The file is pretty-printed JSON for hand-editing.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = Constants.MOD_ID + ".json";

    private static volatile HorrorConfig active = new HorrorConfig();

    private ConfigManager() {
    }

    public static HorrorConfig get() {
        return active;
    }

    /** Reads the config from disk, creating it with defaults if absent. Never throws upward. */
    public static synchronized void load() {
        final Path path = configPath();
        try {
            if (Files.notExists(path)) {
                AdaptiveHorror.LOGGER.info("No config found, writing defaults to {}", path);
                save(new HorrorConfig());
                return;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                final HorrorConfig parsed = GSON.fromJson(reader, HorrorConfig.class);
                active = parsed != null ? parsed : new HorrorConfig();
            }
            AdaptiveHorror.LOGGER.info("Loaded config from {}", path);
        } catch (Exception e) {
            // A malformed config must never crash the game; fall back to safe defaults.
            AdaptiveHorror.LOGGER.error("Failed to read config, using defaults", e);
            active = new HorrorConfig();
        }
    }

    /** Persists the given config and adopts it as active. */
    public static synchronized void save(HorrorConfig config) {
        active = config;
        final Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            AdaptiveHorror.LOGGER.error("Failed to write config to {}", path, e);
        }
    }

    private static Path configPath() {
        return Services.PLATFORM.getConfigDirectory().resolve(FILE_NAME);
    }
}
