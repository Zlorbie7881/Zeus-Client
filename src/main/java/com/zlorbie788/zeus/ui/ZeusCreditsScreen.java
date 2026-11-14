package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ZeusCreditsScreen extends Screen {
    private final Screen parent;
    private final long born = System.nanoTime();
    private final long[] strike = new long[2];

    public ZeusCreditsScreen(Screen parent) {
        super(Component.literal("About Zeus"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
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
        Draw.textCenterS(g, "ABOUT ZEUS", width / 2, py + Math.round(16 * k), 0xFFFFFFFF, s);
        float ls = Math.max(1f, k * 1.05f);
        int textW = pw - Math.round(36 * k);
        int y = py + Math.round(50 * k);
        int lineH = Math.round(10.5f * ls);
        for (String para : about()) {
            for (String line : wrap(para, textW, ls)) {
                Draw.textCenterS(g, line, width / 2, y, 0xFFDDE6F7, ls);
                y += lineH;
            }
            y += Math.round(lineH * 0.7f);
        }
        y += Math.round(2 * k);
        Draw.textCenterS(g, "Made by Zlorbie788", width / 2, y, 0xFFFFFFFF, ls);
        y += lineH;
        Draw.textCenterS(g, "ideas from mitko_pandata, animations by NikiTheFacter", width / 2, y, 0xFF8EA2CC, Math.max(1f, ls * 0.9f));
        y += lineH;
        Draw.textCenterS(g, "Version " + Zeus.VERSION + " for Fabric 1.21.11", width / 2, y, 0xFF8EA2CC, Math.max(1f, ls * 0.9f));
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

    private static String[] about() {
        int modules = Zeus.all().size();
        return new String[] {
            "Zeus started because we were tired of stacking ten different mods just to get the stuff we actually use every day. So we put it all in one place, made it look nice, and kept going.",
            "Right now there are " + modules + " modules: HUD and visuals, combat, building, movement, a bunch of automation, and Zeus AI, which you can just talk to and it does the work for you.",
            "Everything runs on your own game. Zeus doesn't send your data anywhere and doesn't change anything on the server.",
            "We update it a lot. If something breaks or you have an idea, tell us in the Discord. Honestly, most of the good features came from people asking for them.",
        };
    }

    private static java.util.List<String> wrap(String text, int width, float sc) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (Draw.widthS(next, sc) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
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
