package com.zlorbie788.zeus.core;

public final class Hand {
    private Hand() {}

    private static volatile boolean on;

    private static volatile float strength = 0.35f;

    private static final float TREMOR_DEG = 0.42f;

    private static final float DRIFT_DEG = 0.16f;

    private static final float SENSOR_DEG = 0.03f;

    private static final long STARTED = System.nanoTime();
    private static long lastAt = STARTED;

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

    public static void reset() {
        tremorYaw = 0f;
        tremorPitch = 0f;
        targetYaw = 0f;
        targetPitch = 0f;
        retargetAt = System.nanoTime() + 80_000_000L;
        lastAt = System.nanoTime();
    }

    public static float[] shape(float yaw, float pitch) {
        if (!on) return new float[]{yaw, pitch};
        long now = System.nanoTime();
        float dt = Math.max(0f, Math.min(0.25f, (now - lastAt) / 1e9f));
        lastAt = now;
        if (now >= retargetAt) {
            targetYaw = gaussian() * 1.0f;
            targetPitch = gaussian() * 1.0f;
            retargetAt = now + 120_000_000L + (long) (random() * 140_000_000L);
        }
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

    public static float jitter() {
        if (!on) return 0f;
        return quantise(tremorYaw * TREMOR_DEG * (0.35f + 0.65f * strength));
    }

    public static String describe() {
        float amp = TREMOR_DEG * (0.35f + 0.65f * strength);
        float minutes = (System.nanoTime() - STARTED) / 60e9f;
        String shake = String.format(java.util.Locale.ROOT, "%.2f\u00B0", amp);
        String tired = String.format(java.util.Locale.ROOT, "%.2f\u00B0", Math.min(1f, minutes / 180f) * DRIFT_DEG * strength);
        return (on ? "on" : "off") + "  \u00B7  tremor " + shake + "  \u00B7  drift " + tired;
    }

    private static float quantise(float degrees) {
        float step = SENSOR_DEG;
        return Math.round(degrees / step) * step + (random() - 0.5f) * step;
    }

    private static float random() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return ((seed >>> 8) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float gaussian() {
        float u = Math.max(1e-6f, random());
        float v = random();
        return (float) (Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v));
    }
}
