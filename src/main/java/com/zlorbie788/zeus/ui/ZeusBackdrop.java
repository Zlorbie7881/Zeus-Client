package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

public final class ZeusBackdrop {
    private ZeusBackdrop() {}

    private static final long BORN = System.nanoTime();
    private static boolean busy;
    private static boolean warned;

    public static boolean draw(Screen s, GuiGraphics g) {
        if (busy) return false;
        try {
            if (!UiSettings.BACKDROP.on() || !UiSettings.MENU.on()) return false;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level != null) return false;
            if (s instanceof TitleScreen || s instanceof ZeusTitleScreen || s instanceof ZeusCreditsScreen || s instanceof VideoIntroScreen
                    || s instanceof LevelLoadingScreen || s instanceof ConnectScreen) return false;
            busy = true;
            float t = (System.nanoTime() - BORN) / 1e9f;
            MenuAudio.backdrop = true;
            ZeusTitleScreen.scene(g, s.width, s.height, t, new long[2], true);
            g.fill(0, 0, s.width, s.height, Draw.rgba(0xFF04070F, 0.36f));
            return true;
        } catch (RuntimeException | LinkageError e) {
            if (!warned) Zeus.LOG.warn("The Zeus sky could not be drawn behind a screen; using the game's own background", e);
            warned = true;
            return false;
        } finally {
            MenuAudio.backdrop = false;
            busy = false;
        }
    }
}
