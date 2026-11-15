package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Always on, no module, no setting: this is the part of the client that makes the packets it sends
 * look like the packets a mouse makes.
 *
 * <p>Grim's aim processor (and Intave, Vulcan, Polar after it) takes the deltas between consecutive
 * rotation packets, keeps only the ones under five degrees, and takes the running <em>mode of their
 * GCD</em> over eighty samples. That mode is turned back into a sensitivity with
 * {@code ((gcd / 0.15 / 8)^(1/3) - 0.2) / 0.6}; a value that is not a whole number, or that changes
 * every sample, is a client-made angle.
 *
 * <p>So the thing that has to be on the grid is not the angle, it is the <em>movement</em>: a mouse
 * moves in whole pixels, and {@code (sens * 0.6 + 0.2)^3 * 1.2} degrees is what one pixel does. Every
 * rotation this class produces is the previous sent angle plus a whole number of steps, which makes
 * the GCD mode equal to the step exactly, and the sensitivity Grim recovers is the player's own.
 *
 * <p>The second tell is shape rather than numbers. A silent rotation that is dropped the moment the
 * hit lands shows up as an instant snap back to the crosshair, and a look stream that never varies
 * shows up as identical deltas. Flicks are therefore held for a moment and walked home in steps, and
 * the aim carries whole-step hand noise.
 */
public final class Stealth {
    private Stealth() {}

    /** How many ticks a flick is held before anything is done about it. */
    public static final int HOLD_TICKS = 2;
    /** How many ticks the look takes to travel back to the real view. */
    public static final int FADE_TICKS = 4;
    private static final double HAND_STEPS = 1.0;

    private static int samples;
    private static int offGrid;
    private static int repeated;
    private static double maxDelta;

    /** Degrees per mouse pixel at this client's sensitivity: the grid a real mouse sits on. */
    public static double grid(Minecraft mc) {
        try {
            double f = mc.options.sensitivity().get() * 0.6 + 0.2;
            return Math.max(1e-5, f * f * f * 1.2);
        } catch (RuntimeException e) {
            return 0.15;
        }
    }

    /**
     * The next angle on the wire: last sent angle plus whole mouse steps towards the target.
     * A change smaller than a step is not something a mouse can produce while the aim is moving,
     * so it becomes exactly one step rather than a fractional delta.
     */
    public static float stepTo(float last, float target, double grid, boolean pitch) {
        double delta = pitch ? target - last : Mth.wrapDegrees(target - last);
        long steps = Math.round(delta / grid);
        if (steps == 0 && Math.abs(delta) > grid * 0.35) steps = delta > 0 ? 1 : -1;
        return last + (float) (steps * grid);
    }

    /** One tick of a fade back towards the view: several steps at first, the last step exactly on. */
    public static float fadeStep(float last, float target, int remaining, double grid, boolean pitch) {
        double delta = pitch ? target - last : Mth.wrapDegrees(target - last);
        if (Math.abs(delta) < grid) return target;
        long steps = Math.max(1, Math.round(Math.abs(delta) / grid / Math.max(1, remaining)));
        return last + (float) (Math.signum(delta) * steps * grid);
    }

    /**
     * Shape one rotation packet. {@code lastYaw}/{@code lastPitch} are the last values actually sent;
     * when nothing has been sent yet the player's own view is the starting point, which is what the
     * server currently has.
     */
    public static float[] shape(Minecraft mc, float yaw, float pitch, float lastYaw, float lastPitch,
                                boolean sentBefore) {
        double grid = grid(mc);
        float fromYaw = sentBefore ? lastYaw : mc.player.getYRot();
        float fromPitch = sentBefore ? lastPitch : mc.player.getXRot();
        float y = stepTo(fromYaw, yaw, grid, false);
        float p = stepTo(fromPitch, Math.max(-90f, Math.min(90f, pitch)), grid, true);
        // Whole steps of hand noise, and never a zero delta on both axes: identical deltas are a
        // check of their own, and noise between the steps would hand the GCD check a false divisor.
        int jy = (int) Math.round((Math.random() * 2 - 1) * HAND_STEPS);
        int jp = (int) Math.round((Math.random() * 2 - 1) * HAND_STEPS);
        if (jy == 0 && jp == 0) jy = Math.random() < 0.5 ? -1 : 1;
        y += (float) (jy * grid);
        p += (float) (jp * grid * 0.7);
        return new float[]{y, Math.max(-90f, Math.min(90f, p))};
    }

    /** Feed every rotation that actually goes out, for the audit Grim-style checks would run. */
    public static void sent(float yaw, float pitch, float viewYaw, float viewPitch, float lastYaw,
                            float lastPitch, boolean sentBefore) {
        if (!sentBefore) return;
        Minecraft mc = Minecraft.getInstance();
        double grid = grid(mc);
        double dy = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
        double dp = Math.abs(pitch - lastPitch);
        samples++;
        if (grid > 1e-4 && dy > grid) {
            double k = dy / grid;
            if (Math.abs(k - Math.round(k)) > 0.06) offGrid++;
        }
        if (dy == 0 && dp == 0) repeated++;
        maxDelta = Math.max(maxDelta, Math.max(dy, dp));
    }

    public static int samples() {
        return samples;
    }

    public static String summary() {
        if (samples == 0) return "no rotations sent yet";
        return String.format(java.util.Locale.ROOT, "%.1f%% of %d deltas on the mouse grid, %d repeated, largest %.1f\\u00B0",
                100.0 - 100.0 * offGrid / samples, samples, repeated, maxDelta);
    }

    public static boolean clean() {
        return samples < 20 || offGrid * 100 <= samples * 2;
    }

    public static void reset() {
        samples = 0;
        offGrid = 0;
        repeated = 0;
        maxDelta = 0;
    }
}
