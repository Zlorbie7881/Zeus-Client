package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PaletteScreen extends Screen {
    private static final int TEXT = 0xFFE6EDFA;
    private static final int DIM = 0xFF8FA0BE;
    private static final int FAINT = 0xFF63748F;
    private static final int PAD = 8;
    private static final int ROW = 17;
    private static final int ROWS = 9;

    private final Screen parent;
    private EditBox field;
    private List<Entry> results = List.of();
    private int selected;
    private int scroll;
    private String lastQuery;
    private int px;
    private int py;
    private int pw;
    private int ph;

    private static final class Entry {
        final Module module;
        final Setting setting;
        final String label;
        final String sub;
        final String hay;
        int score;

        Entry(Module module, Setting setting, String label, String sub, String hay) {
            this.module = module;
            this.setting = setting;
            this.label = label;
            this.sub = sub;
            this.hay = hay;
        }

        boolean isModule() {
            return setting == null;
        }
    }

    private static List<Entry> index;

    public PaletteScreen(Screen parent) {
        super(Component.literal("Command palette"));
        this.parent = parent;
    }

    private static List<Entry> index() {
        if (index != null) return index;
        List<Entry> list = new ArrayList<>();
        for (Module m : Zeus.all()) {
            String hay = (m.name + " " + m.id + " " + String.join(" ", m.aliases()) + " " + m.desc).toLowerCase(Locale.ROOT);
            list.add(new Entry(m, null, m.name, m.category.label, hay));
            for (Setting s : m.settings()) {
                String h = (s.name + " " + s.desc + " " + m.name).toLowerCase(Locale.ROOT);
                list.add(new Entry(m, s, s.name, m.name, h));
            }
        }
        index = list;
        return list;
    }

    private static int score(String hay, String q) {
        int at = hay.indexOf(q);
        if (at == 0) return 1000 - Math.min(60, hay.length());
        if (at > 0) {
            char before = hay.charAt(at - 1);
            boolean word = !Character.isLetterOrDigit(before);
            return (word ? 850 : 700) - Math.min(260, at * 4);
        }
        int i = 0;
        for (int c = 0; c < hay.length() && i < q.length(); c++) {
            if (hay.charAt(c) == q.charAt(i)) i++;
        }
        if (i < q.length()) return -1;
        return 420 - Math.min(200, hay.length());
    }

    private void update() {
        Minecraft mc = Minecraft.getInstance();
        String raw = field == null ? "" : field.getValue().trim().toLowerCase(Locale.ROOT);
        if (raw.equals(lastQuery)) return;
        lastQuery = raw;
        selected = 0;
        scroll = 0;
        if (raw.isEmpty()) {
            List<Entry> list = new ArrayList<>();
            for (Module m : Zeus.all()) {
                if (m.enabled()) list.add(new Entry(m, null, m.name, "on", (m.name + " " + m.id).toLowerCase(Locale.ROOT)));
            }
            for (Module m : Zeus.all()) {
                if (!m.enabled()) list.add(new Entry(m, null, m.name, m.category.label, (m.name + " " + m.id).toLowerCase(Locale.ROOT)));
            }
            results = list;
            return;
        }
        boolean active = raw.equals("active") || raw.equals("enabled") || raw.equals("on") || raw.equals("running");
        boolean inactive = raw.equals("inactive") || raw.equals("disabled") || raw.equals("off");
        if (active || inactive) {
            List<Entry> list = new ArrayList<>();
            for (Module m : Zeus.all()) {
                if (m.toggleable() && m.enabled() == active) {
                    list.add(new Entry(m, null, m.name, active ? "on" : m.category.label, (m.name + " " + m.id).toLowerCase(Locale.ROOT)));
                }
            }
            results = list;
            return;
        }
        List<Entry> found = new ArrayList<>();
        for (Entry e : index()) {
            int s = score(e.hay, raw);
            if (s < 0) continue;
            if (e.isModule()) s += 30;
            e.score = s;
            found.add(e);
        }
        found.sort((a, b) -> Integer.compare(b.score, a.score));
        results = found.size() > 60 ? new ArrayList<>(found.subList(0, 60)) : found;
    }

    @Override
    protected void init() {
        pw = Math.min(372, width - 40);
        ph = 20 + ROW * ROWS + 16;
        px = (width - pw) / 2;
        py = Math.max(30, height / 2 - ph / 2 - 20);
        field = new EditBox(font, px + 34, py + 9, pw - 44, 12, Component.empty());
        field.setBordered(false);
        field.setMaxLength(40);
        field.setHint(Component.literal("Type a module or a setting…"));
        field.setResponder(s -> update());
        addRenderableWidget(field);
        setInitialFocus(field);
        lastQuery = null;
        update();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        update();
        Minecraft mc = Minecraft.getInstance();
        g.fill(0, 0, width, height, 0xB4000000);
        boolean world = mc.level != null;
        if (world) {
            g.fill(0, 0, width, height, Draw.rgba(0xFF050910, 0.25f));
        }
        Draw.shadow(g, px, py, pw, ph, 8, 6, 0.5f);
        Draw.rrect(g, px, py, pw, ph, 8, 0xFF101A2C, 0xFF0A1220);
        Draw.rrectLine(g, px, py, pw, ph, 8, Draw.rgba(accent(), 0.45f));

        Draw.rrect(g, px + PAD, py + 6, pw - PAD * 2, 18, 5, 0xFF152035);
        Draw.rrectLine(g, px + PAD, py + 6, pw - PAD * 2, 18, 5, Draw.rgba(accent(), 0.3f));
        Draw.ellipse(g, px + 15, py + 14, 4, 4, Draw.rgba(accent(), 0.85f));
        Draw.ellipse(g, px + 15, py + 14, 2, 2, 0xFF152035);
        Draw.line(g, px + 18f, py + 17.5f, px + 22f, py + 21.5f, 1.6f, Draw.rgba(accent(), 0.9f));
        field.render(g, mx, my, partial);

        int listTop = py + 28;
        g.fill(px + PAD, py + 26, px + pw - PAD, py + 27, Draw.rgba(0xFFFFFFFF, 0.06f));
        if (results.isEmpty()) {
            Draw.textCenter(g, "Nothing matches \"" + field.getValue() + "\"", px + pw / 2, listTop + 24, FAINT);
        }
        int rows = Math.min(ROWS, results.size());
        scroll = Math.max(0, Math.min(scroll, Math.max(0, results.size() - rows)));
        for (int i = 0; i < rows; i++) {
            Entry e = results.get(scroll + i);
            int ry = listTop + i * ROW;
            boolean sel = scroll + i == selected;
            if (sel) {
                Draw.rrect(g, px + PAD - 2, ry - 2, pw - PAD * 2 + 4, ROW - 2, 4, Draw.rgba(accent(), 0.20f));
                g.fill(px + PAD - 2, ry - 2, px + PAD - 1, ry + ROW - 4, Draw.rgba(accent(), 0.95f));
            }
            int lx = px + PAD + 4;
            boolean on = e.isModule() && e.module.enabled();
            int tag = e.isModule() ? (on ? Accent.OK : 0xFF7E8CA6) : 0xFF6FA8FF;
            String kind = e.isModule() ? (on ? "ON" : "OFF") : "SET";
            Draw.text(g, kind, lx, ry + 3, tag);
            int nameX = lx + 26;
            int room = pw - PAD * 2 - 30;
            String right = e.isModule() ? e.module.category.label : value(e.setting);
            int rightW = Math.min(Draw.width(right), 96);
            String label = Draw.fit(e.label, room - rightW - 18);
            if (sel) Draw.bold(g, label, nameX, ry + 2, 0xFFFFFFFF);
            else Draw.text(g, label, nameX, ry + 3, e.isModule() ? TEXT : 0xFFC8D4E6);
            if (!e.isModule()) Draw.textRight(g, Draw.fit(right, 96), px + pw - PAD - 6, ry + 3, 0xFF93A5C2);
            else if (!sel) Draw.textRight(g, right, px + pw - PAD - 6, ry + 3, FAINT);
            if (!e.sub.isBlank() && e.isModule() && sel) {
                int subX = nameX + Draw.widthBold(label) + 8;
                if (subX + Draw.width(e.sub) < px + pw - PAD - 60) Draw.text(g, e.sub, subX, ry + 3, 0xFF7F8FA8);
            }
        }
        String hint = "Enter: act  ·  Shift+Enter: open page  ·  Esc: close";
        Draw.text(g, Draw.fit(hint, pw - PAD * 2 - 42), px + PAD, py + ph - 12, FAINT);
        if (results.size() > ROWS) {
            Draw.textRight(g, (selected + 1) + "/" + results.size(), px + pw - PAD, py + ph - 12, FAINT);
        }
    }

    private static String value(Setting s) {
        if (s instanceof Setting.Bool b) return b.on() ? "On" : "Off";
        if (s instanceof Setting.Num n) return n.text();
        if (s instanceof Setting.Choice c) return c.value();
        if (s instanceof Setting.Text t) return t.get().isBlank() ? "empty" : t.get();
        return "";
    }

    private static int accent() {
        return Accent.main();
    }

    private void act(boolean openPage) {
        if (results.isEmpty()) return;
        Entry e = results.get(Math.min(selected, results.size() - 1));
        if (e.isModule()) {
            if (openPage) {
                jump(e.module.name);
            } else if (e.module.toggleable() && !Zeus.destroyed()) {
                int keep = selected;
                e.module.toggle();
                lastQuery = null;
                update();
                selected = Math.max(0, Math.min(keep, results.size() - 1));
            }
        } else {
            jump(e.setting.name);
        }
    }

    private void jump(String query) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(ZeusScreen.at(query));
    }

    private void move(int by) {
        if (results.isEmpty()) return;
        selected = Math.max(0, Math.min(results.size() - 1, selected + by));
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + ROWS) scroll = selected - ROWS + 1;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) { move(1); return true; }
        if (key == GLFW.GLFW_KEY_UP) { move(-1); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) { move(ROWS); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_UP) { move(-ROWS); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            act((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) { move(1); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int listTop = py + 28;
        double y = event.y();
        if (y >= listTop - 2 && y < listTop + ROWS * ROW) {
            int row = (int) ((y - (listTop - 2)) / ROW);
            int target = scroll + row;
            if (target >= 0 && target < results.size()) {
                selected = target;
                act(false);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(x, y, scrollX, scrollY);
        move((int) -Math.signum(scrollY) * 3);
        return true;
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(parent);
    }
}
