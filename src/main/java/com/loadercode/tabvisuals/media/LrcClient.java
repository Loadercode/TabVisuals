// language: Java, file: LrcClient.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loadercode.tabvisuals.TabVisualsClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Synchronized lyrics from lrclib.net. Fetches on a background executor,
 * parses plain LRC lines and answers prev/current/next by media position.
 * No network call happens on the render thread.
 */
public final class LrcClient {

    /** One timed line of an LRC file. */
    public record Line(long timeMs, String text) {
    }

    /** Result of a lyrics lookup; empty lines mean "not available". */
    public record Lyrics(String title, String artist, List<Line> lines) {
        public static final Lyrics EMPTY = new Lyrics("", "", List.of());

        public boolean hasSync() {
            return !lines.isEmpty();
        }
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tabvisuals-lrclib");
        t.setDaemon(true);
        return t;
    });

    private static volatile Lyrics cache = Lyrics.EMPTY;
    private static volatile String cacheKey = "";
    private static volatile long lastRequestAt;

    private LrcClient() {
    }

    public static Lyrics cached() {
        return cache;
    }

    /**
     * Ensures lyrics for the given track are loading; fires {@code onReady}
     * with the result (possibly {@link Lyrics#EMPTY}) on the executor thread.
     */
    public static void request(String title, String artist, long durationMs, Consumer<Lyrics> onReady) {
        String key = (title + "|" + artist).toLowerCase(Locale.ROOT);
        if (key.equals(cacheKey) && cache != Lyrics.EMPTY) {
            onReady.accept(cache);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRequestAt < 1500) return; // rate limit lookups on rapid track changes
        lastRequestAt = now;
        EXEC.execute(() -> {
            Lyrics result = fetch(title, artist, durationMs);
            cacheKey = key;
            cache = result;
            try {
                onReady.accept(result);
            } catch (Throwable t) {
                TabVisualsClient.LOGGER.debug("lyrics listener failed", t);
            }
        });
    }

    /**
     * Returns the triple of lines around {@code posMs}: previous (or null),
     * current (or null), next (or null).
     */
    public static Line[] around(Lyrics lyrics, long posMs) {
        if (lyrics == null || lyrics.lines().isEmpty()) return new Line[]{null, null, null};
        List<Line> lines = lyrics.lines();
        Line prev = null, cur = null, next = null;
        for (Line line : lines) {
            if (line.timeMs() <= posMs) {
                prev = cur == null ? line : cur;
                cur = line;
            } else {
                next = line;
                break;
            }
        }
        return new Line[]{prev, cur, next};
    }

    private static Lyrics fetch(String title, String artist, long durationMs) {
        if (title == null || title.isBlank()) return Lyrics.EMPTY;
        StringBuilder url = new StringBuilder("https://lrclib.net/api/get?artist_name=")
                .append(enc(artist))
                .append("&track_name=").append(enc(title))
                .append("&duration=").append(Math.max(1, durationMs / 1000));
        try {
            JsonObject o = get(url.toString());
            if (o == null) return Lyrics.EMPTY;
            String synced = o.has("syncedLyrics") && o.get("syncedLyrics").isJsonPrimitive()
                    ? o.get("syncedLyrics").getAsString() : "";
            List<Line> lines = parseLrc(synced);
            if (lines.isEmpty()) {
                // fall back to the search endpoint when the exact-match get() misses
                o = search(title, artist);
                if (o != null && o.has("syncedLyrics") && o.get("syncedLyrics").isJsonPrimitive()) {
                    lines = parseLrc(o.get("syncedLyrics").getAsString());
                }
                if (o == null) return Lyrics.EMPTY;
            }
            String t = str(o, "trackName"), a = str(o, "artistName");
            return new Lyrics(t.isBlank() ? title : t, a.isBlank() ? artist : a, lines);
        } catch (Exception e) {
            TabVisualsClient.LOGGER.debug("lrclib failed: {}", e.toString());
            return Lyrics.EMPTY;
        }
    }

    private static JsonObject search(String title, String artist) {
        try {
            String body = getBody("https://lrclib.net/api/search?track_name=" + enc(title)
                    + "&artist_name=" + enc(artist) + "&limit=1");
            if (body == null || body.isBlank() || body.charAt(0) != '[') return null;
            var arr = JsonParser.parseString(body).getAsJsonArray();
            return arr.isEmpty() ? null : arr.get(0).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject get(String url) {
        try {
            String body = getBody(url);
            if (body == null || body.isBlank() || body.charAt(0) != '{') return null;
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String getBody(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "TabVisuals/1.0 (github.com/Loadercode/TabVisuals)");
        conn.setRequestProperty("Accept", "application/json");
        try {
            if (conn.getResponseCode() != 200) return null;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
    }

    static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null || lrc.isBlank()) return out;
        for (String raw : lrc.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) != '[') continue;
            int close = line.indexOf(']');
            if (close < 0) continue;
            String tag = line.substring(1, close);
            String text = line.substring(close + 1).trim();
            long ms = parseTag(tag);
            if (ms >= 0) out.add(new Line(ms, text));
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return out;
    }

    /** Accepts [mm:ss], [mm:ss.xx] and [mm:ss.xxx] tags; returns -1 otherwise. */
    private static long parseTag(String tag) {
        int colon = tag.indexOf(':');
        if (colon <= 0 || tag.startsWith("ar") || tag.startsWith("al")
                || tag.startsWith("ti") || tag.startsWith("by")) return -1;
        try {
            long min = Long.parseLong(tag.substring(0, colon).trim());
            String rest = tag.substring(colon + 1).trim();
            long sec;
            long fracMs = 0;
            int dot = rest.indexOf('.');
            if (dot >= 0) {
                sec = Long.parseLong(rest.substring(0, dot));
                String frac = rest.substring(dot + 1);
                fracMs = (long) (Double.parseDouble("0." + frac) * 1000);
            } else {
                sec = Long.parseLong(rest);
            }
            if (min < 0 || sec < 0 || sec > 59) return -1;
            return min * 60_000 + sec * 1000 + fracMs;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}
