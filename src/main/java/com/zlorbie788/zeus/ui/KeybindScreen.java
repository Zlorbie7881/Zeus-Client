package com.zlorbie788.zeus.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.zlorbie788.zeus.core.Category;
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

/**
 * A full 100% keyboard you can click. Pick a key, then pick what it does from a searchable list of
 * every module, and the link is made. Keys that already do something show the module and light up,
 * so the whole layout is readable at a glance instead of a list of "None".
 */
public final class KeybindScreen extends Screen {
    private static final int BG = 0xF2101826;
    private static final int EDGE = 0xFF2B4A78;
    private static final int TEXT = 0xFFE6EDFA;
    private static final int DIM = 0xFF8FA0BE;
    private static final int HOT = 0xFFFFD76A;
    private static final int ON = 0xFF5EE67A;

    private final Screen parent;
    private final List<Module> all = new ArrayList<>();
    private final List<Cap> caps = new ArrayList<>();
    private final List<Cap> hit = new ArrayList<>();
    private EditBox search;
    private int picked = -1;
    private float listScroll;
    private List<Module> shown = new ArrayList<>();
    private List<int[]> rows = new ArrayList<>();

    private float unit;
    private int kx, ky, kHeight;
    private int px, py, pw, ph;
    private int listX, listY, listW, listH;

    /** One keycap: GLFW code, label and its place on the 22.5 x 6 unit board. */
    private record Cap(String label, int code, float x, float y, float w, float h) {}

    private static final int ROW = 15;

    public KeybindScreen(Screen parent) {
        super(Component.literal("Zeus keybinds"));
        this.parent = parent;
        for (Module m : Zeus.all()) if (m.toggleable() && m.key != null) all.add(m);
        all.sort(Comparator.comparingInt((Module m) -> m.category.ordinal())
                .thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
        layout();
    }

    // ------------------------------------------------------------------ the board

    private void layout() {
        caps.clear();
        // function row
        caps.add(new Cap("Esc", GLFW.GLFW_KEY_ESCAPE, 0, 0, 1, 1));
        for (int i = 0; i < 12; i++) caps.add(new Cap("F" + (i + 1), GLFW.GLFW_KEY_F1 + i, 1 + i, 0, 1, 1));
        caps.add(new Cap("Prt", GLFW.GLFW_KEY_PRINT_SCREEN, 13, 0, 1, 1));

        // number row
        String[] top = {"`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "="};
        int[] topCode = {GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3,
                GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8,
                GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL};
        for (int i = 0; i < top.length; i++) caps.add(new Cap(top[i], topCode[i], i, 1, 1, 1));
        caps.add(new Cap("Bksp", GLFW.GLFW_KEY_BACKSPACE, 13, 1, 2, 1));

        // qwerty row
        caps.add(new Cap("Tab", GLFW.GLFW_KEY_TAB, 0, 2, 1.5f, 1));
        String[] qwerty = {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"};
        for (int i = 0; i < qwerty.length; i++) {
            caps.add(new Cap(qwerty[i], codeOf(qwerty[i]), 1.5f + i, 2, 1, 1));
        }
        caps.add(new Cap("[", GLFW.GLFW_KEY_LEFT_BRACKET, 11.5f, 2, 1, 1));
        caps.add(new Cap("]", GLFW.GLFW_KEY_RIGHT_BRACKET, 12.5f, 2, 1, 1));
        caps.add(new Cap("\\", GLFW.GLFW_KEY_BACKSLASH, 13.5f, 2, 1.5f, 1));

        // home row
        caps.add(new Cap("Caps", GLFW.GLFW_KEY_CAPS_LOCK, 0, 3, 1.75f, 1));
        String[] home = {"A", "S", "D", "F", "G", "H", "J", "K", "L"};
        for (int i = 0; i < home.length; i++) caps.add(new Cap(home[i], codeOf(home[i]), 1.75f + i, 3, 1, 1));
        caps.add(new Cap(";", GLFW.GLFW_KEY_SEMICOLON, 10.75f, 3, 1, 1));
        caps.add(new Cap("'", GLFW.GLFW_KEY_APOSTROPHE, 11.75f, 3, 1, 1));
        caps.add(new Cap("Enter", GLFW.GLFW_KEY_ENTER, 12.75f, 3, 2.25f, 1));

        // bottom letters
        caps.add(new Cap("Shift", GLFW.GLFW_KEY_LEFT_SHIFT, 0, 4, 2.25f, 1));
        String[] lower = {"Z", "X", "C", "V", "B", "N", "M"};
        for (int i = 0; i < lower.length; i++) caps.add(new Cap(lower[i], codeOf(lower[i]), 2.25f + i, 4, 1, 1));
        caps.add(new Cap(",", GLFW.GLFW_KEY_COMMA, 9.25f, 4, 1, 1));
        caps.add(new Cap(".", GLFW.GLFW_KEY_PERIOD, 10.25f, 4, 1, 1));
        caps.add(new Cap("/", GLFW.GLFW_KEY_SLASH, 11.25f, 4, 1, 1));
        caps.add(new Cap("Shift", GLFW.GLFW_KEY_RIGHT_SHIFT, 12.25f, 4, 2.75f, 1));

        // modifiers
        caps.add(new Cap("Ctrl", GLFW.GLFW_KEY_LEFT_CONTROL, 0, 5, 1.25f, 1));
        caps.add(new Cap("Win", GLFW.GLFW_KEY_LEFT_SUPER, 1.25f, 5, 1.25f, 1));
        caps.add(new Cap("Alt", GLFW.GLFW_KEY_LEFT_ALT, 2.5f, 5, 1.25f, 1));
        caps.add(new Cap("Space", GLFW.GLFW_KEY_SPACE, 3.75f, 5, 6.25f, 1));
        caps.add(new Cap("Alt", GLFW.GLFW_KEY_RIGHT_ALT, 10, 5, 1.25f, 1));
        caps.add(new Cap("Win", GLFW.GLFW_KEY_RIGHT_SUPER, 11.25f, 5, 1.25f, 1));
        caps.add(new Cap("Menu", GLFW.GLFW_KEY_MENU, 12.5f, 5, 1.25f, 1));
        caps.add(new Cap("Ctrl", GLFW.GLFW_KEY_RIGHT_CONTROL, 13.75f, 5, 1.25f, 1));

        // navigation cluster
        caps.add(new Cap("Ins", GLFW.GLFW_KEY_INSERT, 15.5f, 1, 1, 1));
        caps.add(new Cap("Home", GLFW.GLFW_KEY_HOME, 16.5f, 1, 1, 1));
        caps.add(new Cap("PgUp", GLFW.GLFW_KEY_PAGE_UP, 17.5f, 1, 1, 1));
        caps.add(new Cap("Del", GLFW.GLFW_KEY_DELETE, 15.5f, 2, 1, 1));
        caps.add(new Cap("End", GLFW.GLFW_KEY_END, 16.5f, 2, 1, 1));
        caps.add(new Cap("PgDn", GLFW.GLFW_KEY_PAGE_DOWN, 17.5f, 2, 1, 1));
        caps.add(new Cap("Up", GLFW.GLFW_KEY_UP, 16.5f, 4, 1, 1));
        caps.add(new Cap("Left", GLFW.GLFW_KEY_LEFT, 15.5f, 5, 1, 1));
        caps.add(new Cap("Down", GLFW.GLFW_KEY_DOWN, 16.5f, 5, 1, 1));
        caps.add(new Cap("Right", GLFW.GLFW_KEY_RIGHT, 17.5f, 5, 1, 1));

        // number pad
        caps.add(new Cap("Num", GLFW.GLFW_KEY_NUM_LOCK, 18.5f, 0, 1, 1));
        caps.add(new Cap("/", GLFW.GLFW_KEY_KP_DIVIDE, 19.5f, 0, 1, 1));
        caps.add(new Cap("*", GLFW.GLFW_KEY_KP_MULTIPLY, 20.5f, 0, 1, 1));
        caps.add(new Cap("-", GLFW.GLFW_KEY_KP_SUBTRACT, 21.5f, 0, 1, 1));
        int[] pad = {GLFW.GLFW_KEY_KP_7, GLFW.GLFW_KEY_KP_8, GLFW.GLFW_KEY_KP_9, GLFW.GLFW_KEY_KP_4,
                GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_KP_6, GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2,
                GLFW.GLFW_KEY_KP_3};
        String[] padName = {"7", "8", "9", "4", "5", "6", "1", "2", "3"};
        for (int i = 0; i < pad.length; i++) {
            int col = i % 3;
            int row = 1 + i / 3;
            caps.add(new Cap(padName[i], pad[i], 18.5f + col, row, 1, 1));
        }
        caps.add(new Cap("+", GLFW.GLFW_KEY_KP_ADD, 21.5f, 1, 1, 2));
        caps.add(new Cap("Enter", GLFW.GLFW_KEY_KP_ENTER, 21.5f, 3, 1, 2));
        caps.add(new Cap("0", GLFW.GLFW_KEY_KP_0, 18.5f, 4, 2, 1));
        caps.add(new Cap(".", GLFW.GLFW_KEY_KP_DECIMAL, 20.5f, 4, 1, 1));
    }

    private static int codeOf(String letter) {
        return switch (letter) {
            case "Q" -> GLFW.GLFW_KEY_Q;
            case "W" -> GLFW.GLFW_KEY_W;
            case "E" -> GLFW.GLFW_KEY_E;
            case "R" -> GLFW.GLFW_KEY_R;
            case "T" -> GLFW.GLFW_KEY_T;
            case "Y" -> GLFW.GLFW_KEY_Y;
            case "U" -> GLFW.GLFW_KEY_U;
            case "I" -> GLFW.GLFW_KEY_I;
            case "O" -> GLFW.GLFW_KEY_O;
            case "P" -> GLFW.GLFW_KEY_P;
            case "A" -> GLFW.GLFW_KEY_A;
            case "S" -> GLFW.GLFW_KEY_S;
            case "D" -> GLFW.GLFW_KEY_D;
            case "F" -> GLFW.GLFW_KEY_F;
            case "G" -> GLFW.GLFW_KEY_G;
            case "H" -> GLFW.GLFW_KEY_H;
            case "J" -> GLFW.GLFW_KEY_J;
            case "K" -> GLFW.GLFW_KEY_K;
            case "L" -> GLFW.GLFW_KEY_L;
            case "Z" -> GLFW.GLFW_KEY_Z;
            case "X" -> GLFW.GLFW_KEY_X;
            case "C" -> GLFW.GLFW_KEY_C;
            case "V" -> GLFW.GLFW_KEY_V;
            case "B" -> GLFW.GLFW_KEY_B;
            case "N" -> GLFW.GLFW_KEY_N;
            case "M" -> GLFW.GLFW_KEY_M;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    // ------------------------------------------------------------------ state

    @Override
    protected void init() {
        if (search == null) {
            search = new EditBox(minecraft.font, 0, 0, 120, 12, Component.empty());
            search.setMaxLength(40);
            search.setBordered(false);
            search.setHint(Component.literal("Search modules"));
            search.setResponder(s -> {
                listScroll = 0;
                refilter();
            });
        }
        addRenderableWidget(search);
        search.setVisible(picked >= 0);
        refilter();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0050A14);
    }

    private void refilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (Module m : all) {
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q) && !m.id.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(m);
        }
    }

    /** The bindable name of the key a module holds, or "" when it is free ("key.keyboard.f"). */
    private String keyId(Module m) {
        return m.key == null || m.key.isUnbound() ? "" : m.key.saveString();
    }

    private static String keyId(int code) {
        return code < 0 ? "" : InputConstants.Type.KEYSYM.getOrCreate(code).getName();
    }

    private String keyName(Module m) {
        return m.key == null || m.key.isUnbound() ? "None" : m.key.getTranslatedKeyMessage().getString();
    }

    private List<Module> boundTo(int code) {
        List<Module> out = new ArrayList<>();
        String id = keyId(code);
        if (id.isEmpty()) return out;
        for (Module m : all) if (id.equals(keyId(m))) out.add(m);
        return out;
    }

    private float maxScroll() {
        return Math.max(0, shown.size() * ROW - listH);
    }

    private void bind(Module m, int code) {
        if (m.key == null) return;
        // One key can only do one thing: whoever held it loses it.
        String id = keyId(code);
        for (Module o : all) {
            if (o != m && id.equals(keyId(o)) && o.key != null) o.key.setKey(InputConstants.UNKNOWN);
        }
        m.key.setKey(code < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(code));
        KeyMapping.resetMapping();
        minecraft.options.save();
        Zeus.save();
    }

    private void unbind(Module m) {
        if (m.key == null) return;
        m.key.setKey(InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        minecraft.options.save();
        Zeus.save();
    }

    private void clearKey(int code) {
        for (Module m : boundTo(code)) if (m.key != null) m.key.setKey(InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        minecraft.options.save();
        Zeus.save();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        pw = Math.min(760, width - 12);
        ph = Math.min(430, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        Draw.rrect(g, px, py, pw, ph, 6, BG);
        Draw.rrectLine(g, px, py, pw, ph, 6, EDGE);

        int bound = 0;
        for (Module m : all) if (!keyId(m).isEmpty()) bound++;
        Draw.text(g, "Keybinds", px + 10, py + 8, TEXT);
        String count = bound + " of " + all.size() + " linked";
        Draw.text(g, count, px + pw - 10 - Draw.width(count), py + 8, DIM);

        // the board
        unit = Math.max(13f, Math.min(30f, (pw - 20f) / 22.5f));
        kHeight = Math.round(unit * 6f + 5f);
        kx = px + 8;
        ky = py + 22;
        drawBoard(g, mx, my, partialTick);

        // the picker
        listX = px + 8;
        listY = ky + kHeight + 18;
        listW = pw - 16;
        listH = py + ph - 26 - listY;
        drawPicker(g, mx, my, partialTick);

        String hint = picked < 0
                ? "Click a key to see every module that can use it. Click a linked module to take it off the key. Escape closes."
                : "Pick a module to link it to this key. Click the same module again to unlink. Escape closes the list.";
        Draw.text(g, Draw.fit(hint, pw - 20), px + 10, py + ph - 14, DIM);

        if (picked >= 0) search.render(g, mx, my, partialTick);
    }

    private void drawBoard(GuiGraphics g, int mx, int my, float partialTick) {
        hit.clear();
        for (Cap c : caps) {
            int x = kx + Math.round(c.x * unit);
            int y = ky + Math.round(c.y * unit);
            int w = Math.round(c.w * unit) - 2;
            int h = Math.round(c.h * unit) - 2;
            if (x + w > px + pw - 8) continue;
            List<Module> linked = boundTo(c.code);
            boolean selected = picked == c.code;
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
            int fill = selected ? 0xFF14356E : !linked.isEmpty() ? (hover ? 0xFF1B3F86 : 0xFF12294F) : hover ? 0xFF122039 : 0xFF0A1226;
            int line = selected ? 0xFF3C92FF : !linked.isEmpty() ? 0xFF4C7FDA : hover ? 0xFF2F4C82 : 0xFF263860;
            Draw.rrect(g, x, y, w, h, 3, fill);
            Draw.rrectLine(g, x, y, w, h, 3, line);
            String label = c.label;
            if (Draw.width(label) > w - 4) label = label.substring(0, Math.min(label.length(), 3));
            Draw.text(g, label, x + (w - Draw.width(label)) / 2, y + (h - 8) / 2,
                    !linked.isEmpty() ? 0xFFFFFFFF : 0xFF9DAECB);
            if (!linked.isEmpty()) {
                g.fill(x + 3, y + 3, x + 6, y + 6, Draw.rgba(ON, 0.95f));
                String name = linked.get(0).name;
                String fit = Draw.fit(name, w - 4);
                if (!fit.isEmpty() && h > 12) {
                    Draw.text(g, fit, x + (w - Draw.width(fit)) / 2, y + h - 11, Draw.rgba(HOT, 0.95f));
                }
            }
            hit.add(new Cap(label, c.code, x, y, w, h));
        }
    }

    private void drawPicker(GuiGraphics g, int mx, int my, float partialTick) {
        rows.clear();
        if (picked < 0) {
            String pick = "No key picked yet";
            Draw.text(g, pick, listX, listY + 2, DIM);
            return;
        }
        String head = "Key " + nameOf(picked);
        Draw.text(g, head, listX, listY, 0xFFC3D2F2);
        String keys = "";
        List<Module> linked = boundTo(picked);
        for (Module m : linked) keys += (keys.isEmpty() ? "" : ", ") + m.name;
        if (!keys.isEmpty()) Draw.text(g, Draw.fit("now: " + keys, Math.round(listW * 0.55f)),
                listX + Math.round(listW * 0.45f), listY, ON);

        int top = listY + 13;
        int boxW = Math.min(220, listW - 10);
        Draw.rrect(g, listX, top, boxW, 14, 3, 0xFF0A1226);
        Draw.rrectLine(g, listX, top, boxW, 14, 3, search.isFocused() ? 0xFF3C92FF : 0xFF263860);
        search.setX(listX + 5);
        search.setY(top + 3);
        search.setWidth(boxW - 10);
        search.setVisible(true);

        int by = top + 18;
        int bh = listY + listH - by;
        g.enableScissor(listX, by, listX + listW, by + bh);
        int first = (int) (listScroll / ROW);
        float off = listScroll - first * ROW;
        for (int i = first; i < shown.size(); i++) {
            int y = by + (i - first) * ROW - Math.round(off);
            if (y >= by + bh) break;
            Module m = shown.get(i);
            boolean on = keyId(m).equals(keyId(picked));
            boolean has = !keyId(m).isEmpty();
            boolean hover = mx >= listX && mx < listX + listW && my >= y && my < y + ROW - 1
                    && my >= by && my < by + bh;
            if (hover) g.fill(listX, y, listX + listW, y + ROW - 1, 0x14FFFFFF);
            g.fill(listX, y + 2, listX + 2, y + ROW - 3, Draw.rgba(m.category.color(), 0.9f));
            Draw.text(g, Draw.fit(m.name, Math.round(listW * 0.5f)), listX + 7, y + 4,
                    on ? 0xFFFFFFFF : m.enabled() ? 0xFFDCE7FA : TEXT);
            String cat = m.category.label;
            Draw.text(g, cat, listX + Math.round(listW * 0.52f), y + 4, 0xFF5E7094);
            String right = on ? "linked" : has ? keyName(m) : "free";
            int col = on ? ON : has ? HOT : 0xFF7F93BD;
            Draw.text(g, right, listX + listW - Draw.width(right), y + 4, col);
            rows.add(new int[]{listX, y, listW, ROW - 1, i});
        }
        g.disableScissor();
        if (shown.isEmpty()) Draw.text(g, "No module matches.", listX + 7, by + 4, DIM);
        if (maxScroll() > 0) {
            int sbH = Math.max(12, Math.round(bh * (bh / (float) (shown.size() * ROW))));
            int sy = by + Math.round((bh - sbH) * (listScroll / maxScroll()));
            g.fill(listX + listW - 3, sy, listX + listW - 1, sy + sbH, 0x66FFFFFF);
        }
    }

    private String nameOf(int code) {
        if (code < 0) return "None";
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
        } catch (RuntimeException e) {
            return "Key " + code;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (button <= 1) {
            for (Cap c : hit) {
                int x = Math.round(c.x);
                int y = Math.round(c.y);
                if (mx >= x && mx < x + Math.round(c.w) && my >= y && my < y + Math.round(c.h)) {
                    if (button == 1) {
                        clearKey(c.code);
                        if (picked == c.code) picked = -1;
                    } else if (picked == c.code) {
                        picked = -1;
                    } else {
                        picked = c.code;
                        listScroll = 0;
                        refilter();
                    }
                    search.setFocused(picked >= 0);
                    search.setVisible(picked >= 0);
                    return true;
                }
            }
        }
        if (picked >= 0 && button == 0) {
            for (int[] r : rows) {
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    Module m = shown.get(r[4]);
                    if (keyId(m).equals(keyId(picked))) unbind(m);
                    else bind(m, picked);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(x, y, scrollX, scrollY);
        if (picked >= 0 && y >= listY && y <= listY + listH) {
            listScroll = Math.max(0, Math.min(maxScroll(), listScroll - (float) scrollY * ROW * 2));
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (picked >= 0) {
                picked = -1;
                search.setVisible(false);
                search.setFocused(false);
                return true;
            }
            if (search != null && !search.getValue().isEmpty()) {
                search.setValue("");
                return true;
            }
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN && picked >= 0) {
            listScroll = Math.min(maxScroll(), listScroll + listH);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP && picked >= 0) {
            listScroll = Math.max(0, listScroll - listH);
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
