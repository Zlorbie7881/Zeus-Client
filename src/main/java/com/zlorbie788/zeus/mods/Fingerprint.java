package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * The fingerprint half of a ghost client: what the server's rotation checks can actually see.
 *
 * <p>A real mouse moves in whole pixels, and the game turns a fixed number of degrees per pixel, so
 * every angle a normal player produces sits on a grid of {@code (sens * 0.6 + 0.2)^3 * 1.2} degrees.
 * Aim already rounds onto that grid, because angles between the steps are the cheapest thing a check
 * can spot; this adds the parts the grid alone does not cover, all measured against what public
 * anti-cheats actually test:
 *
 * <ul>
 *   <li>never change the look by less than one mouse step while visibly moving, which is Vulcan's
 *       "too small yaw change" and Grim's smallest-divisor noise filter,</li>
 *   <li>whole-step hand jitter so consecutive deltas are not identical, which is Vulcan's
 *       "not constant rotations" and Grim's duplicate-look check,</li>
 *   <li>hold a silent flick for a tick or two and then walk it back in mouse steps instead of
 *       snapping the view home in one packet, which is what aim checks look for after a hit,</li>
 *   <li>never send the same rotation twice in a row, which is Vulcan's "repeated yaw values".</li>
 * </ul>
 *
 * <p>The audit is the honest half: it runs those same tests on our own outgoing stream and names the
 * one that would flag, so the numbers can be checked in game instead of trusted.
 */
public final class Fingerprint extends Module {
    private static Fingerprint live;

    private static float lastSentYaw = Float.NaN;
    private static float lastSentPitch;
    private static double lastDeltaYaw;

    private static int samples;
    private static int offGrid;
    private static int repeats;
    private static int silentTicks;
    private static int streak;
    private static int worstStreak;
    private static double maxSnap;
    private static double clean = 100;

    private final Setting.Bool grid = add(new Setting.Bool("Sensitivity grid",
            "Every sent angle is a whole mouse step of your sensitivity, which is the only thing a real mouse can produce. This is the GCD check Grim, Intave and Vulcan all run first.",
            true));
    private final Setting.Bool hand = add(new Setting.Bool("Hand jitter",
            "Adds whole mouse steps of noise to the aim, so two swings at the same target do not send identical deltas. Whole steps only: jitter that lands between the grid is worse than none.",
            true));
    private final Setting.Num jitter = add(new Setting.Num("How shaky",
            "Noise in mouse steps. 1 is a steady hand, 2 is a fight.",
            1, 0, 3, 1, " steps")).showIf(() -> this.hand.on());
    private final Setting.Num hold = add(new Setting.Num("Hold the flick",
            "How many ticks the silent rotation is kept after a hit before anything is done about it. A person keeps looking at what they hit; the instant snap home is the tell.",
            2, 0, 4, 1, " ticks"));
    private final Setting.Num fade = add(new Setting.Num("Fade back",
            "How many ticks the look takes to travel back to where the player is actually looking, in mouse steps. 0 snaps back and gives the trick away.",
            4, 0, 10, 1, " ticks"));
    private final Setting.Bool audit = add(new Setting.Bool("Audit the stream",
            "Runs the public rotation checks on our own outgoing packets and reports what would flag. Nothing is sent anywhere.",
            true));
    private final Setting.Choice corner = Hud.corner(this, 3);
    private final Setting.Info state = addSetting(new Setting.Info("What the checks would say", this::lines));

    public Fingerprint() {
        super("fingerprint", "Fingerprint",
                "Makes the outgoing look stream look like a mouse: sensitivity steps, hand noise, held flicks and an audit against the public rotation checks.",
                Category.UTILITY);
        alias("gcd", "sens", "rotation", "stealth", "undetect");
        onByDefault();
    }

    /** Degrees per mouse pixel at the client's own sensitivity: the grid every real rotation sits on. */
    public static double step(Minecraft mc) {
        try {
            double f = mc.options.sensitivity().get() * 0.6 + 0.2;
            return f * f * f * 1.2;
        } catch (RuntimeException e) {
            return 0.15;
        }
    }

    public static int holdTicks() {
        Fingerprint f = live;
        return f == null || !f.enabled() ? 0 : f.hold.i();
    }

    public static int fadeTicks() {
        Fingerprint f = live;
        return f == null || !f.enabled() ? 0 : f.fade.i();
    }

    /** Shape an already grid-snapped angle before it is sent. */
    public static float[] tune(Minecraft mc, float yaw, float pitch, float grid, float lastYaw, float lastPitch) {
        Fingerprint f = live;
        if (f == null || !f.enabled() || mc.player == null) return new float[]{yaw, pitch};
        float viewYaw = mc.player.getYRot();
        float viewPitch = mc.player.getXRot();
        if (f.grid.on() && grid > 0f) {
            // A change smaller than one step is not something a mouse can produce. When the aim
            // wants to move at all, move at least one step instead of sending a 0.02 degree delta.
            float want = Mth.wrapDegrees(yaw - lastYaw);
            if (!Float.isNaN(lastYaw) && Math.abs(want) > 0f && Math.abs(want) < grid
                    && Math.abs(Mth.wrapDegrees(yaw - viewYaw)) > grid * 0.35f) {
                yaw = lastYaw + Math.copySign(grid, want);
            }
            float wantP = pitch - lastPitch;
            if (!Float.isNaN(lastPitch) && Math.abs(wantP) > 0f && Math.abs(wantP) < grid
                    && Math.abs(pitch - viewPitch) > grid * 0.35f) {
                pitch = lastPitch + Math.copySign(grid, wantP);
            }
        }
        if (f.hand.on()) {
            int steps = f.jitter.i();
            if (steps > 0 && grid > 0f) {
                int jy = (int) Math.round((Math.random() * 2 - 1) * steps);
                int jp = (int) Math.round((Math.random() * 2 - 1) * steps);
                // Whole steps only, and never a zero delta on both axes at once.
                if (jy == 0 && jp == 0) jy = Math.random() < 0.5 ? -1 : 1;
                yaw += jy * grid;
                pitch += jp * grid * 0.7f;
            }
        }
        return new float[]{yaw, Math.max(-90f, Math.min(90f, pitch))};
    }

    /** Every rotation packet that actually goes out, for the audit. */
    public static void sent(float yaw, float pitch, float viewYaw, float viewPitch) {
        Fingerprint f = live;
        if (f == null || !f.enabled() || !f.audit.on()) return;
        Minecraft mc = Minecraft.getInstance();
        double grid = step(mc);
        double dy = 0;
        double dp = 0;
        synchronized (Fingerprint.class) {
            if (!Float.isNaN(lastSentYaw)) {
                dy = Math.abs(Mth.wrapDegrees(yaw - lastSentYaw));
                dp = Math.abs(pitch - lastSentPitch);
            }
            samples++;
            if (grid > 0.0001 && dy > grid) {
                double k = dy / grid;
                // Grim keeps a running mode of these divisors; anything clearly between steps is what
                // it counts as a client-made angle.
                if (Math.abs(k - Math.round(k)) > 0.06) offGrid++;
            }
            if (dy == 0 && dp == 0) repeats++;
            if (Math.abs(Mth.wrapDegrees(yaw - viewYaw)) > 3f || Math.abs(pitch - viewPitch) > 3f) silentTicks++;
            if (dy > 0 && Math.abs(dy - lastDeltaYaw) < 1e-4) {
                streak++;
                if (streak > worstStreak) worstStreak = streak;
            } else {
                streak = 0;
            }
            maxSnap = Math.max(maxSnap, Math.max(dy, dp));
            lastDeltaYaw = dy;
            lastSentYaw = yaw;
            lastSentPitch = pitch;
            if (samples > 0) clean = 100.0 - 100.0 * offGrid / samples;
        }
    }

    private int flags() {
        int out = 0;
        if (clean < 98) out++;                        // Aim / invalid sensitivity
        if (worstStreak >= 5) out++;                  // not constant rotations
        if (repeats > 2) out++;                       // repeated yaw values
        Fingerprint me = this;
        if (me.audit.on() && me.grid.on() && maxSnap > 0 && maxSnap > 90) out++;
        return out;
    }

    private String worst() {
        if (clean < 98) return String.format(Locale.ROOT, "%.0f%% of deltas are off the mouse grid", 100 - clean);
        if (worstStreak >= 5) return worstStreak + " identical deltas in a row (constant rotations)";
        if (repeats > 2) return repeats + " repeated look packets";
        if (silentTicks > samples / 3 && samples > 20) return "the view is silently away from the crosshair most of the time";
        return "nothing in the stream stands out";
    }

    private List<String> lines() {
        Minecraft mc = Minecraft.getInstance();
        double grid = step(mc);
        return List.of(
                String.format(Locale.ROOT, "Mouse step  %.4f\\u00B0 per pixel at your sensitivity", grid),
                String.format(Locale.ROOT, "On the grid %.1f%% of %d sent rotations", clean, samples),
                "Off-grid   " + offGrid + "  (an aim check counts these)",
                "Repeated   " + repeats + "  identical packets in a row",
                "Constant   " + worstStreak + "  identical deltas in a row",
                String.format(Locale.ROOT, "Largest    %.1f\\u00B0 in one packet", maxSnap),
                "Silent     " + silentTicks + " ticks with the view away from the crosshair",
                "Now: " + worst());
    }

    @Override
    protected void onEnable() {
        live = this;
        synchronized (Fingerprint.class) {
            lastSentYaw = Float.NaN;
            lastSentPitch = 0f;
            lastDeltaYaw = 0;
            samples = 0;
            offGrid = 0;
            repeats = 0;
            silentTicks = 0;
            streak = 0;
            worstStreak = 0;
            maxSnap = 0;
            clean = 100;
        }
    }

    @Override
    protected void onDisable() {
        live = null;
    }

    @Override
    protected void onTick(Minecraft mc) {
        // Drive held flicks and the fade home even on ticks where no module aims.
        Aim.tick(mc);
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        int fl = flags();
        int col = fl == 0 ? 0xFF5EE67A : fl == 1 ? 0xFFFFD76A : 0xFFFF7A5C;
        int bw = Math.max(150, Hud.captionWidth("Fingerprint") + Hud.chipWidth(Integer.toString(fl)) + 24);
        int bh = Hud.TOP + 23;
        int[] at = Hud.place(corner.index(), bw, bh, w, h);
        Hud.panel(g, at[0], at[1], bw, bh);
        Hud.header(g, at[0], at[1], bw, "Fingerprint", fl == 0 ? "clean" : Integer.toString(fl), col);
        int x = at[0] + Hud.PAD;
        int rw = bw - Hud.PAD * 2;
        int y = at[1] + Hud.TOP;
        Hud.bar(g, x, y, rw, 3, (float) Math.max(0.0, Math.min(1.0, clean / 100.0)), col);
        Draw.text(g, Draw.fit(worst(), rw), x, y + 6, 0xFF8794AC);
        Draw.text(g, Draw.fit(String.format(Locale.ROOT, "%.4f\\u00B0 step \\u00B7 %d samples \\u00B7 %.0f\\u00B0 max",
                step(mc), samples, maxSnap), rw), x, y + 14, 0xFF5E7094);
    }

    @Override
    public String info() {
        int fl = flags();
        return fl == 0 ? "clean" : fl + " flags";
    }
}
