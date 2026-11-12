package com.zlorbie788.zeus.core;

import com.zlorbie788.zeus.ui.Accent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Notify {
    private Notify() {}

    public enum Kind {
        INFO(0xFF6FA8FF, 2200L, false),
        GOOD(Accent.OK, 2200L, true),
        WARN(Accent.WARN, 3000L, false),
        BAD(0xFFFF6B6B, 3200L, false);

        public final int color;
        public final long life;
        public final boolean good;

        Kind(int color, long life, boolean good) {
            this.color = color;
            this.life = life;
            this.good = good;
        }
    }

    public record Note(String text, Kind kind, long born, int repeats) {
        long expires() {
            return born + kind.life;
        }

        public float fade(long now) {
            long left = expires() - now;
            if (left <= 0L) return 0f;
            if (still()) return 1f;
            float rise = Math.min(1f, Math.max(0f, (now - born) / 130f));
            float fall = Math.min(1f, left / 360f);
            return Math.max(0f, Math.min(rise, fall));
        }

        public float slide(long now) {
            if (still()) return 0f;
            float rise = Math.min(1f, Math.max(0f, (now - born) / 170f));
            return (1f - rise) * (1f - rise) * -9f;
        }

        private static boolean still() {
            return com.zlorbie788.zeus.ui.UiSettings.REDUCE_MOTION.on();
        }

        public String label() {
            return repeats > 1 ? text + " x" + repeats : text;
        }
    }

    private static final int MAX = 5;
    private static final long COALESCE_MS = 1200L;

    private static final CopyOnWriteArrayList<Note> LIVE = new CopyOnWriteArrayList<>();

    public static void push(String text, Kind kind) {
        if (text == null || text.isEmpty() || kind == null) return;
        long now = System.currentTimeMillis();
        LIVE.removeIf(n -> n.expires() <= now);
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            Note existing = LIVE.get(i);
            if (!existing.text.equals(text)) continue;
            if (now - existing.born > COALESCE_MS) break;
            LIVE.set(i, new Note(text, kind, now, Math.min(9, existing.repeats + 1)));
            return;
        }
        LIVE.add(new Note(text, kind, now, 1));
        while (LIVE.size() > MAX) LIVE.remove(0);
    }

    public static void info(String text) {
        push(text, Kind.INFO);
    }

    public static void good(String text) {
        push(text, Kind.GOOD);
    }

    public static void warn(String text) {
        push(text, Kind.WARN);
    }

    public static void bad(String text) {
        push(text, Kind.BAD);
    }

    public static List<Note> live(long now) {
        LIVE.removeIf(n -> n.expires() <= now);
        return LIVE;
    }

    public static void clear() {
        LIVE.clear();
    }

    public static int count() {
        return LIVE.size();
    }
}
