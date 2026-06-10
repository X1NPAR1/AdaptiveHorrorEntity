package com.adaptivehorror.util;

import com.adaptivehorror.AdaptiveHorror;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Resolves the host machine's name and a rough city/country, used by the "I'm near you" sign to make
 * the scare personal. The geo lookup is a best-effort call to a free IP-geolocation service on a
 * daemon thread; it is cached, time-boxed, and fails silently to placeholders so it can never block
 * or crash the game. Nothing is sent anywhere - the result is only ever shown to the player.
 */
public final class PlayerLocationService {

    private static volatile String city = "?";
    private static volatile String country = "?";
    private static volatile boolean started;

    private PlayerLocationService() {
    }

    /** Kicks off the one-time async geo lookup. Safe to call repeatedly. */
    public static synchronized void ensureStarted() {
        if (started) {
            return;
        }
        started = true;
        final Thread t = new Thread(PlayerLocationService::fetch, "adaptivehorror-geo");
        t.setDaemon(true);
        t.start();
    }

    public static String city() {
        return city;
    }

    public static String country() {
        return country;
    }

    /** The local computer name (COMPUTERNAME env on Windows, hostname otherwise). */
    public static String hostName() {
        final String env = System.getenv("COMPUTERNAME");
        if (env != null && !env.isBlank()) {
            return env;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "?";
        }
    }

    private static void fetch() {
        try {
            final URL url = new URL("http://ip-api.com/json/?fields=city,country");
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "AdaptiveHorror");
            final StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            city = extract(sb.toString(), "city", city);
            country = extract(sb.toString(), "country", country);
        } catch (Exception e) {
            AdaptiveHorror.LOGGER.debug("Geo lookup failed (using placeholders): {}", e.toString());
        }
    }

    /** Minimal, dependency-free extraction of a string JSON field value. */
    private static String extract(String json, String field, String fallback) {
        final String needle = "\"" + field + "\":\"";
        final int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        final int from = start + needle.length();
        final int end = json.indexOf('"', from);
        return end > from ? json.substring(from, end) : fallback;
    }
}
