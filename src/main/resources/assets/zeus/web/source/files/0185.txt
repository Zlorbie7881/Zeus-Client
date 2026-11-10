package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Who made what, on the same storm as the menu. */
public final class ZeusCreditsScreen extends Screen {
    private final Screen parent;
    private final long born = System.nanoTime();
    private final long[] strike = new long[2];

    public ZeusCreditsScreen(Screen parent) {
        super(Component.literal("Credits"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // drawn in render
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private int backX() {
        return width / 2 - Math.max(40, height / 8);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = (System.nanoTime() - born) / 1e9f;
        ZeusTitleScreen.scene(g, width, height, t, strike);
        float fl = ZeusTitleScreen.strike(g, width, height, t, strike);
        if (fl > 0.01f) g.fill(0, 0, width, height, Draw.rgba(0xFFCFE4FF, 0.18f * fl));
        float k = height / 473f;
        int pw = Math.min(width - 20, Math.round(300 * k));
        int ph = Math.round(372 * k);
        int px = (width - pw) / 2;
        int py = Math.round(70 * k);
        Draw.rrect(g, px, py, pw, ph, Math.max(4, Math.round(8 * k)), 0xD0070C1A, 0xD0050914);
        Draw.rrectLine(g, px, py, pw, ph, Math.max(4, Math.round(8 * k)), 0xFF234A8A);
        float s = Math.max(1f, k * 2.2f);
        Draw.textCenterS(g, "ZEUS CLIENT", width / 2, py + Math.round(16 * k), 0xFFFFFFFF, s);
        String[][] lines = {
            {"Client, code, website, images", "Zlorbie788"},
            {"Modules and feature ideas", "mitko_pandata"},
            {"Animations", "NikiTheFacter"},
            {"Base mod style reference", "Meteor Client"},
            {"Built for", "Fabric, Minecraft 1.21.11"},
            {"Version", Zeus.VERSION},
            {"Everything runs", "on your side only"}
        };
        float ls = Math.max(1f, k * 1.2f);
        int y = py + Math.round(58 * k);
        for (String[] l : lines) {
            Draw.textCenterS(g, l[0], width / 2, y, 0xFF8EA2CC, ls);
            Draw.textCenterS(g, l[1], width / 2, y + Math.round(11 * k * 1.2f), 0xFFFFFFFF, ls);
            y += Math.round(34 * k);
        }
        int bw = Math.round(120 * k);
        int bh = Math.round(24 * k);
        int bx = (width - bw) / 2;
        int by = py + ph - bh - Math.round(14 * k);
        boolean hot = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        Draw.rrect(g, bx, by, bw, bh, 6, hot ? 0xFF10306E : 0xFF0A1A3C);
        Draw.rrectLine(g, bx, by, bw, bh, 6, hot ? 0xFF3C92FF : 0xFF234A8A);
        Draw.textCenterS(g, "Back", width / 2, by + (bh - Math.round(8 * ls)) / 2, 0xFFFFFFFF, ls);
        float in = Math.max(0f, 1f - t / 0.4f);
        if (in > 0.01f) g.fill(0, 0, width, height, Draw.rgba(0xFF000000, in));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        float k = height / 473f;
        int pw = Math.min(width - 20, Math.round(300 * k));
        int ph = Math.round(372 * k);
        int py = Math.round(70 * k);
        int bw = Math.round(120 * k);
        int bh = Math.round(24 * k);
        int bx = (width - bw) / 2;
        int by = py + ph - bh - Math.round(14 * k);
        if (event.button() == 0 && event.x() >= bx && event.x() < bx + bw && event.y() >= by && event.y() < by + bh) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
