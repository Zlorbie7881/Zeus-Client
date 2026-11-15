package com.zlorbie788.zeus.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Notify;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The detectability oracle. Other clients guess their numbers and find out they guessed wrong when
 * the account is gone; this one measures instead.
 *
 * <p>Every risky action the client takes is a claim: "I hit from 3.41 blocks", "I snapped 48 degrees
 * in one packet". When the server answers with a push back, a rubber band or a position reset, the
 * claim that was in flight is what it refused, and the envelope shrinks below it. Long quiet
 * stretches let the envelope creep back up, which is the binary search the pitch promised, made out
 * of real traffic instead of deliberately impossible actions. The learned envelope is kept per
 * server and Reach is held inside it, so the sliders stop being guesses and become ceilings the
 * measured evidence already agrees with.
 *
 * <p>The second half is the mirror: how human the last actions looked from the outside, from snap
 * size, click rhythm, strafe pattern and how much the aim varies. It cannot promise anything; it
 * only names the action that stands out the most.
 */
public final class Oracle extends Module {
    /** What Combat.Reach already treats as server-safe, so switching this on never nerfs anyone. */
    private static final double REACH_START = 3.35;
    private static final double REACH_FLOOR = 2.6;
    private static final double REACH_CEILING = 4.2;
    /** No rotation clamp at all until a snap is actually refused. */
    private static final float SNAP_START = 90f;
    private static final float SNAP_FLOOR = 12f;

    private static final long CLAIM_WINDOW_MS = 1500L;
    private static final long QUIET_MS = 60_000L;
    private static final long ROT_WINDOW_MS = 15_000L;
    private static final long CLICK_WINDOW_MS = 15_000L;
    private static final long STRAFE_WINDOW_MS = 10_000L;
    private static final int TRACE_LIMIT = 30_000;

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

    private static Oracle live;

    // ---------------------------------------------------------------- what the server has told us

    private static volatile double safeReach = REACH_START;
    private static volatile float safeSnap = SNAP_START;
    private static volatile int reachEvidence;
    private static volatile int snapEvidence;
    private static volatile int corrections;
    private static volatile long lastCorrectionAt;
    private static volatile String claimKind = "";
    private static volatile double claimValue;
    private static volatile long claimAt;
    private static final Map<String, double[]> SERVERS = new HashMap<>();

    // ---------------------------------------------------------------- what we looked like doing it

    private static final ArrayDeque<float[]> ROTATIONS = new ArrayDeque<>();
    private static final ArrayDeque<Long> CLICKS = new ArrayDeque<>();
    private static final ArrayDeque<double[]> REACHES = new ArrayDeque<>();
    private static final ArrayDeque<Long> STRAFE_FLIPS = new ArrayDeque<>();
    private static boolean strafeSide;
    private static long lastStrafeSample;

    private final Setting.Choice corner = Hud.corner(this, 2);
    private final Setting.Bool learn = add(new Setting.Bool("Learn from corrections",
            "A push back, rubber band or position reset while a hit or a snap was in flight means the server refused that one, so the envelope moves under it and it never sends that again. Off: nothing is learned.",
            true));
    private final Setting.Bool probe = add(new Setting.Bool("Probe the envelope",
            "Step the envelope up by 0.05 blocks after 40 clean hits at the current value and let the server push back again, which is the binary search: each correction narrows it. Off: the envelope only ever learns downwards, which is quieter.",
            false));
    private final Setting.Bool fit = add(new Setting.Bool("Hold modules inside it",
            "Reach and silent rotations are capped at the measured envelope instead of the sliders. Turning this off gives the sliders back unchanged.",
            true));
    private final Setting.Num margin = add(new Setting.Num("Margin",
            "How far below a refused value to sit. The bigger the margin, the more hit rate is traded for safety.",
            0.25, 0.05, 1.0, 0.05, " blocks"));
    private final Setting.Bool mirror = add(new Setting.Bool("Human-likeness model",
            "Score the last actions the way an observer would: snap size, click rhythm, strafe pattern and how much the aim varies. It names what stands out, it does not promise anything.",
            true));
    private final Setting.Bool refuse = add(new Setting.Bool("Refuse impossible hits",
            "Cancel a swing that lands outside the measured envelope, instead of letting the server see the one claim per session that gets an account checked. Off: the swing is only recorded and scored.",
            false));
    private final Setting.Bool auto = add(new Setting.Bool("Start with a server",
            "Switches itself on and loads that server's measured envelope whenever you join multiplayer.",
            true));
    private final Setting.Bool record = add(new Setting.Bool("Record traces",
            "Write every claim and correction to config/zeus/traces, one JSON line each, so a real classifier can be trained from your own accounts later. Nothing leaves the machine.",
            true));
    private final Setting.Info state = addSetting(new Setting.Info("What it has measured", this::lines));

    private int risk;
    private int envelopePts;
    private int snapPts;
    private int mirrorPts;
    private int signaturePts;
    private double peakSnap;
    private double farReach;
    private double human = 0.5;
    private double aimScore = 0.5;
    private double clickScore = 0.5;
    private double strafeScore = 0.5;
    private long computedAt;
    private String worst = "Nothing measured yet";

    private BufferedWriter trace;
    private String traceName = "";
    private int traceLines;
    private long sessionStart;

    public Oracle() {
        super("oracle", "Oracle",
                "Measures what this server actually refuses, keeps every module inside that envelope and scores how human the result looks.",
                Category.PANELS);
        alias("exposure", "risk", "detect", "opsec", "anticheat");
    }

    // ---------------------------------------------------------------- the envelope, for everyone else

    /** Cap an entity reach to what this server is known to accept, plus the margin. */
    public static double limitReach(double slider) {
        Oracle o = live;
        if (o == null || !o.enabled() || !o.fit.on()) return slider;
        return Math.min(slider, safeReach + o.margin.get());
    }

    /** Same for silent rotations: never send a bigger snap than the envelope allows. */
    public static float[] limitRotation(float viewYaw, float viewPitch, float yaw, float pitch) {
        Oracle o = live;
        if (o == null || !o.enabled() || !o.fit.on()) return new float[]{yaw, pitch};
        float limit = safeSnap + 5f;
        float dy = Mth.wrapDegrees(yaw - viewYaw);
        float dp = pitch - viewPitch;
        float mag = Math.max(Math.abs(dy), Math.abs(dp));
        if (mag <= limit || mag < 0.01f) return new float[]{yaw, pitch};
        float k = limit / mag;
        return new float[]{viewYaw + dy * k, Mth.clamp(viewPitch + dp * k, -90f, 90f)};
    }

    /** Called from the attack path before the packet goes out. False means the swing is refused. */
    public static boolean allowAttack(Entity target) {
        Oracle o = live;
        if (o == null || !o.enabled() || target == null) return true;
        double d = com.zlorbie788.zeus.core.Aim.reachTo(target);
        if (!Double.isFinite(d) || d > 12.0) return true;
        claim("hit", d);
        o.record("hit", d);
        if (!o.refuse.on() || !o.fit.on()) return true;
        return d <= safeReach + o.margin.get() + 0.02;
    }

    /** Every swing the client makes, whether or not it lands: the click rhythm half of the mirror. */
    public static void swing(Entity target) {
        Oracle o = live;
        if (o == null || !o.enabled()) return;
        if (target == null) return;
        long now = System.currentTimeMillis();
        synchronized (Oracle.class) {
            CLICKS.addLast(now);
            while (CLICKS.size() > 200) CLICKS.removeFirst();
            trim(now);
        }
        o.record("swing", 0);
    }

    /** Every rotation packet that differs from the view the player sees. */
    public static void rotation(float viewYaw, float viewPitch, float sentYaw, float sentPitch) {
        Oracle o = live;
        if (o == null || !o.enabled()) return;
        float d = Math.abs(Mth.wrapDegrees(sentYaw - viewYaw));
        float p = Math.abs(sentPitch - viewPitch);
        float mag = Math.max(d, p);
        if (!(mag > 0.1f) || Float.isNaN(mag)) return;
        long now = System.currentTimeMillis();
        synchronized (Oracle.class) {
            ROTATIONS.addLast(new float[]{Math.min(180f, mag), now});
            while (ROTATIONS.size() > 200) ROTATIONS.removeFirst();
            trim(now);
        }
        if (mag >= 8f) claim("snap", mag);
    }

    /** A position the server forced on us. Small ones are corrections; big ones are real teleports. */
    public static void correction(Vec3 from, Vec3 to) {
        Oracle o = live;
        if (o == null || !o.enabled() || from == null || to == null) return;
        double d = from.distanceTo(to);
        o.record("setback", d);
        if (d < 0.4 || d > 4.5) return;                 // ordinary smoothing, or a teleport that is not about us
        long now = System.currentTimeMillis();
        corrections++;
        lastCorrectionAt = now;
        if (now - claimAt > CLAIM_WINDOW_MS || claimKind.isEmpty()) return;
        double value = claimValue;
        if ("hit".equals(claimKind) && value > safeReach) {
            safeReach = Math.max(REACH_FLOOR, value - o.margin.get());
            reachEvidence = 0;
            o.record("learn", safeReach);
            Notify.warn("Zeus Oracle: this server refused " + String.format(Locale.ROOT, "%.2f", value)
                    + " blocks, sitting at " + String.format(Locale.ROOT, "%.2f", safeReach));
        } else if ("snap".equals(claimKind) && value > safeSnap) {
            safeSnap = (float) Math.max(SNAP_FLOOR, value - 5f);
            snapEvidence = 0;
            o.record("learn", safeSnap);
            Notify.warn("Zeus Oracle: this server refused a " + Math.round(value) + " degree snap, capping at "
                    + Math.round(safeSnap));
        }
    }

    private static void claim(String kind, double value) {
        claimKind = kind;
        claimValue = value;
        claimAt = System.currentTimeMillis();
        if ("hit".equals(kind)) {
            long now = claimAt;
            synchronized (Oracle.class) {
                REACHES.addLast(new double[]{value, now});
                while (REACHES.size() > 300) REACHES.removeFirst();
                while (!REACHES.isEmpty() && now - REACHES.peekFirst()[1] > 30_000L) REACHES.removeFirst();
            }
        }
        Oracle o = live;
        if (o == null || !o.learn.on() || !o.probe.on()) return;
        long now = System.currentTimeMillis();
        if (now - lastCorrectionAt < QUIET_MS) return;
        if ("hit".equals(kind)) {
            if (value > safeReach || ++reachEvidence < 40) return;
            reachEvidence = 0;
            safeReach = Math.min(REACH_CEILING, safeReach + 0.05);
        } else if ("snap".equals(kind)) {
            if (value > safeSnap || ++snapEvidence < 40) return;
            snapEvidence = 0;
            safeSnap = Math.min(SNAP_START, safeSnap + 2f);
        }
    }

    // ---------------------------------------------------------------- the mirror

    private static synchronized void trim(long now) {
        while (!ROTATIONS.isEmpty() && now - ROTATIONS.peekFirst()[1] > ROT_WINDOW_MS) ROTATIONS.removeFirst();
        while (!CLICKS.isEmpty() && now - CLICKS.peekFirst() > CLICK_WINDOW_MS) CLICKS.removeFirst();
        while (!STRAFE_FLIPS.isEmpty() && now - STRAFE_FLIPS.peekFirst() > STRAFE_WINDOW_MS) STRAFE_FLIPS.removeFirst();
    }

    private void compute() {
        long now = System.currentTimeMillis();
        int flips;
        double peak = 0;
        double spreadRot = 0;
        int rotCount;
        List<Double> gaps = new ArrayList<>();
        double far = 0;
        synchronized (Oracle.class) {
            trim(now);
            List<Double> mags = new ArrayList<>();
            for (float[] r : ROTATIONS) {
                peak = Math.max(peak, r[0]);
                mags.add((double) r[0]);
            }
            rotCount = mags.size();
            if (rotCount > 0) {
                double sum = 0;
                for (double v : mags) sum += v;
                double meanRot = sum / rotCount;
                double var = 0;
                for (double v : mags) var += (v - meanRot) * (v - meanRot);
                spreadRot = Math.sqrt(var / rotCount);
            }
            long prev = 0;
            for (long t : CLICKS) {
                if (prev != 0) gaps.add((double) (t - prev));
                prev = t;
            }
            for (double[] r : REACHES) far = Math.max(far, r[0]);
            flips = STRAFE_FLIPS.size();
        }

        peakSnap = peak;
        farReach = far;

        // Snap size: a human flick tops out around 40-70 degrees in a tick; the clamps are the tell.
        double big = clamp01((peak - 40.0) / 80.0);
        double flatAim = rotCount > 20 && spreadRot < 0.8 ? 0.35 : 0.0;
        aimScore = clamp01(1.0 - big - flatAim);

        // Click rhythm: a human has a coefficient of variation around 0.2-0.5; a bot is close to zero.
        clickScore = 0.5;
        double meanGap = 0;
        double gapSpread = 0;
        if (gaps.size() >= 8) {
            double sum = 0;
            for (double g : gaps) sum += g;
            meanGap = gaps.isEmpty() ? 0 : sum / gaps.size();
            double var = 0;
            for (double g : gaps) var += (g - meanGap) * (g - meanGap);
            gapSpread = gaps.isEmpty() ? 0 : Math.sqrt(var / gaps.size());
            if (meanGap > 1) {
                double cv = gapSpread / meanGap;
                double cps = 1000.0 / meanGap;
                clickScore = clamp01(cv / 0.22);
                if (cps > 16) clickScore *= 0.5;
            }
        }

        // Strafing: a few direction changes a minute is human, none is a script, constant flipping is worse.
        if (flips <= 0) strafeScore = 0.45;
        else if (flips < 14) strafeScore = 1.0;
        else if (flips < 30) strafeScore = 0.7;
        else strafeScore = 0.4;

        human = clamp01(aimScore * 0.45 + clickScore * 0.35 + strafeScore * 0.20);

        double overReach = Math.max(0.0, farReach - safeReach);
        double overSnap = Math.max(0.0, peak - safeSnap);
        envelopePts = (int) Math.round(clamp01(overReach / 1.0) * 35);
        snapPts = (int) Math.round(clamp01(overSnap / 60.0) * 30);
        mirrorPts = (int) Math.round((1.0 - human) * 35);

        signaturePts = 0;
        String topName = null;
        int topWeight = 0;
        for (Map.Entry<String, Integer> e : SIGNATURES.entrySet()) {
            Module m = Zeus.get(e.getKey());
            if (m == null || !m.enabled()) continue;
            signaturePts += e.getValue();
            if (e.getValue() > topWeight) {
                topWeight = e.getValue();
                topName = m.name;
            }
        }
        signaturePts = Math.min(20, signaturePts);

        int best = Math.max(Math.max(envelopePts, snapPts), Math.max(mirrorPts, signaturePts));
        if (corrections == 0 && best == 0) worst = "Nothing measured yet";
        else if (best == envelopePts) worst = String.format(Locale.ROOT, "Reach %.2f, envelope %.2f", farReach, safeReach);
        else if (best == snapPts) worst = "Snap " + Math.round(peak) + "\\u00B0 in one packet";
        else if (best == mirrorPts) worst = "Clicks every " + Math.round(meanGap) + " ms, aim varies very little";
        else worst = (topName == null ? "A signature module" : topName) + " is on";

        risk = Math.max(0, Math.min(100, envelopePts + snapPts + mirrorPts + signaturePts));
        computedAt = now;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    private int confidence() {
        if (corrections >= 8) return 96;
        if (corrections >= 4) return 88;
        if (corrections >= 1) return 72;
        return 35;
    }

    private int colour() {
        return risk < 15 ? Accent.OK : risk < 35 ? Accent.WARN : risk < 60 ? Accent.HIGH : Accent.BAD;
    }

    // ---------------------------------------------------------------- traces

    private void openTrace() {
        if (trace != null) return;
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("zeus/traces");
            Files.createDirectories(dir);
            String server = serverKey().replaceAll("[^a-zA-Z0-9._-]", "_");
            if (server.length() > 40) server = server.substring(0, 40);
            traceName = "oracle-" + server + "-"
                    + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".jsonl";
            trace = Files.newBufferedWriter(dir.resolve(traceName), StandardCharsets.UTF_8);
            traceLines = 0;
            sessionStart = System.currentTimeMillis();
        } catch (Exception e) {
            trace = null;
            traceName = "could not open (" + e.getClass().getSimpleName() + ")";
        }
    }

    /** One line per event, flushed every 20 lines, capped so a long session cannot fill a disk. */
    private void record(String kind, double value) {
        if (!record.on()) return;
        if (trace == null) openTrace();
        if (trace == null || traceLines > TRACE_LIMIT) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            double x = mc.player == null ? 0 : mc.player.getX();
            double y = mc.player == null ? 0 : mc.player.getY();
            double z = mc.player == null ? 0 : mc.player.getZ();
            trace.write(String.format(Locale.ROOT,
                    "{\"t\":%d,\"k\":\"%s\",\"v\":%.4f,\"safe\":%.4f,\"snap\":%.2f,\"x\":%.3f,\"y\":%.3f,\"z\":%.3f}%n",
                    System.currentTimeMillis() - sessionStart, kind, value, safeReach, safeSnap, x, y, z));
            traceLines++;
            if (traceLines % 20 == 0) trace.flush();
        } catch (Exception e) {
            closeTrace();
        }
    }

    private void closeTrace() {
        Writer w = trace;
        trace = null;
        if (w == null) return;
        try {
            w.flush();
            w.close();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- per server

    private static String serverKey() {
        Minecraft mc = Minecraft.getInstance();
        var data = mc.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) return data.ip;
        return data != null ? "server" : "local";
    }

    private static Path statsFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("zeus-oracle.json");
    }

    private static void loadServerStats() {
        SERVERS.clear();
        Path f = statsFile();
        if (!Files.exists(f)) return;
        try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonObject servers = root.getAsJsonObject("servers");
            if (servers == null) return;
            for (Map.Entry<String, JsonElement> e : servers.entrySet()) {
                JsonObject s = e.getValue().getAsJsonObject();
                SERVERS.put(e.getKey(), new double[]{
                        s.has("reach") ? s.get("reach").getAsDouble() : REACH_START,
                        s.has("snap") ? s.get("snap").getAsDouble() : SNAP_START,
                        s.has("corrections") ? s.get("corrections").getAsInt() : 0});
            }
        } catch (Exception ignored) {
        }
    }

    private static void saveServerStats() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        SERVERS.put(serverKey(), new double[]{safeReach, safeSnap, corrections});
        JsonObject root = new JsonObject();
        JsonObject servers = new JsonObject();
        for (Map.Entry<String, double[]> e : SERVERS.entrySet()) {
            JsonObject s = new JsonObject();
            s.addProperty("reach", e.getValue()[0]);
            s.addProperty("snap", e.getValue()[1]);
            s.addProperty("corrections", e.getValue()[2]);
            servers.add(e.getKey(), s);
        }
        root.add("servers", servers);
        try {
            Files.createDirectories(statsFile().getParent());
            try (Writer w = Files.newBufferedWriter(statsFile(), StandardCharsets.UTF_8)) {
                w.write(root.toString());
            }
        } catch (Exception ignored) {
        }
    }

    public static void onWorldEntered() {
        Oracle o = Zeus.get(Oracle.class);
        if (o == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() == null) return;
        synchronized (Oracle.class) {
            ROTATIONS.clear();
            CLICKS.clear();
            STRAFE_FLIPS.clear();
            REACHES.clear();
        }
        loadServerStats();
        double[] known = SERVERS.get(serverKey());
        if (known != null) {
            safeReach = Math.max(REACH_FLOOR, known[0]);
            safeSnap = (float) Math.max(SNAP_FLOOR, known[1]);
            corrections = (int) known[2];
        } else {
            safeReach = REACH_START;
            safeSnap = SNAP_START;
            corrections = 0;
        }
        o.closeTrace();
        o.computedAt = 0;
        if (o.auto.on() && !o.enabled()) o.setEnabled(true);
    }

    public static void onWorldLeft() {
        Oracle o = live;
        if (o == null) return;
        saveServerStats();
        o.closeTrace();
    }

    // ---------------------------------------------------------------- module

    @Override
    protected void onEnable() {
        live = this;
        synchronized (Oracle.class) {
            ROTATIONS.clear();
            CLICKS.clear();
            STRAFE_FLIPS.clear();
            REACHES.clear();
        }
        computedAt = 0;
        if (record.on()) openTrace();
    }

    @Override
    protected void onDisable() {
        saveServerStats();
        closeTrace();
        live = null;
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastStrafeSample > 100L) {
            lastStrafeSample = now;
            boolean left = mc.options.keyLeft.isDown();
            boolean right = mc.options.keyRight.isDown();
            if (left != right) {
                boolean side = left;
                if (side != strafeSide) {
                    strafeSide = side;
                    synchronized (Oracle.class) {
                        STRAFE_FLIPS.addLast(now);
                        trim(now);
                    }
                }
            }
        }
        if (now - computedAt > 400L) compute();
    }

    private List<String> lines() {
        compute();
        Minecraft mc = Minecraft.getInstance();
        return List.of(
                "Reach      " + String.format(Locale.ROOT, "%.2f", farReach) + " used, "
                        + String.format(Locale.ROOT, "%.2f", safeReach) + " is what this server accepts",
                "Rotation   " + Math.round(peakSnap) + "\\u00B0 biggest snap, "
                        + Math.round(safeSnap) + "\\u00B0 is the cap",
                "Evidence   " + corrections + " correction" + (corrections == 1 ? "" : "s")
                        + ", confidence " + confidence() + "%",
                "Mirror     aim " + Math.round(aimScore * 100) + "%, clicks " + Math.round(clickScore * 100)
                        + "%, strafe " + Math.round(strafeScore * 100) + "%",
                "Sitting at " + (fit.on() ? "the envelope (sliders are ceilings)" : "the raw sliders"),
                record.on() ? "Traces     config/zeus/traces/" + (traceName.isEmpty() ? "(not started)" : traceName)
                        + " (" + traceLines + " lines)" : "Traces     off",
                "Stream     " + com.zlorbie788.zeus.core.Stealth.summary(),
                mc.getCurrentServer() == null ? "Singleplayer, nothing to measure" : "Measured for " + serverKey());
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (System.currentTimeMillis() - computedAt > 400) compute();
        int col = colour();
        int bw = Math.max(150, Hud.captionWidth("Oracle") + Hud.chipWidth(Integer.toString(risk)) + 24);
        int bh = Hud.TOP + 23;
        int[] at = Hud.place(corner.index(), bw, bh, w, h);
        Hud.panel(g, at[0], at[1], bw, bh);
        Hud.header(g, at[0], at[1], bw, "Oracle", Integer.toString(risk), col);
        int x = at[0] + Hud.PAD;
        int rw = bw - Hud.PAD * 2;
        int y = at[1] + Hud.TOP;
        Hud.bar(g, x, y, rw, 3, risk / 100f, col);
        if (mirror.on()) {
            Draw.text(g, Draw.fit(worst, rw), x, y + 6, 0xFF8794AC);
        }
        String env = String.format(Locale.ROOT, "%.2f/%.2f reach \\u00B7 %.0f\\u00B0 snap \\u00B7 %d%% human",
                farReach, safeReach, peakSnap, Math.round(human * 100));
        Draw.text(g, Draw.fit(env, rw), x, y + 14, 0xFF5E7094);
    }

    @Override
    public String info() {
        if (System.currentTimeMillis() - computedAt > 400) compute();
        return risk + " " + confidence() + "%";
    }
}
