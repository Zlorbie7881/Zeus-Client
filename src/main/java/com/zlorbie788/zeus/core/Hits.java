package com.zlorbie788.zeus.core;

import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Hits {
    private Hits() {}

    private static final long WINDOW_MS = 1200L;

    private static final long RECENT_MS = 8000L;

    private static final int COLD_MISSES = 4;

    private static final long COLD_MS = 2600L;
    private static final int MAX_LOG = 96;
    private static final int MAX_TRACKED = 256;

    private static final class Swing {
        final int id;
        final long at;
        boolean landed;
        long latency;

        Swing(int id, long at) {
            this.id = id;
            this.at = at;
        }
    }

    private static final class Rec {
        int swings;
        int landed;
        int streak;
        long lastSwing;
        long lastLand;
        double latency;
    }

    private static final Map<Integer, Rec> REC = new ConcurrentHashMap<>();
    private static final ArrayDeque<Swing> LOG = new ArrayDeque<>();

    public static boolean fighting(long ms) {
        long now = System.currentTimeMillis();
        synchronized (LOG) {
            for (Swing s : LOG) {
                if (now - s.at <= ms) return true;
            }
        }
        for (Rec r : REC.values()) {
            if (r.lastLand > 0 && now - r.lastLand <= ms) return true;
        }
        return false;
    }

    public static void attempt(Entity e) {
        if (e == null) return;
        com.zlorbie788.zeus.mods.Exposure.swing(e);
        attempt(e.getId());
    }

    public static void attempt(int id) {
        if (id < 0) return;
        long now = System.currentTimeMillis();
        Rec r = REC.computeIfAbsent(id, k -> new Rec());
        r.swings++;
        r.streak++;
        r.lastSwing = now;
        Swing s = new Swing(id, now);
        synchronized (LOG) {
            LOG.addLast(s);
            while (LOG.size() > MAX_LOG) LOG.removeFirst();
        }
        purge(now);
    }

    public static void confirm(int id) {
        long now = System.currentTimeMillis();
        Rec r = REC.get(id);
        if (r == null) return;
        Swing hit = null;
        synchronized (LOG) {
            Iterator<Swing> it = LOG.descendingIterator();
            while (it.hasNext()) {
                Swing s = it.next();
                if (s.id != id || s.landed) continue;
                if (now - s.at > WINDOW_MS) break;
                hit = s;
                break;
            }
            if (hit != null) {
                hit.landed = true;
                hit.latency = now - hit.at;
            }
        }
        if (hit == null) return;
        r.landed++;
        r.streak = 0;
        r.lastLand = now;
        r.latency = r.latency <= 0 ? hit.latency : r.latency * 0.7 + hit.latency * 0.3;
    }

    public static boolean cold(int id) {
        Rec r = REC.get(id);
        return r != null && r.streak >= COLD_MISSES && System.currentTimeMillis() - r.lastSwing < COLD_MS;
    }

    public static int swings(int id) {
        Rec r = REC.get(id);
        return r == null ? 0 : r.swings;
    }

    public static int landed(int id) {
        Rec r = REC.get(id);
        return r == null ? 0 : r.landed;
    }

    public static float ratio(int id) {
        Rec r = REC.get(id);
        if (r == null || r.swings == 0) return 1f;
        return Math.min(1f, r.landed / (float) r.swings);
    }

    public static double latencyMs(int id) {
        Rec r = REC.get(id);
        return r == null ? 0 : r.latency;
    }

    public static int recentSwings() {
        long now = System.currentTimeMillis();
        int n = 0;
        synchronized (LOG) {
            for (Iterator<Swing> it = LOG.descendingIterator(); it.hasNext(); ) {
                if (now - it.next().at > RECENT_MS) break;
                n++;
            }
        }
        return n;
    }

    public static int recentLanded() {
        long now = System.currentTimeMillis();
        int n = 0;
        synchronized (LOG) {
            for (Iterator<Swing> it = LOG.descendingIterator(); it.hasNext(); ) {
                Swing s = it.next();
                if (now - s.at > RECENT_MS) break;
                if (s.landed) n++;
            }
        }
        return n;
    }

    public static float recentRatio() {
        int n = recentSwings();
        return n == 0 ? 1f : Math.min(1f, recentLanded() / (float) n);
    }

    public static double recentLatency() {
        long now = System.currentTimeMillis();
        double sum = 0;
        int n = 0;
        synchronized (LOG) {
            for (Iterator<Swing> it = LOG.descendingIterator(); it.hasNext(); ) {
                Swing s = it.next();
                if (now - s.at > RECENT_MS) break;
                if (s.landed) {
                    sum += s.latency;
                    n++;
                }
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    public static String text() {
        int n = recentSwings();
        if (n == 0) return "";
        int ok = recentLanded();
        StringBuilder sb = new StringBuilder();
        sb.append(ok).append('/').append(n);
        double ms = recentLatency();
        if (ms > 0) sb.append(' ').append(Math.round(ms)).append("ms");
        return sb.toString();
    }

    public static void reset() {
        REC.clear();
        synchronized (LOG) {
            LOG.clear();
        }
    }

    private static long purgedAt;

    private static void purge(long now) {
        if (now - purgedAt < 5000L) return;
        purgedAt = now;
        REC.entrySet().removeIf(e -> now - e.getValue().lastSwing > 30000L);
        if (REC.size() > MAX_TRACKED) {
            REC.entrySet().removeIf(e -> now - e.getValue().lastSwing > 6000L);
        }
    }
}
