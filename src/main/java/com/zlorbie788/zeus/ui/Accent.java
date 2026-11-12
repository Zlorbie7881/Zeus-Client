package com.zlorbie788.zeus.ui;

public final class Accent {
    private Accent() {}

    private static int wheel(float offset) {
        float speed = (float) UiSettings.RAINBOW_SPEED.get() / 100f;
        float hue = ((System.nanoTime() / 1_000_000_000.0f) * 0.08f * speed + offset) % 1f;
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.62f, 1f);
    }

    public static boolean rainbow() {
        return Themes.rainbow(UiSettings.ACCENT.index());
    }

    public static int rainbowAt(float offset) {
        return wheel(offset);
    }

    public static int main() {
        if (rainbow()) return wheel(0f);
        return Themes.get(Themes.paletteFor(UiSettings.ACCENT.index())).main();
    }

    public static int second() {
        if (rainbow()) return wheel(0.33f);
        return Themes.get(Themes.paletteFor(UiSettings.ACCENT.index())).second();
    }

    public static int surface(int base) {
        if (rainbow()) return base;
        float reach = (float) UiSettings.THEME_TINT.get() / 100f;
        if (reach <= 0f) return base;
        return Draw.mix(base, 0xFF000000 | Themes.get(Themes.paletteFor(UiSettings.ACCENT.index())).tint(), reach * 0.55f);
    }

    public static int glow() {
        return rainbow() ? wheel(0.15f) : Draw.mix(main(), 0xFFFFFFFF, 0.25f);
    }

    public static final int OK = 0xFF5EE6A0;
    public static final int WARN = 0xFFFFC857;
    public static final int BAD = 0xFFFF5C6C;
    public static final int INFO = 0xFF6FA8FF;
    public static final int HIGH = 0xFFFF8F4D;

    public static int level(float fraction) {
        return fraction > 0.5f ? OK : fraction > 0.22f ? WARN : BAD;
    }

    public static int ping(int ms) {
        return ms < 60 ? OK : ms < 120 ? WARN : ms < 200 ? 0xFFFF8F4D : BAD;
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
