package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class KnifePickerScreen extends Screen {

    public interface Chosen {
        void pick(int knife, int finish);
    }

    /** Only the HD knives are shown; tile i is knife kind FIRST + i. */
    private static final int FIRST = KnifeArt.allKnives().length - KnifeArt.HD_COUNT;
    private final Screen parent;
    private final Chosen chosen;
    private final int[] finishOf;
    private int knife;
    private int px, py, pw, ph;

    public KnifePickerScreen(Screen parent, int knife, int[] finishes, Chosen chosen) {
        super(Component.literal("Knife gallery"));
        this.parent = parent;
        this.chosen = chosen;
        this.finishOf = finishes.clone();
        this.knife = Math.max(FIRST, Math.min(KnifeArt.allKnives().length - 1, knife));
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

    private static final int KCOLS = 5;
    private static final int FCOLS = 5;

    private int gridTop() {
        return py + 30;
    }

    private int gridH() {
        return ph - 30 - 26;
    }

    private int leftW() {
        return Math.min(250, (pw - 36) * 55 / 100);
    }

    private int kTileW() {
        return (leftW() - (KCOLS - 1) * 3) / KCOLS;
    }

    private int kTileH() {
        int rows = (KnifeArt.HD_COUNT + KCOLS - 1) / KCOLS;
        return Math.max(20, Math.min(46, (gridH() - (rows - 1) * 3) / rows));
    }

    private int rightX() {
        return px + 12 + leftW() + 12;
    }

    private int rightW() {
        return px + pw - 12 - rightX();
    }

    private int previewH() {
        return Math.max(50, gridH() * 44 / 100);
    }

    private int fTileW() {
        return (rightW() - (FCOLS - 1) * 3) / FCOLS;
    }

    private int fTileH() {
        int rows = (KnifeArt.finishNames(knife).length + FCOLS - 1) / FCOLS;
        return Math.max(18, Math.min(34, (gridH() - previewH() - 24 - (rows - 1) * 3) / rows));
    }

    private void layout() {
        pw = Math.min(width - 16, 460);
        ph = Math.min(height - 16, 272);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1e9f;
        String[] knives = KnifeArt.allKnives();
        String[] fins = KnifeArt.finishNames(knife);
        g.fill(0, 0, width, height, 0xB0030610);
        Draw.shadow(g, px, py, pw, ph, 8, 10, 0.5f);
        Draw.rrect(g, px, py, pw, ph, 8, 0xF2070C1A, 0xF2050914);
        Draw.rrectLine(g, px, py, pw, ph, 8, 0xFF234A8A);
        Draw.bold(g, "ZEUS / KNIFE GALLERY", px + 12, py + 10, 0xFFE9F1FF);
        Draw.textRight(g, "The same art the game draws", px + pw - 12, py + 10, 0xFF8EA2CC);

        int tw = kTileW(), th = kTileH(), gx = px + 12, gy = gridTop();
        for (int t = 0; t < KnifeArt.HD_COUNT; t++) {
            int i = FIRST + t;
            int x = gx + (t % KCOLS) * (tw + 3), y = gy + (t / KCOLS) * (th + 3);
            boolean on = i == knife, hot = mouseX >= x && mouseX < x + tw && mouseY >= y && mouseY < y + th;
            Draw.rrect(g, x, y, tw, th, 4, on ? 0xFF14306A : hot ? 0xFF10224A : 0xFF0A1530);
            Draw.rrectLine(g, x, y, tw, th, 4, on ? 0xFF5CA8FF : 0xFF1E3A70);
            KnifeArt.showcase(g, i, Math.min(finishOf[i], KnifeArt.finishNames(i).length - 1), x + 2, y + 2, tw - 4, th - 4, time, false);
            if (hot) {
                Draw.textCenter(g, Draw.fit(knives[i], tw * 3), x + tw / 2, y + th + 4 > gy + gridH() ? y - 10 : y + th - 9, 0xFFFFFFFF);
            }
        }

        int rx = rightX(), rw = rightW(), pvh = previewH();
        int fin = Math.min(finishOf[knife], fins.length - 1);
        Draw.rrect(g, rx, gy, rw, pvh, 6, 0xFF08122A);
        Draw.rrectLine(g, rx, gy, rw, pvh, 6, 0xFF234A8A);
        KnifeArt.showcase(g, knife, fin, rx + 4, gy + 4, rw - 8, pvh - 8, time, true);
        Draw.bold(g, knives[knife].toUpperCase(Locale.ROOT), rx + 4, gy + pvh + 4, 0xFFE9F1FF);
        Draw.textRight(g, fins[fin], rx + rw, gy + pvh + 4, 0xFF8CC4FF);
        int fw = fTileW(), fh = fTileH(), fy0 = gy + pvh + 18;
        for (int i = 0; i < fins.length; i++) {
            int x = rx + (i % FCOLS) * (fw + 3), y = fy0 + (i / FCOLS) * (fh + 3);
            boolean on = i == fin, hot = mouseX >= x && mouseX < x + fw && mouseY >= y && mouseY < y + fh;
            Draw.rrect(g, x, y, fw, fh, 4, on ? 0xFF14306A : hot ? 0xFF10224A : 0xFF0A1530);
            Draw.rrectLine(g, x, y, fw, fh, 4, on ? 0xFF5CA8FF : 0xFF1E3A70);
            KnifeArt.showcase(g, knife, i, x + 2, y + 2, fw - 4, fh - 4, time, false);
            if (hot) Draw.textCenter(g, Draw.fit(fins[i], 120), x + fw / 2, y - 10, 0xFFFFFFFF);
        }
        Draw.text(g, "Click a knife, then a finish. Esc closes.", px + 12, py + ph - 18, 0xFF8EA2CC);
        int bx = px + pw - 12 - 56, by = py + ph - 20;
        boolean hotDone = mouseX >= bx && mouseX < bx + 56 && mouseY >= by - 2 && mouseY < by + 16;
        Draw.rrect(g, bx, by - 2, 56, 18, 5, hotDone ? 0xFF1E4C9A : 0xFF14306A);
        Draw.boldCenter(g, "Done", bx + 28, by + 2, 0xFFE9F1FF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        layout();
        double x = event.x();
        double y = event.y();
        if (event.button() == 0) {
            int bx = px + pw - 12 - 56, by = py + ph - 20;
            if (x >= bx && x < bx + 56 && y >= by - 2 && y < by + 16) {
                onClose();
                return true;
            }
            int tw = kTileW(), th = kTileH(), gx = px + 12, gy = gridTop();
            for (int t = 0; t < KnifeArt.HD_COUNT; t++) {
                int tx = gx + (t % KCOLS) * (tw + 3), ty = gy + (t / KCOLS) * (th + 3);
                if (x >= tx && x < tx + tw && y >= ty && y < ty + th) {
                    knife = FIRST + t;
                    chosen.pick(knife, finishOf[knife]);
                    return true;
                }
            }
            int rx = rightX(), fw = fTileW(), fh = fTileH(), fy0 = gy + previewH() + 18;
            int fn = KnifeArt.finishNames(knife).length;
            for (int i = 0; i < fn; i++) {
                int tx = rx + (i % FCOLS) * (fw + 3), ty = fy0 + (i / FCOLS) * (fh + 3);
                if (x >= tx && x < tx + fw && y >= ty && y < ty + fh) {
                    if (knife >= 2) for (int q = 2; q < finishOf.length; q++) finishOf[q] = i;
                    else finishOf[knife] = i;
                    chosen.pick(knife, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256 || key == 257 || key == 335) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
