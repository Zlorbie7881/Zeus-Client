package com.zlorbie788.zeus.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures what every module costs. Timing only runs while the Performance Monitor is on (a flag check otherwise), so it
 * adds nothing for everyone else. Every module runs on the game thread, so the share of one second it used is the share of
 * your frame rate it takes.
 */
public final class Prof {
    private Prof() {}

    /** True while somebody is looking at the numbers. */
    public static volatile boolean on;
    private static final java.util.Set<String> HOLDERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Asks for the measuring to run (or stops asking). The timing only runs while at least one holder wants it. */
    public static void hold(String who, boolean want) {
        boolean changed = want ? HOLDERS.add(who) : HOLDERS.remove(who);
        if (!changed) return;
        boolean was = on;
        on = !HOLDERS.isEmpty();
        if (on && !was) reset();
        else if (!on && was) reset();
    }

    /** One row of the report. */
    public record Row(Module module, double percent, double msPerFrame) {}

    private static long windowStart;
    private static int frames;
    private static List<Row> report = List.of();
    private static double totalPercent;

    private static boolean costWanted;

    /** Keeps the measuring in step with the "Show what each module costs" setting. */
    public static void sync(boolean wanted) {
        if (wanted == costWanted) return;
        costWanted = wanted;
        hold("cost", wanted);
    }

    /** Called once a frame by the monitor: rolls the window over once a second. */
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

    /** What all Zeus modules together used of the last second, in percent of the game thread. */
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
