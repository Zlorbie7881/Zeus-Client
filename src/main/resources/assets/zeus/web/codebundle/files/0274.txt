package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.client.ZeusClient;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Clock;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** About Zeus, opened from the round button on the main menu: what Zeus is and what it holds. Information only. */
public final class ZeusAboutScreen extends Screen {
    private static final int TEXT = 0xFFDDE6F7;
    private static final int DIM = 0xFF8EA2CC;
    private static final int LINE = 0xFF234A8A;
    private static final int ACCENT = 0xFF5FA8FF;

    private final Screen parent;
    private final long born = Clock.nanoTime();
    private final long[] strike = new long[2];

    // worked out once: the counts do not change while the screen is open
    private final int modules;
    private final List<Category> cats = new ArrayList<>();
    private final List<Integer> catCounts = new ArrayList<>();
    private List<String> wrapped;
    private int wrappedFor = -1;
    private int[] back;

    public ZeusAboutScreen(Screen parent) {
        super(Component.literal("About Zeus"));
        this.parent = parent;
        int n = 0;
        int[] per = new int[Category.values().length];
        for (Module m : Zeus.all()) {
            per[m.category.ordinal()]++;
            n++;
        }
        modules = n;
        for (Category c : Category.values()) {
            if (per[c.ordinal()] == 0 || c == Category.PROFILES || c == Category.FRIENDS) continue;
            cats.add(c);
            catCounts.add(per[c.ordinal()]);
        }
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

    private static final String[] ABOUT = {
        "Zeus is one Fabric client that brings everything you use every day into one place: a clean panel, a HUD you place yourself, visuals, building and movement tools, automation and base finding.",
        "Every module has a short description and plain settings. Open the panel, flip a switch, and tune it only if you want to.",
        "Zeus runs entirely in your own game. It does not send your data anywhere.",
        "It is updated often. Ideas and bug reports go in the Discord, and most new features start there.",
    };

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = (Clock.nanoTime() - born) / 1e9f;
        ZeusTitleScreen.scene(g, width, height, t, strike);
        g.fill(0, 0, width, height, 0x66030712);
        float in = Math.min(1f, t / 0.35f);
        float ease = 1f - (1f - in) * (1f - in);

        int pw = Math.min(width - 24, 380);
        int ph = Math.min(height - 24, 300);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2 + Math.round((1f - ease) * 10f);
        Draw.glow(g, px, py, pw, ph, 10, Draw.rgba(ACCENT, 0.5f), 14, 0.35f * ease);
        Draw.rrect(g, px, py, pw, ph, 10, Draw.rgba(0xFF0B1426, 0.96f), Draw.rgba(0xFF060B18, 0.96f));
        Draw.rrectLine(g, px, py, pw, ph, 10, LINE);

        // title
        int cx = width / 2;
        int y = py + 14;
        Draw.boldCenterS(g, "ABOUT ZEUS", cx, y, 0xFFFFFFFF, 1.6f);
        y += 18;
        Draw.textCenter(g, "Version " + Zeus.VERSION + "  ·  Fabric 1.21.11", cx, y, DIM);
        y += 16;
        g.fill(px + 20, y, px + pw - 20, y + 1, Draw.rgba(LINE, 0.8f));
        y += 10;

        // three facts as tiles
        String[] big = {String.valueOf(modules), String.valueOf(cats.size()), ZeusClient.menuKeyName()};
        String[] small = {"modules", "categories", "opens the panel"};
        int gap = 8;
        int tw = (pw - 40 - 2 * gap) / 3;
        for (int i = 0; i < 3; i++) {
            int tx = px + 20 + i * (tw + gap);
            Draw.rrect(g, tx, y, tw, 34, 6, 0xFF101C34, 0xFF0C1528);
            Draw.rrectLine(g, tx, y, tw, 34, 6, 0xFF1E3660);
            Draw.boldCenterS(g, Draw.fit(big[i], tw - 8), tx + tw / 2, y + 6, 0xFFFFFFFF, 1f);
            Draw.textCenter(g, small[i], tx + tw / 2, y + 20, DIM);
        }
        y += 44;

        // category chips
        int chipX = px + 20;
        int chipY = y;
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            String s = c.label + " " + catCounts.get(i);
            int w = Draw.width(s) + 16;
            if (chipX + w > px + pw - 20) {
                chipX = px + 20;
                chipY += 16;
            }
            Draw.rrect(g, chipX, chipY, w, 13, 6, Draw.rgba(c.color, 0.16f));
            g.fill(chipX + 5, chipY + 5, chipX + 8, chipY + 8, c.color);
            Draw.text(g, s, chipX + 11, chipY + 3, TEXT);
            chipX += w + 4;
        }
        y = chipY + 22;

        // a few plain paragraphs
        int textW = pw - 40;
        if (wrappedFor != textW) {
            wrapped = new ArrayList<>();
            for (String para : ABOUT) {
                wrapped.addAll(wrap(para, textW));
                wrapped.add("");
            }
            wrappedFor = textW;
        }
        int bh = 18;
        int by = py + ph - bh - 12;
        for (String line : wrapped) {
            if (y + 10 > by - 4) break;
            if (line.isEmpty()) {
                y += 4;
                continue;
            }
            Draw.text(g, line, px + 20, y, TEXT);
            y += 10;
        }

        // back
        int bw = 90;
        int bx = cx - bw / 2;
        back = new int[] {bx, by, bw, bh};
        boolean hot = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        Draw.rrect(g, bx, by, bw, bh, 5, hot ? 0xFF15386E : 0xFF0D1F40);
        Draw.rrectLine(g, bx, by, bw, bh, 5, hot ? ACCENT : LINE);
        Draw.textCenter(g, "Back", cx, by + 5, 0xFFFFFFFF);

        float fl = ZeusTitleScreen.strike(g, width, height, t, strike);
        if (fl > 0.01f) g.fill(0, 0, width, height, Draw.rgba(0xFFCFE4FF, 0.10f * fl));
        if (in < 1f) g.fill(0, 0, width, height, Draw.rgba(0xFF000000, 1f - ease));
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
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
        if (event.button() == 0 && back != null && event.x() >= back[0] && event.x() < back[0] + back[2] && event.y() >= back[1] && event.y() < back[1] + back[3]) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
