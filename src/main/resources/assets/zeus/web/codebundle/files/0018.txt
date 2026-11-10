package com.zlorbie788.zeus.core;

/** The pages of the panel. */
public enum Category {
    COMBAT("Combat", 0xFFFF5C6C),
    MOVEMENT("Movement", 0xFF4DA3FF),
    PLAYER("Player", 0xFF5EE6A0),
    WORLD("World", 0xFFFFC857),
    RENDER("Visuals", 0xFFB77BFF),
    SCANNER("Base Finding", 0xFF35E0D0),
    UTILITY("Tools", 0xFFFF8F4D),
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
}
