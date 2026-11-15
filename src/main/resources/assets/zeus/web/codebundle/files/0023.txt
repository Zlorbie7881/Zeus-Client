package com.zlorbie788.zeus.core;

import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import com.zlorbie788.zeus.ui.UiSettings;

public enum Category {
    COMBAT("Combat", Accent.BAD),
    MOVEMENT("Movement", 0xFF4DA3FF),
    PLAYER("Player", Accent.OK),
    WORLD("World", Accent.WARN),
    RENDER("Visuals", 0xFFB77BFF),
    SCANNER("Base Finding", 0xFF35E0D0),
    UTILITY("Tools", Accent.HIGH),
    FRIENDS("Friends", 0xFF7BE0A0),
    PROFILES("Profiles", 0xFFE070C8),
    PANELS("HUD & Info", 0xFF6FA8FF),
    INTERFACE("Interface", 0xFF9AA7C7);

    public final String label;
    public final int color;

    Category(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public int color() {
        if (Accent.rainbow()) return color;
        float reach = (float) UiSettings.THEME_TINT.get() / 100f;
        if (reach <= 0f) return color;
        return Draw.mix(color, Accent.main(), reach * 0.35f);
    }
}
