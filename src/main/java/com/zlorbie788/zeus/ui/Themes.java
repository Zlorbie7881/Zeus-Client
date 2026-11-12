package com.zlorbie788.zeus.ui;

import java.util.Locale;

public final class Themes {
    private Themes() {}

    public record Palette(String name, int main, int second, int tint) {}

    private static final Palette[] SETS = {
            new Palette("Storm blue", 0xFF3DB8FF, 0xFF7B6BFF, 0xFF0E1A2E),
            new Palette("Violet", 0xFF9A6BFF, 0xFFFF6BD6, 0xFF1A1230),
            new Palette("Gold", 0xFFFFC23D, 0xFFFF7A3D, 0xFF241A0B),
            new Palette("Emerald", 0xFF3DE0A0, 0xFF3DB8FF, 0xFF0B221B),
            new Palette("Crimson", 0xFFFF4D6D, 0xFFFF9A3D, 0xFF240E14),
            new Palette("Glacier", 0xFF9BE7FF, 0xFF5AA9FF, 0xFF0B1B26),
            new Palette("Magma", 0xFFFF7A45, Accent.WARN, 0xFF251108),
            new Palette("Orchid", 0xFFE48BFF, 0xFF8B7BFF, 0xFF1D1024),
            new Palette("Slate", 0xFFC7D2E8, 0xFF8E9BBC, 0xFF151A24),
    };

    public static final int RAINBOW = 5;

    public static final String[] LABELS = {
            "Storm blue", "Violet", "Gold", "Emerald", "Crimson", "Rainbow", "Glacier", "Magma", "Orchid", "Slate"
    };

    public static final String[] IDS = {
            "storm", "violet", "gold", "emerald", "crimson", "rainbow", "glacier", "magma", "orchid", "slate"
    };

    public static int count() {
        return SETS.length;
    }

    public static Palette get(int paletteIndex) {
        return SETS[Math.max(0, Math.min(SETS.length - 1, paletteIndex))];
    }

    public static int paletteFor(int optionIndex) {
        int mapped = optionIndex < RAINBOW ? optionIndex : optionIndex - 1;
        return Math.max(0, Math.min(SETS.length - 1, mapped));
    }

    public static boolean rainbow(int optionIndex) {
        return optionIndex == RAINBOW;
    }

    public static String[] ids() {
        return IDS.clone();
    }

    public static String[] labels() {
        return LABELS.clone();
    }

    static String label(int paletteIndex) {
        return get(paletteIndex).name().toUpperCase(Locale.ROOT);
    }
}
