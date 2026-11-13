package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The knife switcher: every HD knife as a live 3D tile, a large turning preview of the one under
 * the mouse (or the one in hand), and a click to hold it.
 */
public final class KnifePickerScreen extends Screen {

    public interface Chosen {
        void pick(int knife, int finish);
    }

    /** Only the HD knives are offered; tile i is knife kind FIRST + i. */
    private static final int FIRST = KnifeArt.allKnives().length - KnifeArt.HD_COUNT;
    private static final int COLS = 4;

    private static final String[][] ABOUT = {
            {"Damascus", "Pattern-welded steel folded into flowing grey layers, orange-trimmed rubber grip, brass pommel."},
            {"Slaughter", "Polished steel splashed with deep red, stippled orange grip and a steel bolster."},
            {"Case hardened", "Heat-blued steel in gold, blue and violet patches, ribbed black grip, brass cap."},
            {"Fade", "Red to violet to blue anodised blade, red G10 handle with three screws."},
            {"Crimson web", "Black carbon blade laced with a red web, ribbed grip and a finger ring."},
            {"Tiger tooth", "Gold blade with torn black stripes, blue paracord wrap and a brass ring."},
            {"Sapphire", "Deep blue swirled gem finish with sparkles and a serrated spine."},
            {"Urban masked", "Grey and carbon camo blade, black G10 handle sweeping into a ring."},
            {"Emerald", "Green swirled gem finish, black G10 handle with a green inlay."},
            {"Marble fade", "A red and yellow flame swirl that runs into a blue tip, red paracord grip."},
            {"Ruby", "Deep red swirled gem finish with sparkles, dark red G10 handle."},
            {"Ultraviolet", "Violet-black anodised steel, worn bright at the edge, ribbed purple grip."},
    };

    private final Screen parent;
    private final Chosen chosen;
    private final int[] finishOf;
    private int knife;
    private int hover = -1;
    private int px, py, pw, ph;

    public KnifePickerScreen(Screen parent, int knife, int[] finishes, Chosen chosen) {
        super(Component.literal("Switch knife"));
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

    private void layout() {
        pw = Math.min(width - 16, 500);
        ph = Math.min(height - 16, 290);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    private int gridW() {
        return (pw - 36) * 58 / 100;
    }

    private int tileW() {
        return (gridW() - (COLS - 1) * 4) / COLS;
    }

    private int tileH() {
        int rows = (KnifeArt.HD_COUNT + COLS - 1) / COLS;
        return Math.max(30, (ph - 34 - 30 - (rows - 1) * 4) / rows);
    }

    private int tileX(int t) {
        return px + 12 + (t % COLS) * (tileW() + 4);
    }

    private int tileY(int t) {
        return py + 30 + (t / COLS) * (tileH() + 4);
    }

    private static String shortName(int kind) {
        String n = KnifeArt.allKnives()[kind];
        int dot = n.lastIndexOf('·');
        return dot >= 0 ? n.substring(dot + 1).trim() : n;
    }

    private static String about(int kind) {
        String n = shortName(kind);
        for (String[] a : ABOUT) if (a[0].equalsIgnoreCase(n)) return a[1];
        return "";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1e9f;
        g.fill(0, 0, width, height, 0xB0030610);
        Draw.shadow(g, px, py, pw, ph, 8, 10, 0.5f);
        Draw.rrect(g, px, py, pw, ph, 8, 0xF2070C1A, 0xF2050914);
        Draw.rrectLine(g, px, py, pw, ph, 8, 0xFF234A8A);
        Draw.bold(g, "ZEUS / SWITCH KNIFE", px + 12, py + 11, 0xFFE9F1FF);
        Draw.textRight(g, KnifeArt.HD_COUNT + " HD knives", px + pw - 12, py + 11, 0xFF8EA2CC);

        hover = -1;
        int tw = tileW(), th = tileH();
        for (int t = 0; t < KnifeArt.HD_COUNT; t++) {
            int kind = FIRST + t;
            int x = tileX(t), y = tileY(t);
            boolean on = kind == knife, hot = mouseX >= x && mouseX < x + tw && mouseY >= y && mouseY < y + th;
            if (hot) hover = kind;
            Draw.rrect(g, x, y, tw, th, 5, on ? 0xFF14306A : hot ? 0xFF10224A : 0xFF0A1530);
            Draw.rrectLine(g, x, y, tw, th, 5, on ? 0xFF5CA8FF : hot ? 0xFF3A6ACA : 0xFF1E3A70);
            KnifeArt.showcase(g, kind, 0, x + 3, y + 2, tw - 6, th - 14, time + t * 0.7f, false);
            Draw.textCenter(g, Draw.fit(shortName(kind), tw - 4), x + tw / 2, y + th - 11, on ? 0xFFFFFFFF : 0xFFB8C8E8);
        }

        // big preview of the knife under the mouse, otherwise the one in hand
        int show = hover >= 0 ? hover : knife;
        int rx = px + 12 + gridW() + 12, rw = px + pw - 12 - rx, ry = py + 30;
        int pvh = Math.max(60, ph - 30 - 92);
        Draw.rrect(g, rx, ry, rw, pvh, 7, 0xFF0B1834, 0xFF060C1E);
        Draw.rrectLine(g, rx, ry, rw, pvh, 7, 0xFF234A8A);
        KnifeArt.showcase(g, show, 0, rx + 6, ry + 6, rw - 12, pvh - 12, time, true);
        Draw.bold(g, shortName(show).toUpperCase(Locale.ROOT), rx + 2, ry + pvh + 6, 0xFFE9F1FF);
        if (show == knife) Draw.textRight(g, "in hand", rx + rw, ry + pvh + 6, 0xFF6FE3A0);
        String desc = about(show);
        int ty = ry + pvh + 19;
        for (String line : wrap(desc, rw)) {
            Draw.text(g, line, rx + 2, ty, 0xFF9FB2D8);
            ty += 10;
            if (ty > py + ph - 30) break;
        }

        Draw.text(g, "Click a knife to hold it. Esc closes.", px + 12, py + ph - 18, 0xFF8EA2CC);
        int bx = px + pw - 12 - 56, by = py + ph - 20;
        boolean hotDone = mouseX >= bx && mouseX < bx + 56 && mouseY >= by - 2 && mouseY < by + 16;
        Draw.rrect(g, bx, by - 2, 56, 18, 5, hotDone ? 0xFF1E4C9A : 0xFF14306A);
        Draw.boldCenter(g, "Done", bx + 28, by + 2, 0xFFE9F1FF);
    }

    private static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (Draw.width(next) > width && line.length() > 0) {
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
        layout();
        double x = event.x();
        double y = event.y();
        if (event.button() == 0) {
            int bx = px + pw - 12 - 56, by = py + ph - 20;
            if (x >= bx && x < bx + 56 && y >= by - 2 && y < by + 16) {
                onClose();
                return true;
            }
            int tw = tileW(), th = tileH();
            for (int t = 0; t < KnifeArt.HD_COUNT; t++) {
                int tx = tileX(t), ty = tileY(t);
                if (x >= tx && x < tx + tw && y >= ty && y < ty + th) {
                    knife = FIRST + t;
                    chosen.pick(knife, finishOf[knife]);
                    if (doubleClick) onClose();
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
        int t = knife - FIRST;
        int moved = switch (key) {
            case 262 -> t + 1;          // right
            case 263 -> t - 1;          // left
            case 264 -> t + COLS;       // down
            case 265 -> t - COLS;       // up
            default -> -99;
        };
        if (moved != -99) {
            moved = Math.max(0, Math.min(KnifeArt.HD_COUNT - 1, moved));
            knife = FIRST + moved;
            chosen.pick(knife, finishOf[knife]);
            return true;
        }
        return super.keyPressed(event);
    }
}
