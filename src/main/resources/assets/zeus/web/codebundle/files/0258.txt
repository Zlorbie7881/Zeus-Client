package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class PlayerPickerScreen extends Screen {
    private record Entry(PlayerInfo info, String name, String id, String words) {}

    private static final int ROW_H = 18;
    private List<Entry> all;

    private final Screen parent;
    private final Consumer<String> commit;
    private final String title;
    private EditBox search;
    private float scroll;
    private List<Entry> shown = new ArrayList<>();
    private String shownFor = null;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int listTop;
    private int listH;

    public PlayerPickerScreen(Screen parent, String title, String current, Consumer<String> commit) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.commit = commit;
        search = new EditBox(minecraft.font, 0, 0, 100, 12, Component.empty());
        search.setMaxLength(40);
        search.setBordered(false);
        search.setHint(Component.literal("Search players on this server"));
        search.setValue(current);
        search.setResponder(s -> scroll = 0);
    }

    @Override
    protected void init() {
        all = null;
        shownFor = null;
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private List<Entry> everything() {
        if (all != null) return all;
        Minecraft mc = Minecraft.getInstance();
        List<Entry> out = new ArrayList<>();
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String id = info.getProfile().id().toString();
                String name = info.getProfile().name();
                String words = (name + " " + id).toLowerCase(Locale.ROOT);
                out.add(new Entry(info, name, id, words));
            }
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        all = out;
        return all;
    }

    private static List<Entry> filter(List<Entry> src, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return new ArrayList<>(src);
        String[] w = q.split("\\s+");
        List<Entry> first = new ArrayList<>();
        List<Entry> rest = new ArrayList<>();
        for (Entry e : src) {
            boolean ok = true;
            for (String t : w) {
                if (!e.words().contains(t)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            if (e.name().toLowerCase(Locale.ROOT).startsWith(q) || e.id().startsWith(q.replace(' ', '_'))) first.add(e);
            else rest.add(e);
        }
        first.addAll(rest);
        return first;
    }

    private void layout() {
        pw = Math.min(width - 16, 340);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listTop = py + 52;
        listH = ph - 52 - 24;
    }

    private float maxScroll() {
        return Math.max(0, shown.size() * ROW_H - listH);
    }

    private Entry byId(String id) {
        for (Entry e : everything()) if (e.id().equals(id)) return e;
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        g.fill(0, 0, width, height, 0xB0030610);

        Draw.shadow(g, px, py, pw, ph, 8, 10, 0.5f);
        Draw.rrect(g, px, py, pw, ph, 8, 0xF2070C1A, 0xF2050914);
        Draw.rrectLine(g, px, py, pw, ph, 8, 0xFF234A8A);
        Draw.bold(g, "ZEUS / " + title.toUpperCase(Locale.ROOT), px + 12, py + 10, 0xFFE9F1FF);

        String q = search.getValue();
        if (!q.equals(shownFor)) {
            shown = filter(everything(), q);
            shownFor = q;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll));

        int sy = py + 28;
        boolean focused = search.isFocused();
        Draw.rrect(g, px + 12, sy, pw - 24, 20, 5, 0xFF0A1226, 0xFF08101F);
        Draw.rrectLine(g, px + 12, sy, pw - 24, 20, 5, focused ? 0xFF3C92FF : 0xFF234A8A);
        search.setX(px + 34);
        search.setY(sy + 6);
        search.setWidth(pw - 24 - 40);
        search.render(g, mouseX, mouseY, partialTick);
        Draw.ellipse(g, px + 21, sy + 9, 4, 4, 0xFF5AA2FF);
        Draw.ellipse(g, px + 21, sy + 9, 2, 2, 0xFF0A1226);
        Draw.line(g, px + 24, sy + 12, px + 28, sy + 16, 1.6f, 0xFF5AA2FF);

        g.enableScissor(px + 12, listTop, px + pw - 12, listTop + listH);
        int first = (int) (scroll / ROW_H);
        int last = Math.min(shown.size(), first + listH / ROW_H + 2);
        for (int i = first; i < last; i++) {
            Entry e = shown.get(i);
            int y = listTop + i * ROW_H - (int) scroll;
            boolean on = e.name().equalsIgnoreCase(search.getValue().trim());
            boolean hot = mouseX >= px + 12 && mouseX < px + pw - 12 && mouseY >= y && mouseY < y + ROW_H;
            Draw.rrect(g, px + 12, y, pw - 24, ROW_H, 5, on ? 0xFF10361E : hot ? 0xFF16264A : 0xFF0D1528);
            if (on || hot) Draw.rrectLine(g, px + 12, y, pw - 24, ROW_H, 5, on ? 0xFF3CD070 : 0xFF3C92FF);
            Draw.text(g, e.name(), px + 18, y + 3, on ? 0xFFFFFFFF : hot ? 0xFFE9F1FF : 0xFF8EA2CC);
            Draw.textRight(g, e.id().substring(0, Math.min(8, e.id().length())), px + pw - 18, y + 3, 0xFF5A6C8C);
            if (hot) Draw.text(g, e.name() + "  ·  click to pick", px + 18, y + ROW_H + 3, 0xFF8EA2CC);
        }
        g.disableScissor();

        if (shown.isEmpty()) {
            Draw.textCenter(g, "No player has \"" + q.trim() + "\" in their name.", px + pw / 2, listTop + 18, 0xFF8EA2CC);
        }

        int dy = py + ph - 20;
        Draw.text(g, Draw.fit(shown.size() + " player" + (shown.size() == 1 ? "" : "s") + "  ·  click to pick, scroll for more", pw - 100), px + 12, dy + 4, 0xFF6F82AA);
        boolean doneHot = mouseX >= px + pw - 12 - 56 && mouseX < px + pw - 12 && mouseY >= dy - 2 && mouseY < dy + 16;
        Draw.rrect(g, px + pw - 12 - 56, dy - 2, 56, 18, 5, doneHot ? 0xFF10306E : 0xFF0A1A3C);
        Draw.rrectLine(g, px + pw - 12 - 56, dy - 2, 56, 18, 5, doneHot ? 0xFF3C92FF : 0xFF234A8A);
        Draw.textCenter(g, "Done", px + pw - 12 - 28, dy + 3, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        layout();
        double x = event.x();
        double y = event.y();
        if (event.button() == 0) {
            int dy = py + ph - 20;
            if (x >= px + pw - 12 - 56 && x < px + pw - 12 && y >= dy - 2 && y < dy + 16) {
                commit.accept(search.getValue().trim());
                onClose();
                return true;
            }
            if (x >= px + 12 && x < px + pw - 12 && y >= listTop && y < listTop + listH) {
                int idx = (int) ((y - listTop + scroll) / ROW_H);
                if (idx >= 0 && idx < shown.size()) {
                    commit.accept(shown.get(idx).name());
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(x, y, scrollX, scrollY);
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (float) scrollY * ROW_H * 2));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 257 || key == 335) {
            onClose();
            return true;
        }
        if (key == 256) {
            if (!search.getValue().isEmpty()) {
                search.setValue("");
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
