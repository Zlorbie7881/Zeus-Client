package com.zlorbie788.zeus.core;

import java.util.ArrayList;
import java.util.List;

public final class Prof {
    private Prof() {}

    public static volatile boolean on;
    private static final java.util.Set<String> HOLDERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void hold(String who, boolean want) {
        boolean changed = want ? HOLDERS.add(who) : HOLDERS.remove(who);
        if (!changed) return;
        boolean was = on;
        on = !HOLDERS.isEmpty();
        if (on && !was) reset();
        else if (!on && was) reset();
    }

    public record Row(Module module, double percent, double msPerFrame) {}

    private static long windowStart;
    private static int frames;
    private static List<Row> report = List.of();
    private static double totalPercent;

    private static boolean costWanted;

    public static void sync(boolean wanted) {
        if (wanted == costWanted) return;
        costWanted = wanted;
        hold("cost", wanted);
    }

    public static void roll() {
        long now = System.nanoTime();
        frames++;
        if (windowStart == 0) {
            windowStart = now;
            return;
        }
        long span = now - windowStart;
        if (span < 1_000_000_000L) return;
        List<Row> rows = new ArrayList<>();
        double total = 0;
        int f = Math.max(1, frames);
        for (Module m : Zeus.all()) {
            long ns = m.profNs;
            m.profNs = 0;
            if (ns <= 0) {
                m.costPct = 0f;
                continue;
            }
            double pct = ns * 100.0 / span;
            m.costPct = (float) pct;
            total += pct;
            rows.add(new Row(m, pct, ns / 1e6 / f));
        }
        rows.sort((a, b) -> Double.compare(b.percent(), a.percent()));
        report = rows;
        totalPercent = total;
        windowStart = now;
        frames = 0;
    }

    public static List<Row> report() {
        return report;
    }

    public static double total() {
        return totalPercent;
    }

    public static void reset() {
        windowStart = 0;
        frames = 0;
        report = List.of();
        totalPercent = 0;
        for (Module m : Zeus.all()) {
            m.profNs = 0;
            m.costPct = 0f;
        }
    }
}
