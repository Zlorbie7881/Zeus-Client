package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SeedWorldsScreen extends Screen {

    public record Row(String key, String label, String seed, boolean here) {}

    private static final int ROW = 32;

    private final Screen parent;
    private final Supplier<List<Row>> rows;
    private final Consumer<String> use;
    private final Consumer<String> forget;
    private float scroll;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int listY;
    private int listH;
    private int doneX;
    private int doneY;

    public SeedWorldsScreen(Screen parent, Supplier<List<Row>> rows, Consumer<String> use, Consumer<String> forget) {
        super(Component.literal("Seed worlds"));
        this.parent = parent;
        this.rows = rows;
        this.use = use;
        this.forget = forget;
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
        pw = Math.min(width - 16, 430);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listY = py + 50;
        listH = py + ph - listY - 34;
        doneX = px + pw - 12 - 56;
        doneY = py + ph - 24;
    }

    private float maxScroll(int count) {
        return Math.max(0, count * ROW - listH);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        List<Row> list = rows.get();
        g.fill(0, 0, width, height, 0xB0030610);

        Draw.shadow(g, px, py, pw, ph, 8, 10, 0.5f);
        Draw.rrect(g, px, py, pw, ph, 8, 0xF2070C1A, 0xF2050914);
        Draw.rrectLine(g, px, py, pw, ph, 8, 0xFF234A8A);
        Draw.bold(g, "ZEUS / SEED WORLDS", px + 12, py + 10, 0xFFE9F1FF);
        String count = list.isEmpty() ? "nothing remembered" : list.size() + (list.size() == 1 ? " world" : " worlds");
        Draw.textRight(g, count, px + pw - 12, py + 10, list.isEmpty() ? 0xFF8EA2CC : 0xFF5CFF8A);
        Draw.text(g, Draw.fit("Click a world to use its seed, or Forget to drop it.", pw - 24), px + 12, py + 26, 0xFF8EA2CC);

        scroll = Math.max(0, Math.min(maxScroll(list.size()), scroll));
        if (listH > 0) g.enableScissor(px + 12, listY, px + pw - 12, listY + listH);
        for (int i = 0; i < list.size(); i++) {
            Row row = list.get(i);
            int x = px + 12;
            int w = pw - 24;
            int y = listY + i * ROW - (int) scroll;
            if (y + ROW < listY || y > listY + listH) continue;
            boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW - 4 && mouseY >= listY && mouseY < listY + listH;
            int fill = row.here() ? 0xFF10361E : hot ? 0xFF16264A : 0xFF0D1528;
            Draw.rrect(g, x, y, w, ROW - 4, 5, fill);
            Draw.rrectLine(g, x, y, w, ROW - 4, 5, row.here() ? 0xFF3CD070 : hot ? 0xFF3C92FF : 0xFF234A8A);
            Draw.text(g, Draw.fit(row.label(), w - 84), x + 9, y + 5, 0xFFE9F1FF);
            Draw.text(g, Draw.fit("Seed " + row.seed(), w - 84), x + 9, y + 16, 0xFF8EA2CC);
            if (row.here()) Draw.textRight(g, "HERE", x + w - 66, y + 16, 0xFF5CFF8A);

            int fx = x + w - 62;
            boolean fhot = mouseX >= fx && mouseX < fx + 54 && mouseY >= y + 6 && mouseY < y + ROW - 10;
            Draw.rrect(g, fx, y + 6, 54, 16, 4, fhot ? 0xFF4A1A24 : 0xFF2A1219);
            Draw.textCenter(g, "Forget", fx + 27, y + 10, 0xFFFFB0B0);
        }
        if (listH > 0) g.disableScissor();

        if (list.isEmpty()) {
            Draw.textCenter(g, "No save or server has a seed yet.", px + pw / 2, listY + 18, 0xFF8EA2CC);
            Draw.textCenter(g, "Join a world with Seed ESP on and its seed is kept here.", px + pw / 2, listY + 32, 0xFF6F82AA);
        }
        float ms = maxScroll(list.size());
        if (ms > 0) {
            int bx = px + pw - 8;
            g.fill(bx, listY, bx + 3, listY + listH, 0xFF0D1528);
            int th = Math.max(16, (int) (listH * (listH / (float) (list.size() * ROW))));
            int ty = listY + (int) ((listH - th) * (scroll / ms));
            g.fill(bx, ty, bx + 3, ty + th, 0xFF3C92FF);
        }

        boolean doneHot = mouseX >= doneX && mouseX < doneX + 56 && mouseY >= doneY - 2 && mouseY < doneY + 16;
        Draw.rrect(g, doneX, doneY - 2, 56, 18, 5, doneHot ? 0xFF10306E : 0xFF0A1A3C);
        Draw.rrectLine(g, doneX, doneY - 2, 56, 18, 5, doneHot ? 0xFF3C92FF : 0xFF234A8A);
        Draw.textCenter(g, "Done", doneX + 28, doneY + 3, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        layout();
        double mx = event.x();
        double my = event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);

        if (mx >= doneX && mx < doneX + 56 && my >= doneY - 2 && my < doneY + 16) {
            onClose();
            return true;
        }
        List<Row> list = rows.get();
        if (my < listY || my >= listY + listH || mx < px + 12 || mx >= px + pw - 12) return super.mouseClicked(event, doubleClick);
        int index = (int) ((my - listY + scroll) / ROW);
        if (index < 0 || index >= list.size()) return super.mouseClicked(event, doubleClick);
        Row row = list.get(index);
        int y = listY + index * ROW - (int) scroll;
        int fx = px + 12 + (pw - 24) - 62;
        if (mx >= fx && mx < fx + 54 && my >= y + 6 && my < y + ROW - 10) {
            forget.accept(row.key());
        } else {
            use.accept(row.key());
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(x, y, scrollX, scrollY);
        scroll = Math.max(0, Math.min(maxScroll(rows.get().size()), scroll - (float) scrollY * ROW));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 257 || key == 335 || key == 256) {
            onClose();
            return true;
        }
        if (key == 267) {
            scroll = Math.min(maxScroll(rows.get().size()), scroll + listH);
            return true;
        }
        if (key == 266) {
            scroll = Math.max(0, scroll - listH);
            return true;
        }
        return super.keyPressed(event);
    }

}
