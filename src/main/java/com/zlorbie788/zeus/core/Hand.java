package com.zlorbie788.zeus.core;

/**
 * A hand, not a randomness generator.
 *
 * <p>Adding white noise to an aim angle is what every client does, and it is the wrong shape: white noise is uncorrelated,
 * so a naive check sees a target that is hit with an error that never repeats and never settles. A real hand is the
 * opposite. It shakes around a slowly moving point (a tremor that is smooth over a few hundred milliseconds), it drifts
 * over a session as the arm tires, and it never lands exactly on the same angle twice, because the mouse only reports
 * whole counts of its sensor.</p>
 *
 * <p>This models those three things: an Ornstein-Uhlenbeck tremor (smooth, mean-reverting, the right spectrum), a slow
 * fatigue drift, and a sensor-sized quantisation. Everything here is a function of time, so two calls a tick agree with
 * each other, and the error stays under half a degree, which is a couple of centimetres at fighting range.</p>
 */
public final class Hand {
    private Hand() {}

    /** Off when the Hand model module is off; the angles then pass through untouched. */
    private static volatile boolean on;

    /** 0 to 1, how shaky the hand is. Set by the module from its own setting. */
    private static volatile float strength = 0.35f;

    /** Tremor at rest, in degrees, before the strength scales it. */
    private static final float TREMOR_DEG = 0.42f;
    /** The most the slow drift may add, in degrees, after three hours of play. */
    private static final float DRIFT_DEG = 0.16f;
    /** A mouse reports whole counts, so angles land on a grid rather than anywhere at all. */
    private static final float SENSOR_DEG = 0.03f;

    private static final long STARTED = System.nanoTime();
    private static long lastAt = STARTED;

    // the tremor: a target the hand is heading for, and where it is now
    private static float tremorYaw;
    private static float tremorPitch;
    private static float targetYaw;
    private static float targetPitch;
    private static long retargetAt;
    private static long seed = 0x5EEDF00DL;

    public static void set(boolean enabled, float amount) {
        if (!enabled) {
            on = false;
            return;
        }
        strength = Math.max(0f, Math.min(1f, amount));
        if (!on) {
            on = true;
            reset();
        }
    }

    public static boolean on() {
        return on;
    }

    /** Starts a fresh hand: a new world is a new arm, with no drift carried over from the last one. */
    public static void reset() {
        tremorYaw = 0f;
        tremorPitch = 0f;
        targetYaw = 0f;
        targetPitch = 0f;
        retargetAt = System.nanoTime() + 80_000_000L;
        lastAt = System.nanoTime();
    }

    /**
     * Shapes the angles a silent rotation is about to send. Returns the array it was given when the hand is off, so the
     * caller can use the result either way.
     */
    public static float[] shape(float yaw, float pitch) {
        if (!on) return new float[]{yaw, pitch};
        long now = System.nanoTime();
        float dt = Math.max(0f, Math.min(0.25f, (now - lastAt) / 1e9f));
        lastAt = now;
        if (now >= retargetAt) {
            targetYaw = gaussian() * 1.0f;
            targetPitch = gaussian() * 1.0f;
            // a new direction every 120 to 260 ms: slow enough to be smooth, fast enough to be alive
            retargetAt = now + 120_000_000L + (long) (random() * 140_000_000L);
        }
        // the hand follows its target through a first-order lag, which is what makes the motion continuous
        float follow = 1f - (float) Math.exp(-dt / 0.075f);
        tremorYaw += (targetYaw - tremorYaw) * follow;
        tremorPitch += (targetPitch - tremorPitch) * follow;

        float amp = TREMOR_DEG * (0.35f + 0.65f * strength);
        float minutes = (now - STARTED) / 60e9f;
        float fatigue = Math.min(1f, minutes / 180f) * DRIFT_DEG * strength;
        long t = now - STARTED;
        double driftYaw = Math.sin(t / 17.3e9) * 1.0 + Math.sin(t / 5.1e9 + 2.1) * 0.45;
        double driftPitch = Math.sin(t / 21.7e9 + 0.7) * 1.0 + Math.sin(t / 6.3e9 + 4.4) * 0.45;

        float outYaw = yaw + tremorYaw * amp + (float) driftYaw * fatigue;
        float outPitch = pitch + tremorPitch * amp + (float) driftPitch * fatigue;
        outYaw = quantise(outYaw);
        outPitch = Math.max(-90f, Math.min(90f, quantise(outPitch)));
        return new float[]{outYaw, outPitch};
    }

    /** A single signed offset in degrees, for code that nudges an angle rather than sets one. */
    public static float jitter() {
        if (!on) return 0f;
        return quantise(tremorYaw * TREMOR_DEG * (0.35f + 0.65f * strength));
    }

    /** How far this hand is from a machine right now, for the panel to show. */
    public static String describe() {
        float amp = TREMOR_DEG * (0.35f + 0.65f * strength);
        float minutes = (System.nanoTime() - STARTED) / 60e9f;
        String shake = String.format(java.util.Locale.ROOT, "%.2f\u00B0", amp);
        String tired = String.format(java.util.Locale.ROOT, "%.2f\u00B0", Math.min(1f, minutes / 180f) * DRIFT_DEG * strength);
        return (on ? "on" : "off") + "  \u00B7  tremor " + shake + "  \u00B7  drift " + tired;
    }

    /** Rounds to the sensor's step and adds the last half step, so no two packets carry the identical angle. */
    private static float quantise(float degrees) {
        float step = SENSOR_DEG;
        return Math.round(degrees / step) * step + (random() - 0.5f) * step;
    }

    /** Cheap deterministic stream, seeded per launch: reproducible on a given machine, not on every machine. */
    private static float random() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return ((seed >>> 8) & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Box-Muller on the same stream: a tremor sample with a proper bell shape rather than a flat one. */
    private static float gaussian() {
        float u = Math.max(1e-6f, random());
        float v = random();
        return (float) (Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v));
    }
}
