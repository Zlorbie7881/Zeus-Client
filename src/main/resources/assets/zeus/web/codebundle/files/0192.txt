package com.zlorbie788.zeus.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;

/** Draws one row of the world list or the server list as a stone plate (see {@code ListPlateMixin}). */
public final class ListPlates {
    private ListPlates() {}

    public static boolean active(Object list) {
        if (!(list instanceof WorldSelectionList) && !(list instanceof ServerSelectionList)) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level == null && UiSettings.STONE_LISTS.on() && UiSettings.MENU.on();
    }

    public static void row(AbstractSelectionList<?> list, Object entry, GuiGraphics g, int mx, int my) {
        int index = list.children().indexOf(entry);
        if (index < 0) return;
        int left = list.getRowLeft() - 3;
        int width = list.getRowWidth() + 6;
        int top = list.getRowTop(index);
        int bottom = list.getRowBottom(index);
        int h = bottom - top - 4;
        if (h < 10) return;
        int y = top + 1;
        boolean selected = list.getSelected() == entry;
        boolean hot = mx >= left && mx < left + width && my >= y && my < y + h && my >= list.getY() && my < list.getBottom();
        float t = (System.nanoTime() / 1_000_000L % 100_000_000L) / 1000f;
        if (selected || hot) Draw.glow(g, left, y, width, h, 3, 0xFF7C9CE0, 5, selected ? 0.40f : 0.22f);
        Plate9.draw(g, left, y, width, h, hot || selected ? 0xFFFFFFFF : 0xFFE6E6EE);
        if (selected) {
            Draw.rrectLine(g, left, y, width, h, 3, Draw.rgba(0xFFA8BEEA, 0.8f));
            if (!UiSettings.CALM.on() && !Perf.fast()) PlateFx.hover(g, left, y, width, h, t, 1f, Math.max(0.8f, h / 26f));
        } else if (hot) {
            Draw.rrectLine(g, left, y, width, h, 3, Draw.rgba(0xFFA8BEEA, 0.5f));
        }
    }
}
