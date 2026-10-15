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

    /** One queued lookup. A single pending slot coalesces rapid track changes. */
    private record Request(String key, String title, String artist, long durationMs, Consumer<Lyrics> onReady) {
    }

    /** Memoized around()-result: reuses the returned array while the triple is unchanged. */
    private record Around(Lyrics lyrics, Line prev, Line cur, Line next, Line[] out) {
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tabvisuals-lrclib");
        t.setDaemon(true);
        return t;
    });

    /** Shared empty triple — callers only read it (documented contract of around()). */
    private static final Line[] NO_LINES = new Line[]{null, null, null};

    /** lrclib answers with a small JSON document; anything bigger is garbage. */
    private static final int MAX_BODY_BYTES = 2 << 20;
    /** Minimum gap between real network fetches. */
    private static final long FETCH_THROTTLE_MS = 1500;

    private static volatile Lyrics cache = Lyrics.EMPTY;
    private static volatile String cacheKey = "";
    private static volatile Around aroundCache;

    private static final Object LOCK = new Object();
    /** Latest requested track; replaced (not queued) when tracks flip fast. */
    private static Request pending;
    /** True while a drain loop owns the worker thread. */
    private static boolean draining;
    /** Executor-thread confined: when the last network fetch happened. */
    private static long lastFetchAt;

    private LrcClient() {
    }

    /**
     * Stops the background worker (game exit). Idempotent: repeat calls are
     * no-ops. A fetch in flight is bounded by the 4s/6s HTTP timeouts, so the
     * worker thread dies shortly after the interrupt; clearing pending and
     * draining here guarantees no caller is left waiting for a worker that
     * will never restart (a rejected EXEC.execute already resets draining).
     */
    public static void shutdown() {
        EXEC.shutdownNow();
        synchronized (LOCK) {
            pending = null;
            draining = false;
        }
    }

    public static Lyrics cached() {
        return cache;
    }

    /**
     * Ensures lyrics for the given track are loading; fires {@code onReady}
     * with the result (possibly {@link Lyrics#EMPTY}) once the lookup lands.
     * Rapid track changes coalesce into the newest request instead of being
     * silently dropped by a rate limiter.
     */
    public static void request(String title, String artist, long durationMs, Consumer<Lyrics> onReady) {
        String key = (title + "|" + artist).toLowerCase(Locale.ROOT);
        // cache by key alone: a fetched "no lyrics" result IS Lyrics.EMPTY, and
        // requiring a different identity made every no-lyrics track refetch forever
        if (key.equals(cacheKey)) {
            safeAccept(onReady, cache);
            return;
        }
        boolean startWorker;
        synchronized (LOCK) {
            pending = new Request(key, title, artist, durationMs, onReady);
            startWorker = !draining;
            draining = startWorker;
        }
        if (startWorker) {
            try {
                EXEC.execute(LrcClient::drain);
            } catch (Throwable t) {
                // иначе draining остался бы навсегда true и все будущие
                // запросы зависли бы в pending
                synchronized (LOCK) {
                    draining = false;
                }
            }
        }
    }

    private static void drain() {
        try {
            while (true) {
                Request req;
                synchronized (LOCK) {
                    req = pending;
                    pending = null;
                    if (req == null) return;
                }
                if (req.key().equals(cacheKey)) {
                    safeAccept(req.onReady(), cache);
                    continue;
                }
                // the throttle lives on the worker thread: tracks that flipped
                // faster than the limit collapse into pending instead of losing
                // their lyrics until the next track change
                long wait = FETCH_THROTTLE_MS - (System.currentTimeMillis() - lastFetchAt);
                if (wait > 0) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                synchronized (LOCK) {
                    if (pending != null) continue; // a fresher track arrived — skip this one
                }
                lastFetchAt = System.currentTimeMillis();
                Lyrics result = fetch(req.title(), req.artist(), req.durationMs());
                cacheKey = req.key();
                cache = result;
                safeAccept(req.onReady(), result);
            }
        } finally {
            boolean restart;
            synchronized (LOCK) {
                draining = false;
                restart = pending != null;
                if (restart) draining = true;
            }
            if (restart) {
                try {
                    EXEC.execute(LrcClient::drain);
                } catch (Throwable t) {
                    synchronized (LOCK) {
                        draining = false;
                    }
                }
            }
        }
    }

    private static void safeAccept(Consumer<Lyrics> onReady, Lyrics result) {
        try {
            onReady.accept(result);
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.debug("lyrics listener failed", t);
        }
    }

    /**
     * Returns the triple of lines around {@code posMs}: previous (or null),
     * current (or null), next (or null). The result array is shared and reused
     * between calls while the triple stays the same — read-only for callers.
     */
    public static Line[] around(Lyrics lyrics, long posMs) {
        if (lyrics == null || lyrics.lines().isEmpty()) return NO_LINES;
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
        Around memo = aroundCache;
        if (memo != null && memo.lyrics() == lyrics && memo.prev() == prev
                && memo.cur() == cur && memo.next() == next) {
            return memo.out();
        }
        Line[] out = new Line[]{prev, cur, next};
        aroundCache = new Around(lyrics, prev, cur, next, out);
        return out;
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
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                    if (sb.length() > MAX_BODY_BYTES) return null; // не душим OOM на мусорном ответе
                }
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
    }

    static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null || lrc.isBlank()) return out;
        // [offset:±ms] applies to every line: per the LRC spec a positive
        // offset makes a line appear EARLIER than its timestamp
        long offset = 0;
        String[] rawLines = lrc.split("\r?\n");
        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.regionMatches(true, 0, "[offset:", 0, 8)) {
                int close = line.indexOf(']');
                if (close > 8) {
                    try {
                        offset = Long.parseLong(line.substring(8, close).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        for (String raw : rawLines) {
            String line = raw.trim();
            int i = 0;
            // a line may carry several timestamps: [00:10.00][00:50.00]Chorus
            while (i < line.length() && line.charAt(i) == '[') {
                int close = line.indexOf(']', i);
                if (close < 0) break;
                long ms = parseTag(line.substring(i + 1, close));
                i = close + 1;
                if (ms < 0) continue; // metadata tags ([ar:], [ti:]) and junk
                out.add(new Line(Math.max(0, ms - offset), line.substring(i).trim()));
            }
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return out;
    }

    /** Accepts [mm:ss], [mm:ss.xx] and [mm:ss.xxx] tags; returns -1 otherwise. */
    private static long parseTag(String tag) {
        int colon = tag.indexOf(':');
        if (colon <= 0) return -1;
        try {
            long min = Long.parseLong(tag.substring(0, colon).trim());
            String rest = tag.substring(colon + 1).trim();
            long sec;
            long fracMs = 0;
            int dot = rest.indexOf('.');
            if (dot >= 0) {
                sec = Long.parseLong(rest.substring(0, dot).trim());
                // 1 знак — десятые, 2 — сотые, 3 — миллисекунды; хвост отбрасываем
                String frac = rest.substring(dot + 1).trim();
                if (frac.isEmpty()) return -1;
                long f = Long.parseLong(frac.substring(0, Math.min(3, frac.length())));
                fracMs = frac.length() == 1 ? f * 100 : frac.length() == 2 ? f * 10 : f;
            } else {
                sec = Long.parseLong(rest);
            }
            if (min < 0 || sec < 0 || sec > 59) return -1;
            long ms = min * 60_000 + sec * 1000 + fracMs;
            return ms < 0 ? -1 : ms; // переполнение от сумасшедших значений
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
