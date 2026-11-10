package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How bannable you are right now, as one number.
 *
 * <p>Nothing here guesses what a specific anti-cheat does. It measures the four things that are true of every check that
 * has ever caught a client user: rotations that no wrist could produce, reach past what vanilla allows, clicks that keep
 * time like a metronome, and modules that announce themselves by name (click teleport, flight, scaffold). Those are
 * observable from the outside, and they are what a staff member or an automated check actually reacts to.</p>
 *
 * <p>The score is a model, not a verdict: 0 is "plays like a person", 100 is "banned on sight". It reacts within seconds,
 * so you can change something and watch the number move.</p>
 */
public final class Exposure extends Module {
    private static final long ROT_WINDOW_MS = 15_000L;
    private static final long SWING_WINDOW_MS = 30_000L;
    private static final long CLICK_WINDOW_MS = 15_000L;

    // ---- what the rest of the client reports to us
    private static final ArrayDeque<float[]> ROTATIONS = new ArrayDeque<>();
    private static final ArrayDeque<double[]> SWINGS = new ArrayDeque<>();
    private static final ArrayDeque<Long> CLICKS = new ArrayDeque<>();

    /** Big turns start to look like a machine above this many degrees in a single packet. */
    private static final float ROT_FREE = 6f;
    /** Vanilla reach is three blocks; the risk climbs from there to about 4.2. */
    private static final double REACH_FREE = 3.0;

    /** The modules whose mere presence is a signature, and how much each adds. */
    private static final Map<String, Integer> SIGNATURES = Map.ofEntries(
            Map.entry("clicktp", 30), Map.entry("flight", 16), Map.entry("boatnoclip", 16),
            Map.entry("boatfly", 14), Map.entry("longjump", 14), Map.entry("highjump", 12),
            Map.entry("scaffold", 14), Map.entry("reach", 14), Map.entry("killaura", 12),
            Map.entry("multiaura", 12), Map.entry("spider", 12), Map.entry("speed", 10),
            Map.entry("nofall", 9), Map.entry("autoweb", 8), Map.entry("jesus", 8),
            Map.entry("triggerbot", 8), Map.entry("aimassist", 7), Map.entry("bowaimbot", 7),
            Map.entry("crystalaura", 7), Map.entry("anchoraura", 7), Map.entry("maceaura", 6),
            Map.entry("autoclicker", 6), Map.entry("nuker", 6), Map.entry("fakelag", 5),
            Map.entry("surround", 4), Map.entry("fastplace", 4), Map.entry("fastbreak", 4),
            Map.entry("antiknockback", 4), Map.entry("autototem", 3), Map.entry("criticals", 3));

    private final Setting.Choice corner = Hud.corner(this, 2);
    private final Setting.Bool reason = add(new Setting.Bool("Say why", "One line under the bar naming what is scoring highest.", true));
    private final Setting.Bool auto = add(new Setting.Bool("Start with a server", "Switches itself on whenever you join a multiplayer server.", true));
    private final Setting.Info detail = add(new Setting.Info("What is counted", this::lines));

    private int score;
    private int rotPts;
    private int reachPts;
    private int clickPts;
    private int cfgPts;
    private String worst = "";
    private long computedAt;

    public Exposure() {
        super("exposure", "Exposure", "How detectable you look right now: rotations, reach, click rhythm and the modules that sign your name.", Category.PANELS);
        alias("risk", "detect", "opsec");
    }

    // ------------------------------------------------------------------ what gets measured

    /**
     * A silent rotation is about to be sent at these angles while your view stays where it is. The gap between the two
     * is what a server sees as an impossible snap, and it is the strongest single signal a client gives off.
     */
    public static void rotation(float viewYaw, float viewPitch, float sentYaw, float sentPitch) {
        float d = Math.abs(net.minecraft.util.Mth.wrapDegrees(sentYaw - viewYaw));
        float p = Math.abs(sentPitch - viewPitch);
        note(Math.max(d, p));
    }

    private static synchronized void note(float degrees) {
        if (!(degrees > 0f) || Float.isNaN(degrees)) return;
        long now = System.currentTimeMillis();
        ROTATIONS.addLast(new float[]{Math.min(180f, degrees), now});
        while (ROTATIONS.size() > 200) ROTATIONS.removeFirst();
        trim(now);
    }

    /** A swing that was sent at this entity, with how far away it was. */
    public static void swing(Entity target) {
        if (target == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = Math.sqrt(mc.player.getEyePosition().distanceToSqr(target.getBoundingBox().getCenter()));
        long now = System.currentTimeMillis();
        synchronized (Exposure.class) {
            SWINGS.addLast(new double[]{now, Math.min(10.0, d)});
            while (SWINGS.size() > 200) SWINGS.removeFirst();
            CLICKS.addLast(now);
            while (CLICKS.size() > 200) CLICKS.removeFirst();
        }
        trim(now);
    }

    private static synchronized void trim(long now) {
        while (!ROTATIONS.isEmpty() && now - ROTATIONS.peekFirst()[1] > ROT_WINDOW_MS) ROTATIONS.removeFirst();
        while (!SWINGS.isEmpty() && now - SWINGS.peekFirst()[0] > SWING_WINDOW_MS) SWINGS.removeFirst();
        while (!CLICKS.isEmpty() && now - CLICKS.peekFirst() > CLICK_WINDOW_MS) CLICKS.removeFirst();
    }

    // ------------------------------------------------------------------ scoring

    private void compute() {
        long now = System.currentTimeMillis();
        float peak = 0f;
        double far = 0;
        double mean = 0;
        double spread = 0;
        int clicks;
        synchronized (Exposure.class) {
            trim(now);
            for (float[] r : ROTATIONS) peak = Math.max(peak, r[0]);
            for (double[] s : SWINGS) far = Math.max(far, s[1]);
            clicks = CLICKS.size();
            if (clicks >= 6) {
                long prev = 0;
                List<Double> gaps = new ArrayList<>(clicks);
                for (long t : CLICKS) {
                    if (prev != 0) gaps.add((double) (t - prev));
                    prev = t;
                }
                double sum = 0;
                for (double g : gaps) sum += g;
                mean = gaps.isEmpty() ? 0 : sum / gaps.size();
                double var = 0;
                for (double g : gaps) var += (g - mean) * (g - mean);
                spread = gaps.isEmpty() ? 0 : Math.sqrt(var / gaps.size());
            }
        }

        double rot = peak <= ROT_FREE ? 0 : Math.min(1, (peak - ROT_FREE) / 66.0);
        rotPts = (int) Math.round(Math.pow(rot, 0.75) * 20);

        double over = far <= REACH_FREE ? 0 : Math.min(1, (far - REACH_FREE) / 1.2);
        reachPts = (int) Math.round(Math.pow(over, 0.8) * 20);

        clickPts = 0;
        if (clicks >= 10 && mean > 1) {
            double cps = 1000.0 / mean;
            double regularity = Math.max(0, Math.min(1, 1 - spread / (0.30 * mean)));
            double intensity = Math.max(0, Math.min(1, (cps - 6) / 6));
            clickPts = (int) Math.round(regularity * intensity * 20);
        }

        cfgPts = 0;
        String topName = null;
        int topWeight = 0;
        for (Map.Entry<String, Integer> e : SIGNATURES.entrySet()) {
            Module m = Zeus.get(e.getKey());
            if (m == null || !m.enabled()) continue;
            cfgPts += e.getValue();
            if (e.getValue() > topWeight) {
                topWeight = e.getValue();
                topName = m.name;
            }
        }
        cfgPts = Math.min(40, cfgPts);

        int best = Math.max(Math.max(rotPts, reachPts), Math.max(clickPts, cfgPts));
        if (best == 0) worst = "Nothing here stands out";
        else if (best == rotPts) worst = "Rotation " + Math.round(peak) + "\u00B0 in one packet";
        else if (best == reachPts) worst = "Reach " + String.format(Locale.ROOT, "%.1f", far) + " blocks";
        else if (best == clickPts) worst = "Clicks every " + Math.round(mean) + " ms, no variation";
        else worst = (topName == null ? "A signature module" : topName) + " is on";

        score = Math.max(0, Math.min(100, rotPts + reachPts + clickPts + cfgPts));
        computedAt = now;
    }

    private List<String> lines() {
        compute();
        return List.of(
                "Rotation  " + rotPts + "/20   the biggest silent snap in the last 15 s",
                "Reach     " + reachPts + "/20   the longest swing in the last 30 s",
                "Rhythm    " + clickPts + "/20   how regular the clicks are above 6 cps",
                "Modules   " + cfgPts + "/40   how many signature modules are on",
                "A low number is not a promise: it only says nothing here is obvious.");
    }

    private int colour() {
        return score < 15 ? 0xFF5EE6A0 : score < 35 ? 0xFFFFC857 : score < 60 ? 0xFFFF8F4D : 0xFFFF5C6C;
    }

    // ------------------------------------------------------------------ the card

    @Override
    protected void onTick(Minecraft mc) {
        if (System.currentTimeMillis() - computedAt > 400) compute();
    }

    public static void onWorldEntered() {
        Exposure e = Zeus.get(Exposure.class);
        if (e == null || !e.auto.on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() == null) return;
        synchronized (Exposure.class) {
            ROTATIONS.clear();
            SWINGS.clear();
            CLICKS.clear();
        }
        if (!e.enabled()) e.setEnabled(true);
    }

    @Override
    protected void onEnable() {
        synchronized (Exposure.class) {
            ROTATIONS.clear();
            SWINGS.clear();
            CLICKS.clear();
        }
        computedAt = 0;
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (System.currentTimeMillis() - computedAt > 400) compute();
        int col = colour();
        int bw = Math.max(126, Hud.captionWidth("Exposure") + Hud.chipWidth("100") + 24);
        int bh = Hud.TOP + (reason.on() ? 18 : 6);
        int[] at = Hud.place(corner.index(), bw, bh, w, h);
        Hud.panel(g, at[0], at[1], bw, bh);
        Hud.header(g, at[0], at[1], bw, "Exposure", Integer.toString(score), col);
        int x = at[0] + Hud.PAD;
        int rw = bw - Hud.PAD * 2;
        int y = at[1] + Hud.TOP;
        Hud.bar(g, x, y, rw, 3, score / 100f, col);
        if (reason.on()) Draw.text(g, Draw.fit(worst, rw), x, y + 6, 0xFF8794AC);
    }

    @Override
    public String info() {
        if (System.currentTimeMillis() - computedAt > 400) compute();
        return Integer.toString(score);
    }
}
