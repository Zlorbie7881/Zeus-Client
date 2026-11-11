package com.zlorbie788.zeus.ui;

public final class Accent {
    private Accent() {}

    private static final int[][] SETS = {
        {0xFF3DB8FF, 0xFF7B6BFF},
        {0xFF9A6BFF, 0xFFFF6BD6},
        {0xFFFFC23D, 0xFFFF7A3D},
        {0xFF3DE0A0, 0xFF3DB8FF},
        {0xFFFF4D6D, 0xFFFF9A3D},
    };

    private static int wheel(float offset) {
        float speed = (float) UiSettings.RAINBOW_SPEED.get() / 100f;
        float hue = ((System.nanoTime() / 1_000_000_000.0f) * 0.08f * speed + offset) % 1f;
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.62f, 1f);
    }

    public static boolean rainbow() {
        return UiSettings.ACCENT.index() >= SETS.length;
    }

    public static int rainbowAt(float offset) {
        return wheel(offset);
    }

    public static int main() {
        if (rainbow()) return wheel(0f);
        return SETS[Math.max(0, Math.min(SETS.length - 1, UiSettings.ACCENT.index()))][0];
    }

    public static int second() {
        if (rainbow()) return wheel(0.33f);
        return SETS[Math.max(0, Math.min(SETS.length - 1, UiSettings.ACCENT.index()))][1];
    }

    public static final int BG = 0xFF0A0D15;
    public static final int PANEL = 0xFF0F1320;
    public static final int CARD = 0xFF151A2B;
    public static final int CARD_HI = 0xFF1B2136;
    public static int LINE = 0xFF252C44;
    public static int TEXT = 0xFFE9EEFB;
    public static int DIM = 0xFF8791AE;
    public static int FAINT = 0xFF58627F;

    public static void contrast(boolean high) {
        LINE = high ? 0xFF4A5680 : 0xFF252C44;
        TEXT = high ? 0xFFFFFFFF : 0xFFE9EEFB;
        DIM = high ? 0xFFC9D2EC : 0xFF8791AE;
        FAINT = high ? 0xFF9AA6C6 : 0xFF58627F;
    }
}
