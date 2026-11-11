package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class BlockPickerScreen extends Screen {

    public record Entry(Block block, String id, String name, String words) {}

    private static final int CELL = 22;
    private static List<Entry> all;

    private final Screen parent;
    private final Set<String> picked;
    private final Consumer<Set<String>> changed;
    private final String title;
    private final int max;
    private EditBox search;
    private float scroll;
    private List<Entry> shown = new ArrayList<>();
    private String shownFor = null;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int gx;
    private int gy;
    private int gw;
    private int gh;
    private int cols;
    private int chipY;

    public BlockPickerScreen(Screen parent, String title, Set<String> start, Consumer<Set<String>> changed) {
        this(parent, title, start, 0, changed);
    }

    public BlockPickerScreen(Screen parent, String title, Set<String> start, int max, Consumer<Set<String>> changed) {
        super(Component.literal(title));
        this.max = max;
        this.parent = parent;
        this.title = title;
        this.picked = new LinkedHashSet<>(start);
        this.changed = changed;
    }

    public static synchronized List<Entry> everything() {
        if (all != null) return all;
        List<Entry> out = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            String id;
            try {
                String s = BuiltInRegistries.BLOCK.getKey(b).toString();
                id = s.substring(s.indexOf(':') + 1);
            } catch (RuntimeException e) {
                continue;
            }
            if (id.equals("air") || id.equals("cave_air") || id.equals("void_air")) continue;
            String name;
            try {
                name = new ItemStack(b.asItem()).getHoverName().getString();
            } catch (RuntimeException e) {
                name = id.replace('_', ' ');
            }
            out.add(new Entry(b, id, name, (name + " " + id.replace('_', ' ') + " " + id).toLowerCase(Locale.ROOT)));
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        all = out;
        return all;
    }

    public static List<Entry> filter(List<Entry> src, String query) {
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

    @Override
    protected void init() {
        if (search == null) {
            search = new EditBox(minecraft.font, 0, 0, 100, 12, Component.empty());
            search.setMaxLength(40);
            search.setBordered(false);
            search.setHint(Component.literal("Search blocks, like diamond ore"));
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
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void toggle(String id) {
        if (!picked.remove(id)) {
            if (max == 1) picked.clear();
            picked.add(id);
            if (max > 1) while (picked.size() > max) picked.remove(picked.iterator().next());
        }
        changed.accept(new LinkedHashSet<>(picked));
    }

    private void layout() {
        pw = Math.min(width - 16, 440);
        ph = Math.min(height - 16, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        gx = px + 12;
        gw = pw - 24 - 6;
        cols = Math.max(1, gw / CELL);
        chipY = py + 56;
        gy = chipY + 30;
        gh = py + ph - gy - 22;
    }

    private int chipsPerRow() {
        return Math.max(1, (pw - 24 - 70) / 20);
    }

    private int rows() {
        return (shown.size() + cols - 1) / cols;
    }

    private float maxScroll() {
        return Math.max(0, rows() * CELL - gh);
    }

    private Entry byId(String id) {
        for (Entry e : everything()) if (e.id().equals(id)) return e;
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        g.fill(0, 0, width, height, 0xB0030610);
        String q = search.getValue();
        if (!q.equals(shownFor)) {
            shown = filter(everything(), q);
            shownFor = q;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll));

        Draw.shadow(g, px, py, pw, ph, 8, 10, 0.5f);
        Draw.rrect(g, px, py, pw, ph, 8, 0xF2070C1A, 0xF2050914);
        Draw.rrectLine(g, px, py, pw, ph, 8, 0xFF234A8A);
        Draw.bold(g, "ZEUS / " + title.toUpperCase(Locale.ROOT), px + 12, py + 10, 0xFFE9F1FF);
        String count = picked.isEmpty() ? "nothing picked" : picked.size() + " picked";
        Draw.textRight(g, count, px + pw - 12, py + 10, picked.isEmpty() ? 0xFF8EA2CC : 0xFF5CFF8A);

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

        Draw.text(g, "Picked", px + 12, chipY + 2, 0xFF8EA2CC);
        List<String> ids = new ArrayList<>(picked);
        int per = chipsPerRow();
        String hover = null;
        for (int i = 0; i < Math.min(ids.size(), per); i++) {
            int cx = px + 12 + 44 + i * 20;
            Entry e = byId(ids.get(i));
            boolean hot = mouseX >= cx && mouseX < cx + 18 && mouseY >= chipY - 2 && mouseY < chipY + 16;
            Draw.rrect(g, cx, chipY - 2, 18, 18, 4, hot ? 0xFF3A1620 : 0xFF111A2D);
            if (e != null) {
                try {
                    g.renderItem(new ItemStack(e.block().asItem()), cx + 1, chipY - 1);
                } catch (RuntimeException ignored) {
                }
            }
            if (hot) hover = (e == null ? ids.get(i) : e.name()) + "  (click to remove)";
        }
        if (ids.size() > per) Draw.text(g, "+" + (ids.size() - per), px + 12 + 44 + per * 20 + 2, chipY + 2, 0xFF8EA2CC);
        boolean clearHot = !picked.isEmpty() && mouseX >= px + pw - 12 - 54 && mouseX < px + pw - 12 && mouseY >= chipY - 2 && mouseY < chipY + 16;
        if (!picked.isEmpty()) {
            Draw.rrect(g, px + pw - 12 - 54, chipY - 2, 54, 18, 5, clearHot ? 0xFF4A1A24 : 0xFF2A1219);
            Draw.textCenter(g, "Clear", px + pw - 12 - 27, chipY + 3, 0xFFFFB0B0);
        }

        g.enableScissor(gx, gy, gx + gw + 6, gy + gh);
        int first = (int) (scroll / CELL);
        int last = Math.min(rows(), first + gh / CELL + 2);
        for (int r = first; r < last; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                if (idx >= shown.size()) break;
                Entry e = shown.get(idx);
                int x = gx + c * CELL;
                int y = gy + r * CELL - (int) scroll;
                boolean on = picked.contains(e.id());
                boolean hot = mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2
                        && mouseX >= gx && mouseX < gx + gw && mouseY >= gy && mouseY < gy + gh;
                Draw.rrect(g, x, y, CELL - 2, CELL - 2, 4, on ? 0xFF10361E : hot ? 0xFF16264A : 0xFF0D1528);
                if (on || hot) Draw.rrectLine(g, x, y, CELL - 2, CELL - 2, 4, on ? 0xFF3CD070 : 0xFF3C92FF);
                try {
                    g.renderItem(new ItemStack(e.block().asItem()), x + 2, y + 2);
                } catch (RuntimeException ignored) {
                }
                if (hot) hover = e.name() + "  \u00b7  " + e.id();
            }
        }
        g.disableScissor();
        if (shown.isEmpty()) Draw.textCenter(g, "No block has \"" + q.trim() + "\" in its name.", px + pw / 2, gy + 20, 0xFF8EA2CC);

        float ms = maxScroll();
        if (ms > 0) {
            int bx = gx + gw + 2;
            g.fill(bx, gy, bx + 3, gy + gh, 0xFF0D1528);
            int th = Math.max(16, (int) (gh * (gh / (float) (rows() * CELL))));
            int ty = gy + (int) ((gh - th) * (scroll / ms));
            g.fill(bx, ty, bx + 3, ty + th, 0xFF3C92FF);
        }

        int dy = py + ph - 20;
        String hint = shown.size() + (shown.size() == 1 ? " block" : " blocks") + "  \u00b7  click to pick or drop, scroll for more";
        Draw.text(g, Draw.fit(hint, pw - 100), px + 12, dy + 4, 0xFF6F82AA);
        boolean doneHot = mouseX >= px + pw - 12 - 56 && mouseX < px + pw - 12 && mouseY >= dy - 2 && mouseY < dy + 16;
        Draw.rrect(g, px + pw - 12 - 56, dy - 2, 56, 18, 5, doneHot ? 0xFF10306E : 0xFF0A1A3C);
        Draw.rrectLine(g, px + pw - 12 - 56, dy - 2, 56, 18, 5, doneHot ? 0xFF3C92FF : 0xFF234A8A);
        Draw.textCenter(g, "Done", px + pw - 12 - 28, dy + 3, 0xFFFFFFFF);

        if (hover != null) {
            int tw = Draw.width(hover) + 10;
            int tx = Math.min(width - tw - 4, mouseX + 10);
            int ty = Math.min(height - 18, mouseY + 12);
            Draw.rrect(g, tx, ty, tw, 15, 4, 0xF0050914);
            Draw.rrectLine(g, tx, ty, tw, 15, 4, 0xFF3C92FF);
            Draw.text(g, hover, tx + 5, ty + 4, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        layout();
        double x = event.x();
        double y = event.y();
        if (event.button() == 0) {
            int dy = py + ph - 20;
            if (x >= px + pw - 12 - 56 && x < px + pw - 12 && y >= dy - 2 && y < dy + 16) {
                onClose();
                return true;
            }
            if (!picked.isEmpty() && x >= px + pw - 12 - 54 && x < px + pw - 12 && y >= chipY - 2 && y < chipY + 16) {
                picked.clear();
                changed.accept(new LinkedHashSet<>(picked));
                return true;
            }
            if (y >= chipY - 2 && y < chipY + 16 && x >= px + 12 + 44) {
                int i = (int) ((x - (px + 12 + 44)) / 20);
                List<String> ids = new ArrayList<>(picked);
                if (i >= 0 && i < Math.min(ids.size(), chipsPerRow())) {
                    toggle(ids.get(i));
                    return true;
                }
            }
            if (x >= gx && x < gx + gw && y >= gy && y < gy + gh) {
                int c = (int) ((x - gx) / CELL);
                int r = (int) ((y - gy + scroll) / CELL);
                int idx = r * cols + c;
                if (c < cols && idx >= 0 && idx < shown.size()) {
                    toggle(shown.get(idx).id());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(x, y, scrollX, scrollY);
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (float) scrollY * CELL * 2));
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
        if (key == 267) {
            scroll = Math.min(maxScroll(), scroll + gh);
            return true;
        }
        if (key == 266) {
            scroll = Math.max(0, scroll - gh);
            return true;
        }
        return super.keyPressed(event);
    }
}
