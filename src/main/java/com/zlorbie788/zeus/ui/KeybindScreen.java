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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keybinds as a full-size (100%) virtual keyboard. Every key shows what is linked to it; click a key (or press it on
 * the real keyboard) and a searchable list of every module opens, where one click links or unlinks a module.
 * Several modules can share one key: they all toggle together.
 */
public final class KeybindScreen extends Screen {
    private static final int TEXT = 0xFFE6EDFA;
    private static final int DIM = 0xFF8FA0BE;
    private static final int FAINT = 0xFF5E7094;
    private static final int BLUE = 0xFF3C92FF;
    private static final int GOLD = 0xFFF5C04A;
    private static final int OK = Accent.OK;
    private static final int WARN = Accent.WARN;
    private static final int CAP = 0xFF141C2E;
    private static final int CAP_HOT = 0xFF1C2742;
    private static final int CAP_EDGE = 0xFF070B14;
    private static final float KB_W = 22.5f, KB_H = 6.25f;
    private static final int ROW_H = 18;

    /** One key of the virtual keyboard, in key units. */
    private record Cap(String label, InputConstants.Key key, float x, float y, float w, float h, boolean bindable) {
    }

    private static final List<Cap> CAPS = layout();

    private final Screen parent;
    private final List<Module> all = new ArrayList<>();
    private final Map<String, List<Module>> byKey = new HashMap<>();
    private final Map<String, List<String>> actions = new HashMap<>();
    private boolean dirty = true;
    private EditBox search;
    private Cap selected;
    private Module listening;
    private int filter;              // 0 all, 1 on this key / bound, 2 free
    private int scroll;
    private float t;
    private float pressAnim;
    // layout of the frame
    private float u;
    private int kx, ky, panelX, panelY, panelW, panelH, listY, listH, cols, colW;
    private final List<int[]> rowBoxes = new ArrayList<>();
    private final List<Module> rowModules = new ArrayList<>();
    private final int[][] filterBtn = new int[3][];
    private int[] clearBtn, doneBtn;
    private List<Module> shown = new ArrayList<>();

    public KeybindScreen(Screen parent) {
        super(Component.literal("Zeus keybinds"));
        this.parent = parent;
        for (Module m : Zeus.all()) if (m.toggleable() && m.key != null) all.add(m);
        all.sort(Comparator.comparingInt((Module m) -> m.category.ordinal()).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
    }

    // ------------------------------------------------------------------ the keyboard
    private static InputConstants.Key k(int glfw) {
        return InputConstants.Type.KEYSYM.getOrCreate(glfw);
    }

    private static List<Cap> layout() {
        List<Cap> l = new ArrayList<>();
        // function row
        add(l, "Esc", GLFW.GLFW_KEY_ESCAPE, 0, 0, 1, false);
        for (int i = 0; i < 12; i++) {
            float x = 2 + i + (i / 4) * 0.5f;
            add(l, "F" + (i + 1), GLFW.GLFW_KEY_F1 + i, x, 0, 1, true);
        }
        add(l, "PrtSc", GLFW.GLFW_KEY_PRINT_SCREEN, 15.25f, 0, 1, true);
        add(l, "ScrLk", GLFW.GLFW_KEY_SCROLL_LOCK, 16.25f, 0, 1, true);
        add(l, "Pause", GLFW.GLFW_KEY_PAUSE, 17.25f, 0, 1, true);
        // mouse side buttons sit where a 100% board has its LEDs
        String[] ml = {"M3", "M4", "M5"};
        for (int i = 0; i < 3; i++)
            l.add(new Cap(ml[i], InputConstants.Type.MOUSE.getOrCreate(2 + i), 18.5f + i * 4f / 3f, 0, 4f / 3f, 1, true));
        float r1 = 1.25f, r2 = 2.25f, r3 = 3.25f, r4 = 4.25f, r5 = 5.25f;
        // number row
        row(l, r1, 0, new String[] {"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "="},
                new int[] {GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6,
                        GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL});
        add(l, "Backspace", GLFW.GLFW_KEY_BACKSPACE, 13, r1, 2, true);
        // top letter row
        add(l, "Tab", GLFW.GLFW_KEY_TAB, 0, r2, 1.5f, true);
        row(l, r2, 1.5f, "QWERTYUIOP".chars().mapToObj(c -> String.valueOf((char) c)).toArray(String[]::new),
                "QWERTYUIOP".chars().map(c -> GLFW.GLFW_KEY_A + (c - 'A')).toArray());
        add(l, "[", GLFW.GLFW_KEY_LEFT_BRACKET, 11.5f, r2, 1, true);
        add(l, "]", GLFW.GLFW_KEY_RIGHT_BRACKET, 12.5f, r2, 1, true);
        add(l, "\\", GLFW.GLFW_KEY_BACKSLASH, 13.5f, r2, 1.5f, true);
        // home row
        add(l, "Caps", GLFW.GLFW_KEY_CAPS_LOCK, 0, r3, 1.75f, true);
        row(l, r3, 1.75f, "ASDFGHJKL".chars().mapToObj(c -> String.valueOf((char) c)).toArray(String[]::new),
                "ASDFGHJKL".chars().map(c -> GLFW.GLFW_KEY_A + (c - 'A')).toArray());
        add(l, ";", GLFW.GLFW_KEY_SEMICOLON, 10.75f, r3, 1, true);
        add(l, "'", GLFW.GLFW_KEY_APOSTROPHE, 11.75f, r3, 1, true);
        add(l, "Enter", GLFW.GLFW_KEY_ENTER, 12.75f, r3, 2.25f, true);
        // bottom letter row
        add(l, "Shift", GLFW.GLFW_KEY_LEFT_SHIFT, 0, r4, 2.25f, true);
        row(l, r4, 2.25f, "ZXCVBNM".chars().mapToObj(c -> String.valueOf((char) c)).toArray(String[]::new),
                "ZXCVBNM".chars().map(c -> GLFW.GLFW_KEY_A + (c - 'A')).toArray());
        add(l, ",", GLFW.GLFW_KEY_COMMA, 9.25f, r4, 1, true);
        add(l, ".", GLFW.GLFW_KEY_PERIOD, 10.25f, r4, 1, true);
        add(l, "/", GLFW.GLFW_KEY_SLASH, 11.25f, r4, 1, true);
        add(l, "Shift", GLFW.GLFW_KEY_RIGHT_SHIFT, 12.25f, r4, 2.75f, true);
        // space row
        float x = 0;
        Object[][] sp = {{"Ctrl", GLFW.GLFW_KEY_LEFT_CONTROL, 1.25f}, {"Win", GLFW.GLFW_KEY_LEFT_SUPER, 1.25f}, {"Alt", GLFW.GLFW_KEY_LEFT_ALT, 1.25f},
                {"", GLFW.GLFW_KEY_SPACE, 6.25f}, {"Alt", GLFW.GLFW_KEY_RIGHT_ALT, 1.25f}, {"Win", GLFW.GLFW_KEY_RIGHT_SUPER, 1.25f},
                {"Menu", GLFW.GLFW_KEY_MENU, 1.25f}, {"Ctrl", GLFW.GLFW_KEY_RIGHT_CONTROL, 1.25f}};
        for (Object[] o : sp) {
            add(l, (String) o[0], (Integer) o[1], x, r5, (Float) o[2], true);
            x += (Float) o[2];
        }
        // navigation cluster
        add(l, "Ins", GLFW.GLFW_KEY_INSERT, 15.25f, r1, 1, true);
        add(l, "Home", GLFW.GLFW_KEY_HOME, 16.25f, r1, 1, true);
        add(l, "PgUp", GLFW.GLFW_KEY_PAGE_UP, 17.25f, r1, 1, true);
        add(l, "Del", GLFW.GLFW_KEY_DELETE, 15.25f, r2, 1, true);
        add(l, "End", GLFW.GLFW_KEY_END, 16.25f, r2, 1, true);
        add(l, "PgDn", GLFW.GLFW_KEY_PAGE_DOWN, 17.25f, r2, 1, true);
        add(l, "↑", GLFW.GLFW_KEY_UP, 16.25f, r4, 1, true);
        add(l, "←", GLFW.GLFW_KEY_LEFT, 15.25f, r5, 1, true);
        add(l, "↓", GLFW.GLFW_KEY_DOWN, 16.25f, r5, 1, true);
        add(l, "→", GLFW.GLFW_KEY_RIGHT, 17.25f, r5, 1, true);
        // numpad
        float n = 18.5f;
        add(l, "Num", GLFW.GLFW_KEY_NUM_LOCK, n, r1, 1, true);
        add(l, "/", GLFW.GLFW_KEY_KP_DIVIDE, n + 1, r1, 1, true);
        add(l, "*", GLFW.GLFW_KEY_KP_MULTIPLY, n + 2, r1, 1, true);
        add(l, "-", GLFW.GLFW_KEY_KP_SUBTRACT, n + 3, r1, 1, true);
        int[][] pad = {{7, 8, 9}, {4, 5, 6}, {1, 2, 3}};
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                add(l, String.valueOf(pad[r][c]), GLFW.GLFW_KEY_KP_0 + pad[r][c], n + c, r2 + r, 1, true);
        l.add(new Cap("+", k(GLFW.GLFW_KEY_KP_ADD), n + 3, r2, 1, 2, true));
        l.add(new Cap("Enter", k(GLFW.GLFW_KEY_KP_ENTER), n + 3, r4, 1, 2, true));
        add(l, "0", GLFW.GLFW_KEY_KP_0, n, r5, 2, true);
        add(l, ".", GLFW.GLFW_KEY_KP_DECIMAL, n + 2, r5, 1, true);
        return l;
    }

    private static void add(List<Cap> l, String label, int glfw, float x, float y, float w, boolean bindable) {
        l.add(new Cap(label, k(glfw), x, y, w, 1, bindable));
    }

    private static void row(List<Cap> l, float y, float x0, String[] labels, int[] codes) {
        for (int i = 0; i < labels.length; i++) add(l, labels[i], codes[i], x0 + i, y, 1, true);
    }

    // ------------------------------------------------------------------ data
    private static boolean bound(Module m) {
        return m.key != null && !m.key.isUnbound();
    }

    private void refresh() {
        if (!dirty) return;
        dirty = false;
        byKey.clear();
        actions.clear();
        for (Module m : all) if (bound(m)) byKey.computeIfAbsent(m.key.saveString(), x -> new ArrayList<>()).add(m);
        if (minecraft == null) return;
        for (KeyMapping km : minecraft.options.keyMappings) {
            if (km.isUnbound() || km.getName().startsWith("key.zeus.module.")) continue;
            String name = Component.translatable(km.getName()).getString();
            if (km.getName().startsWith("key.zeus.") && !name.startsWith("Zeus")) name = "Zeus: " + name;
            actions.computeIfAbsent(km.saveString(), x -> new ArrayList<>()).add(name);
        }
    }

    private List<Module> on(Cap c) {
        return byKey.getOrDefault(c.key.getName(), List.of());
    }

    private List<String> actionsOn(Cap c) {
        return actions.getOrDefault(c.key.getName(), List.of());
    }

    private Cap capFor(InputConstants.Key key) {
        for (Cap c : CAPS) if (c.key.equals(key)) return c;
        return null;
    }

    private String keyLabel(Module m) {
        if (!bound(m)) return "";
        Cap c = capFor(InputConstants.getKey(m.key.saveString()));
        if (c != null && !c.label.isEmpty()) return c.label;
        return m.key.getTranslatedKeyMessage().getString();
    }

    private void link(Module m, InputConstants.Key key) {
        m.key.setKey(key);
        KeyMapping.resetMapping();
        minecraft.options.save();
        dirty = true;
        pressAnim = 1f;
    }

    private boolean matches(Module m, String q) {
        return q.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(q) || m.id.toLowerCase(Locale.ROOT).contains(q)
                || m.category.label.toLowerCase(Locale.ROOT).contains(q);
    }

    private void refilter() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        String sel = selected == null ? null : selected.key.getName();
        for (Module m : all) {
            if (!matches(m, q)) continue;
            boolean here = sel != null && bound(m) && m.key.saveString().equals(sel);
            if (filter == 1 && (sel != null ? !here : !bound(m))) continue;
            if (filter == 2 && bound(m)) continue;
            shown.add(m);
        }
        if (sel != null) shown.sort(Comparator.comparing((Module m) -> !(bound(m) && m.key.saveString().equals(sel))));
    }

    // ------------------------------------------------------------------ screen
    @Override
    protected void init() {
        if (search == null) {
            search = new EditBox(minecraft.font, 0, 0, 100, 12, Component.empty());
            search.setMaxLength(40);
            search.setBordered(false);
            search.setHint(Component.literal("Search modules..."));
            search.setResponder(s -> scroll = 0);
        }
        addRenderableWidget(search);
        dirty = true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xC8050A14);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        float dt = Math.min(0.1f, minecraft.getDeltaTracker().getRealtimeDeltaTicks() / 20f);
        t += dt;
        pressAnim = Math.max(0, pressAnim - dt * 4);
        refresh();
        renderBackground(g, mx, my, partialTick);

        // sizes: the keyboard takes the top, the module list the rest
        // on short screens the keyboard gets nearly all the height, and the list slides up over it when needed
        boolean compact = height < 360;
        u = Math.min(40f, Math.min((width - 24) / KB_W, (compact ? height - 80f : height * 0.5f) / KB_H));
        int kw = Math.round(KB_W * u), kh = Math.round(KB_H * u);
        kx = (width - kw) / 2;
        ky = 30;
        panelY = ky + kh + 14;
        if (compact && (selected != null || listening != null || search.isFocused() || !search.getValue().isEmpty()))
            panelY = Math.min(panelY, Math.round(height * 0.38f));
        int bound = 0;
        for (Module m : all) if (bound(m)) bound++;
        int shared = 0;
        for (List<Module> l : byKey.values()) if (l.size() > 1) shared++;
        Draw.text(g, "Keybinds", kx, 10, TEXT);
        String stats = bound + " of " + all.size() + " modules bound" + (shared > 0 ? "  ·  " + shared + (shared == 1 ? " key links" : " keys link") + " several" : "");
        Draw.textRight(g, stats, kx + kw, 10, DIM);

        // keyboard case
        Draw.rrect(g, kx - 8, ky - 6, kw + 16, kh + 12, 8, 0xF00B1120);
        Draw.rrectLine(g, kx - 8, ky - 6, kw + 16, kh + 12, 8, 0xFF22324F);
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        Cap hover = null;
        for (Cap c : CAPS) {
            int x = kx + Math.round(c.x * u), y = ky + Math.round(c.y * u);
            int w = Math.round((c.x + c.w) * u) - Math.round(c.x * u), h = Math.round((c.y + c.h) * u) - Math.round(c.y * u);
            boolean hot = mx >= x && mx < x + w && my >= y && my < y + h && my < panelY;
            if (hot) hover = c;
            drawCap(g, c, x + 1, y + 1, w - 2, h - 2, hot, q);
        }

        // panel with the module list
        panelX = kx - 8;
        panelW = kw + 16;
        panelH = height - panelY - 8;
        if (panelY < ky + kh + 14) g.fill(0, panelY - 6, width, height, 0x99050A14);   // overlay: shade the keys behind
        drawPanel(g, mx, my, partialTick, q);

        if (hover != null && listening == null) tooltip(g, hover, mx, my);
    }

    private void drawCap(GuiGraphics g, Cap c, int x, int y, int w, int h, boolean hot, String q) {
        List<Module> mods = on(c);
        List<String> act = actionsOn(c);
        boolean sel = c == selected;
        boolean down = c.key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(minecraft.getWindow(), c.key.getValue());
        boolean menuKey = ZeusClient.OPEN != null && !ZeusClient.OPEN.isUnbound() && ZeusClient.OPEN.saveString().equals(c.key.getName());
        int depth = Math.max(2, Math.round(u * 0.09f));
        int press = down || (sel && pressAnim > 0) ? depth - 1 : 0;
        Draw.rrect(g, x, y, w, h, 3, CAP_EDGE);
        int face = !c.bindable ? 0xFF0E1320 : hot || sel ? CAP_HOT : CAP;
        int fy = y + press, fh = h - depth;
        Draw.rrect(g, x, fy, w, fh, 3, face);
        if (!mods.isEmpty()) {
            int col = mods.get(0).category.color();
            boolean anyOn = false;
            for (Module m : mods) anyOn |= m.enabled();
            Draw.rrect(g, x, fy, w, fh, 3, Draw.rgba(col, anyOn ? 0.42f : 0.26f));
            g.fill(x + 3, fy + fh - 2, x + w - 3, fy + fh - 1, Draw.rgba(col, 0.9f));
        }
        // top highlight for a keycap look
        g.fill(x + 2, fy + 1, x + w - 2, fy + 2, 0x18FFFFFF);
        if (!q.isEmpty() && listening == null) {
            boolean hit = false;
            for (Module m : mods) hit |= matches(m, q);
            if (hit) Draw.rrectLine(g, x - 1, fy - 1, w + 2, fh + 2, 4, Draw.rgba(WARN, 0.55f + 0.4f * (float) Math.sin(t * 6)));
        }
        if (sel) {
            Draw.glow(g, x, fy, w, fh, 3, BLUE, 4, 0.5f);
            Draw.rrectLine(g, x, fy, w, fh, 3, BLUE);
        } else if (listening != null && c.bindable && hot) {
            Draw.rrectLine(g, x, fy, w, fh, 3, BLUE);
        } else if (menuKey) {
            Draw.rrectLine(g, x, fy, w, fh, 3, GOLD);
        }
        float ls = u < 26 ? 0.55f : u < 32 ? 0.65f : 0.75f;
        int lc = !c.bindable ? FAINT : !mods.isEmpty() ? 0xFFFFFFFF : act.isEmpty() && !menuKey ? DIM : 0xFFC9D5EE;
        Draw.textS(g, c.label, x + 3, fy + 3, lc, ls);
        float ms = ls * 0.82f;
        int lineY = fy + fh - Math.round(9 * ms) - 3;
        if (!mods.isEmpty()) {
            Draw.textS(g, fitS(mods.get(0).name, w - 6, ms), x + 3, lineY, 0xFFFFFFFF, ms);
            if (mods.size() > 1) {
                String b = "+" + (mods.size() - 1);
                int bw = Draw.widthS(b, ms) + 4;
                Draw.rrect(g, x + w - bw - 2, fy + 2, bw, Math.round(9 * ms) + 2, 2, 0xFF0A1226);
                Draw.textS(g, b, x + w - bw, fy + 3, WARN, ms);
            }
        } else if (menuKey) {
            Draw.textS(g, fitS("Zeus menu", w - 6, ms), x + 3, lineY, GOLD, ms);
        } else if (!act.isEmpty() && fh > 9 * ls + 9 * ms + 4) {
            Draw.textS(g, fitS(act.get(0), w - 6, ms), x + 3, lineY, FAINT, ms);
        }
    }

    private static String fitS(String s, int max, float sc) {
        if (Draw.widthS(s, sc) <= max) return s;
        String e = "..";
        int n = s.length();
        while (n > 0 && Draw.widthS(s.substring(0, n) + e, sc) > max) n--;
        return n <= 0 ? "" : s.substring(0, n) + e;
    }

    private void tooltip(GuiGraphics g, Cap c, int mx, int my) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        String name = c.label.isEmpty() ? "Space" : c.key.getDisplayName().getString();
        lines.add(name);
        colors.add(TEXT);
        if (!c.bindable) {
            lines.add("Escape always closes menus, so it cannot be bound.");
            colors.add(DIM);
        }
        for (Module m : on(c)) {
            lines.add((m.enabled() ? "● " : "○ ") + m.name);
            colors.add(m.enabled() ? OK : 0xFFC9D5EE);
        }
        for (String a : actionsOn(c)) {
            lines.add("Minecraft: " + a);
            colors.add(FAINT);
        }
        if (on(c).isEmpty() && actionsOn(c).isEmpty() && c.bindable) {
            lines.add("Free. Click to link modules.");
            colors.add(DIM);
        }
        int w = 0;
        for (String l : lines) w = Math.max(w, Draw.width(l));
        int h = lines.size() * 11 + 6;
        int x = Math.min(mx + 10, width - w - 14), y = my + 12;
        if (y + h > height - 4) y = my - h - 4;
        Draw.rrect(g, x, y, w + 10, h, 4, 0xF20A1020);
        Draw.rrectLine(g, x, y, w + 10, h, 4, 0xFF2B4A78);
        for (int i = 0; i < lines.size(); i++) Draw.text(g, lines.get(i), x + 5, y + 4 + i * 11, colors.get(i));
    }

    private boolean in(int[] b, double x, double y) {
        return b != null && x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3];
    }

    private void button(GuiGraphics g, int[] b, String label, boolean on, int mx, int my, int accent) {
        boolean hot = in(b, mx, my);
        Draw.rrect(g, b[0], b[1], b[2], b[3], 3, on ? Draw.mix(0xFF0A1226, accent, 0.35f) : hot ? 0xFF16223E : 0xFF0A1226);
        Draw.rrectLine(g, b[0], b[1], b[2], b[3], 3, on ? accent : hot ? 0xFF3A5AA0 : 0xFF263860);
        Draw.textCenter(g, label, b[0] + b[2] / 2, b[1] + (b[3] - 8) / 2, on ? 0xFFFFFFFF : 0xFFC3D2F2);
    }

    private void drawPanel(GuiGraphics g, int mx, int my, float pt, String q) {
        Draw.rrect(g, panelX, panelY, panelW, panelH, 6, 0xF2101826);
        Draw.rrectLine(g, panelX, panelY, panelW, panelH, 6, 0xFF2B4A78);
        int x0 = panelX + 10, y0 = panelY + 8;
        // header
        clearBtn = doneBtn = null;
        if (listening != null) {
            Draw.text(g, "Click a key above, or press one, to link " + listening.name + ".", x0, y0 + 2, BLUE);
            Draw.textRight(g, "Escape cancels", panelX + panelW - 10, y0 + 2, DIM);
        } else if (selected != null) {
            String nm = selected.label.isEmpty() ? "Space" : selected.key.getDisplayName().getString();
            int cw = Draw.width(nm) + 12;
            Draw.rrect(g, x0, y0 - 2, cw, 14, 3, 0xFF1C2742);
            Draw.rrectLine(g, x0, y0 - 2, cw, 14, 3, BLUE);
            Draw.text(g, nm, x0 + 6, y0 + 1, 0xFFFFFFFF);
            int n = on(selected).size();
            String sub = n == 0 ? "Nothing linked yet. Click modules below to link them." : n + (n == 1 ? " module" : " modules") + " linked" + (n > 1 ? ", all toggle together" : "");
            List<String> act = actionsOn(selected);
            if (!act.isEmpty()) sub += "  ·  Minecraft uses it for " + String.join(", ", act);
            doneBtn = new int[] {panelX + panelW - 10 - 50, y0 - 3, 50, 15};
            clearBtn = new int[] {doneBtn[0] - 6 - 70, y0 - 3, 70, 15};
            Draw.text(g, Draw.fit(sub, clearBtn[0] - x0 - cw - 18), x0 + cw + 8, y0 + 1, !act.isEmpty() && n > 0 ? WARN : DIM);
            if (n > 0) button(g, clearBtn, "Unlink all", false, mx, my, Accent.BAD);
            else clearBtn = null;
            button(g, doneBtn, "Done", false, mx, my, BLUE);
        } else {
            Draw.text(g, "Click any key, or press one on your keyboard. Or pick a module below, then a key.", x0, y0 + 2, DIM);
        }
        // search and filters
        int sy = y0 + 18;
        int fw = 62;
        int sw = panelW - 20 - 3 * (fw + 4);
        Draw.rrect(g, x0, sy, sw, 16, 3, 0xFF0A1226);
        Draw.rrectLine(g, x0, sy, sw, 16, 3, search.isFocused() ? BLUE : 0xFF263860);
        search.setX(x0 + 6);
        search.setY(sy + 4);
        search.setWidth(sw - 12);
        search.render(g, mx, my, pt);
        String[] fl = {"All", selected != null ? "Linked" : "Bound", "Free"};
        for (int i = 0; i < 3; i++) {
            filterBtn[i] = new int[] {x0 + sw + 4 + i * (fw + 4), sy, fw, 16};
            button(g, filterBtn[i], fl[i], filter == i, mx, my, BLUE);
        }
        // module grid
        refilter();
        listY = sy + 22;
        listH = panelY + panelH - 6 - listY;
        cols = Math.max(1, (panelW - 20) / 230);
        colW = (panelW - 20 - (cols - 1) * 6) / cols;
        int rowsVisible = Math.max(1, listH / ROW_H);
        int totalRows = (shown.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(Math.max(0, totalRows - rowsVisible), scroll));
        rowBoxes.clear();
        rowModules.clear();
        String sel = selected == null ? null : selected.key.getName();
        g.enableScissor(panelX + 2, listY, panelX + panelW - 2, listY + listH);
        for (int r = 0; r < rowsVisible + 1; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (scroll + r) * cols + c;
                if (i >= shown.size()) break;
                Module m = shown.get(i);
                int rx = x0 + c * (colW + 6), ry = listY + r * ROW_H;
                if (ry >= listY + listH) break;
                boolean here = sel != null && bound(m) && m.key.saveString().equals(sel);
                boolean hot = mx >= rx && mx < rx + colW && my >= ry && my < ry + ROW_H - 2 && my >= listY && my < listY + listH;
                int bg = here ? Draw.rgba(OK, 0.13f) : listening == m ? Draw.rgba(BLUE, 0.25f) : hot ? 0x16FFFFFF : 0x08FFFFFF;
                Draw.rrect(g, rx, ry, colW, ROW_H - 2, 3, bg);
                if (here) Draw.rrectLine(g, rx, ry, colW, ROW_H - 2, 3, Draw.rgba(OK, 0.6f));
                g.fill(rx + 2, ry + 3, rx + 4, ry + ROW_H - 5, Draw.rgba(m.category.color(), 0.95f));
                String tag;
                int tc;
                if (here) {
                    tag = "✔ linked";
                    tc = OK;
                } else if (bound(m)) {
                    tag = keyLabel(m);
                    tc = 0xFFC9D5EE;
                } else {
                    tag = selected != null ? "+ link" : "free";
                    tc = selected != null && hot ? BLUE : FAINT;
                }
                int tw = Draw.width(tag);
                if (bound(m) && !here) {
                    Draw.rrect(g, rx + colW - tw - 12, ry + 2, tw + 8, ROW_H - 6, 2, 0xFF0A1226);
                    Draw.rrectLine(g, rx + colW - tw - 12, ry + 2, tw + 8, ROW_H - 6, 2, 0xFF263860);
                }
                Draw.text(g, tag, rx + colW - tw - 8, ry + 4, tc);
                Draw.text(g, Draw.fit(m.name, colW - tw - 30), rx + 9, ry + 4, m.enabled() ? 0xFFFFFFFF : TEXT);
                rowBoxes.add(new int[] {rx, ry, colW, ROW_H - 2});
                rowModules.add(m);
            }
        }
        g.disableScissor();
        if (shown.isEmpty()) Draw.text(g, filter == 1 && selected != null ? "Nothing linked to this key yet." : "No module matches.", x0, listY + 4, DIM);
        if (totalRows > rowsVisible) {
            int bh = Math.max(12, Math.round(listH * (rowsVisible / (float) totalRows)));
            int by = listY + Math.round((listH - bh) * (scroll / (float) (totalRows - rowsVisible)));
            g.fill(panelX + panelW - 6, by, panelX + panelW - 4, by + bh, 0x66FFFFFF);
        }
    }

    // ------------------------------------------------------------------ input
    private Cap capAt(double mx, double my) {
        for (Cap c : CAPS) {
            int x = kx + Math.round(c.x * u), y = ky + Math.round(c.y * u);
            int w = Math.round((c.x + c.w) * u) - Math.round(c.x * u), h = Math.round((c.y + c.h) * u) - Math.round(c.y * u);
            if (mx >= x && mx < x + w && my >= y && my < y + h) return c;
        }
        return null;
    }

    private void select(Cap c) {
        if (c == null || !c.bindable) return;
        if (listening != null) {
            link(listening, c.key);
            listening = null;
        }
        selected = c;
        pressAnim = 1f;
        scroll = 0;
        if (filter == 2) filter = 0;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        int button = event.button();
        Cap c = y < panelY ? capAt(x, y) : null;
        if (c != null) {
            search.setFocused(false);
            if (button == 1 && c.bindable) {       // right click a key: unlink everything on it
                for (Module m : new ArrayList<>(on(c))) link(m, InputConstants.UNKNOWN);
                return true;
            }
            if (button == 0) {
                if (c == selected && listening == null) selected = null;
                else select(c);
            }
            return true;
        }
        if (button == 0 && in(doneBtn, x, y)) {
            selected = null;
            return true;
        }
        if (button == 0 && in(clearBtn, x, y) && selected != null) {
            for (Module m : new ArrayList<>(on(selected))) link(m, InputConstants.UNKNOWN);
            return true;
        }
        for (int i = 0; i < 3; i++) {
            if (button == 0 && in(filterBtn[i], x, y)) {
                filter = filter == i ? 0 : i;
                scroll = 0;
                return true;
            }
        }
        if (y >= listY && y < listY + listH) {
            for (int i = 0; i < rowBoxes.size(); i++) {
                if (!in(rowBoxes.get(i), x, y)) continue;
                Module m = rowModules.get(i);
                if (button == 1) {
                    link(m, InputConstants.UNKNOWN);
                    if (listening == m) listening = null;
                } else if (button == 0) {
                    if (selected != null) {
                        boolean here = bound(m) && m.key.saveString().equals(selected.key.getName());
                        link(m, here ? InputConstants.UNKNOWN : selected.key);
                    } else {
                        listening = listening == m ? null : m;
                        search.setFocused(false);
                    }
                }
                return true;
            }
        }
        boolean r = super.mouseClicked(event, doubleClick);
        if (!r && listening != null) listening = null;
        return r;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(x, y, scrollX, scrollY);
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 2);
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
            select(capFor(k(key)) != null ? capFor(k(key)) : null);
            if (listening != null) {                // a key the board does not show: still allowed
                link(listening, k(key));
                listening = null;
            }
            return true;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.getValue().isEmpty()) search.setValue("");
                else search.setFocused(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_DOWN) {
                search.setFocused(false);
                return true;
            }
            return super.keyPressed(event);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (selected != null) selected = null;
            else onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            search.setFocused(true);
            setFocused(search);
            return true;
        }
        Cap c = capFor(k(key));
        if (c != null) {
            select(c);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        // typing only goes to the search box once it is focused; otherwise keys select keys
        return search.isFocused() && super.charTyped(event);
    }

    @Override
    public void onClose() {
        Zeus.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
