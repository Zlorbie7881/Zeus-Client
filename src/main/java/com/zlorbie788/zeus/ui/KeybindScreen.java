package com.zlorbie788.zeus.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.zlorbie788.zeus.client.ZeusClient;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class KeybindScreen extends Screen {
    private static final int ROW = 16;
    private static final int CHIP_W = 96;
    private static final int BG = 0xF2101826;
    private static final int EDGE = 0xFF2B4A78;
    private static final int TEXT = 0xFFE6EDFA;
    private static final int DIM = 0xFF8FA0BE;
    private static final int WARN = Accent.WARN;

    private final Screen parent;
    private final List<Module> all = new ArrayList<>();
    private EditBox search;
    private boolean boundOnly;
    private Module listening;
    private float scroll;
    private int px, py, pw, ph, listY, listH;
    private final List<int[]> chips = new ArrayList<>();
    private final List<Module> chipModules = new ArrayList<>();
    private int[] boundBtn = new int[4];
    private List<Module> shown = new ArrayList<>();

    public KeybindScreen(Screen parent) {
        super(Component.literal("Zeus keybinds"));
        this.parent = parent;
        for (Module m : Zeus.all()) if (m.toggleable() && m.key != null) all.add(m);
        all.sort(Comparator.comparingInt((Module m) -> m.category.ordinal()).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void init() {
        if (search == null) {
            search = new EditBox(minecraft.font, 0, 0, 100, 12, Component.empty());
            search.setMaxLength(40);
            search.setBordered(false);
            search.setHint(Component.literal("Search modules"));
            search.setResponder(s -> scroll = 0);
        }
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0050A14);
    }

    private static boolean bound(Module m) {
        return m.key != null && !m.key.isUnbound();
    }

    private String keyName(KeyMapping k) {
        return k.isUnbound() ? "None" : k.getTranslatedKeyMessage().getString();
    }

    private String sharedWith(Module m) {
        if (!bound(m)) return null;
        String name = keyName(m.key);
        if (ZeusClient.OPEN != null && !ZeusClient.OPEN.isUnbound() && keyName(ZeusClient.OPEN).equals(name)) return "the Zeus menu";
        for (Module o : all) {
            if (o != m && bound(o) && keyName(o.key).equals(name)) return o.name;
        }
        return null;
    }

    private void refilter() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (Module m : all) {
            if (boundOnly && !bound(m)) continue;
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q) && !m.id.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(m);
        }
    }

    private float maxScroll() {
        return Math.max(0, shown.size() * ROW - listH);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        pw = Math.min(420, width - 16);
        ph = Math.min(360, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        Draw.rrect(g, px, py, pw, ph, 5, BG);
        Draw.rrectLine(g, px, py, pw, ph, 5, EDGE);
        int bound = 0;
        for (Module m : all) if (bound(m)) bound++;
        Draw.text(g, "Keybinds", px + 10, py + 9, TEXT);
        String count = bound + " of " + all.size() + " bound";
        Draw.text(g, count, px + pw - 10 - Draw.width(count), py + 9, DIM);

        int sy = py + 26;
        Draw.rrect(g, px + 8, sy, pw - 16 - 78, 16, 3, 0xFF0A1226);
        Draw.rrectLine(g, px + 8, sy, pw - 16 - 78, 16, 3, search.isFocused() ? 0xFF3C92FF : 0xFF263860);
        search.setX(px + 14);
        search.setY(sy + 4);
        search.setWidth(pw - 28 - 78);
        search.render(g, mx, my, partialTick);
        int bx = px + pw - 8 - 72;
        boundBtn = new int[] {bx, sy, 72, 16};
        boolean bh = mx >= bx && mx < bx + 72 && my >= sy && my < sy + 16;
        Draw.rrect(g, bx, sy, 72, 16, 3, boundOnly ? 0xFF10306E : bh ? 0xFF0F1B3C : 0xFF0A1226);
        Draw.rrectLine(g, bx, sy, 72, 16, 3, boundOnly ? 0xFF3C92FF : 0xFF263860);
        String bt = "Bound only";
        Draw.text(g, bt, bx + (72 - Draw.width(bt)) / 2, sy + 4, boundOnly ? 0xFFFFFFFF : 0xFFC3D2F2);

        refilter();
        listY = sy + 22;
        listH = ph - (listY - py) - 22;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        chips.clear();
        chipModules.clear();
        g.enableScissor(px + 4, listY, px + pw - 4, listY + listH);
        int first = (int) (scroll / ROW);
        float off = scroll - first * ROW;
        for (int i = first; i < shown.size(); i++) {
            int y = listY + (i - first) * ROW - Math.round(off);
            if (y >= listY + listH) break;
            Module m = shown.get(i);
            boolean rowHot = mx >= px + 8 && mx < px + pw - 8 && my >= y && my < y + ROW && my >= listY && my < listY + listH;
            if (rowHot) g.fill(px + 8, y, px + pw - 8, y + ROW - 1, 0x14FFFFFF);
            g.fill(px + 8, y + 2, px + 10, y + ROW - 3, Draw.rgba(m.category.color(), 0.9f));
            Draw.text(g, Draw.fit(m.name, pw - 40 - CHIP_W - 70), px + 16, y + 4, m.enabled() ? 0xFFFFFFFF : TEXT);
            String cat = m.category.label;
            Draw.text(g, cat, px + pw - 16 - CHIP_W - 6 - Draw.width(cat), y + 4, 0xFF5E7094);
            int cx = px + pw - 12 - CHIP_W;
            boolean wait = listening == m;
            boolean chipHot = mx >= cx && mx < cx + CHIP_W && my >= y + 1 && my < y + ROW - 1 && my >= listY && my < listY + listH;
            String shared = wait ? null : sharedWith(m);
            Draw.rrect(g, cx, y + 1, CHIP_W, ROW - 2, 3, wait ? 0xFF10306E : chipHot ? 0xFF0F1B3C : 0xFF0A1128);
            Draw.rrectLine(g, cx, y + 1, CHIP_W, ROW - 2, 3, wait ? 0xFF3C92FF : shared != null ? WARN : chipHot ? 0xFF3A5AA0 : 0xFF263860);
            String t = wait ? "Press a key..." : keyName(m.key) + (shared != null ? " (shared)" : "");
            Draw.text(g, Draw.fit(t, CHIP_W - 8), cx + 5, y + 4, wait ? 0xFFFFFFFF : bound(m) ? (shared != null ? WARN : 0xFFFFFFFF) : 0xFF7F93BD);
            chips.add(new int[] {cx, y + 1, CHIP_W, ROW - 2});
            chipModules.add(m);
        }
        g.disableScissor();
        if (shown.isEmpty()) Draw.text(g, "No module matches.", px + 16, listY + 6, DIM);
        if (maxScroll() > 0) {
            int bh2 = Math.max(12, Math.round(listH * (listH / (float) (shown.size() * ROW))));
            int by = listY + Math.round((listH - bh2) * (scroll / maxScroll()));
            g.fill(px + pw - 6, by, px + pw - 4, by + bh2, 0x66FFFFFF);
        }
        String hint = listening != null ? "Press a key for " + listening.name + ". Backspace clears it, Escape cancels." : "Click a key box, then press a key. Right click clears. Escape closes.";
        Draw.text(g, Draw.fit(hint, pw - 20), px + 10, py + ph - 14, DIM);
    }

    private void setKey(KeyMapping k, InputConstants.Key key) {
        k.setKey(key);
        KeyMapping.resetMapping();
        minecraft.options.save();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        int button = event.button();
        if (listening != null) {
            boolean onChip = false;
            for (int[] c : chips) if (x >= c[0] && x < c[0] + c[2] && y >= c[1] && y < c[1] + c[3]) onChip = true;
            if (!onChip) listening = null;
        }
        if (button <= 1 && x >= boundBtn[0] && x < boundBtn[0] + boundBtn[2] && y >= boundBtn[1] && y < boundBtn[1] + boundBtn[3]) {
            boundOnly = !boundOnly;
            scroll = 0;
            return true;
        }
        if (y >= listY && y < listY + listH) {
            for (int i = 0; i < chips.size(); i++) {
                int[] c = chips.get(i);
                if (x >= c[0] && x < c[0] + c[2] && y >= c[1] && y < c[1] + c[3]) {
                    Module m = chipModules.get(i);
                    if (button == 1) {
                        setKey(m.key, InputConstants.UNKNOWN);
                        listening = null;
                    } else if (button == 0) {
                        listening = m;
                        search.setFocused(false);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(x, y, scrollX, scrollY);
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (float) scrollY * ROW * 2));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listening = null;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) setKey(listening.key, InputConstants.UNKNOWN);
            else setKey(listening.key, InputConstants.Type.KEYSYM.getOrCreate(key));
            listening = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!search.getValue().isEmpty()) {
                search.setValue("");
                return true;
            }
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Math.min(maxScroll(), scroll + listH);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) {
            scroll = Math.max(0, scroll - listH);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Zeus.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
