package com.zlorbie788.zeus.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * The Z button: a small button with the Zeus logo in the corner of every title screen that is not the Zeus menu (the game's
 * own, or one another mod draws). Each click opens the Zeus menu, whatever the menu switch says, so there is always a way back.
 */
public final class ZButton {
    private ZButton() {}

    private static final int SIZE = 24;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof TitleScreen) attach(mc, screen);
        });
    }

    private static void attach(Minecraft mc, Screen screen) {
        try {
            Screens.getButtons(screen).add(Button.builder(Component.literal(""), b -> open(mc)).bounds(6, 6, SIZE, SIZE).build());
            ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> draw(g, mx, my));
        } catch (RuntimeException | LinkageError e) {
            com.zlorbie788.zeus.core.Zeus.LOG.warn("The Z button could not be added to the title screen", e);
        }
    }

    static void open(Minecraft mc) {
        try {
            mc.setScreen(new ZeusTitleScreen());
        } catch (RuntimeException e) {
            com.zlorbie788.zeus.core.Zeus.LOG.warn("The Zeus menu could not be opened", e);
        }
    }

    private static void draw(net.minecraft.client.gui.GuiGraphics g, int mx, int my) {
        boolean hot = mx >= 6 && mx < 6 + SIZE && my >= 6 && my < 6 + SIZE;
        // an opaque tile first, so whatever another mod painted on the button is covered
        Draw.rrect(g, 6, 6, SIZE, SIZE, 5, hot ? 0xFF0F2A5E : 0xFF081330);
        Draw.rrectLine(g, 6, 6, SIZE, SIZE, 5, hot ? 0xFF5AA2FF : 0xFF2358B8);
        if (hot) Draw.glow(g, 6, 6, SIZE, SIZE, 5, 0xFF2F86FF, 3, 0.25f);
        int gs = Tex.guiScale();
        int inner = SIZE - 6;
        Tex.Img logo = Tex.fitted("zbutton", "textures/gui/logo_z.png", inner * gs, inner * gs, false, 0, 0);
        if (logo != null) logo.draw(g, 6 + 3, 6 + 3, inner, inner, 0xFFFFFFFF);
        else Draw.boldCenter(g, "Z", 6 + SIZE / 2, 6 + 8, 0xFF8CC4FF);
    }
}
