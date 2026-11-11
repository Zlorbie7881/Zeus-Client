package com.zlorbie788.zeus.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

public final class ZeusMenu {
    private ZeusMenu() {}

    public static final String MENU_VERSION = com.zlorbie788.zeus.core.Zeus.VERSION;

    private static boolean launched;
    private static boolean warned;

    public static Screen swap(Screen s) {
        if (com.zlorbie788.zeus.core.Zeus.destroyed()) return s;
        if (!(s instanceof TitleScreen)) return s;
        try {
            if (!UiSettings.MENU.on()) return s;
            boolean first = !launched;
            launched = true;
            if (first && UiSettings.STARTUP_VIDEO.on() && VideoIntroScreen.available()) {
                return new VideoIntroScreen(ZeusTitleScreen::new, true);
            }
            return new ZeusTitleScreen();
        } catch (RuntimeException | LinkageError e) {
            if (!warned) com.zlorbie788.zeus.core.Zeus.LOG.warn("Zeus menu could not start; using the normal title screen", e);
            warned = true;
            return s;
        }
    }

    public static void tick(Minecraft mc) {
        Screen s = mc.screen;
        if (!(s instanceof TitleScreen) || mc.getOverlay() != null) return;
        Screen r = swap(s);
        if (r != s) mc.setScreen(r);
    }
}
