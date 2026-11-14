package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The knife switcher: knife types down the left, the chosen type's skins as live 3D tiles, and a
 * large turning preview of the skin under the mouse (or the one in hand). Click a skin to hold it.
 */
public final class KnifePickerScreen extends Screen {

    public interface Chosen {
        void pick(int knife, int finish);
    }

    /** Only the HD knives are offered; HD knife i is knife kind FIRST + i. */
    private static final int FIRST = KnifeArt.allKnives().length - KnifeArt.HD_COUNT;

    private static final String[][] TYPE_ABOUT = {
            {"Tactical", "A straight drop-point fighter with a ribbed grip. The all-rounder."},
            {"Karambit", "A hooked claw on a grooved grip, with the tang coming through as a steel finger ring."},
            {"M9 Bayonet", "Military bayonet with a sawback spine, a fuller and a muzzle ring guard."},
            {"Butterfly", "Two slotted handle halves that swing around the blade. Flip it open."},
            {"Huntsman", "A heavy tanto-point survival knife with a sawback and a black rubber grip."},
            {"Bowie", "The classic big clip-point bowie with a brass guard and a wooden handle."},
            {"Skeleton", "A lean spear point with a finger hole through the blade and a wrapped handle."},
            {"Ursus", "Broad folding blade with a long fuller, finger-grooved G10 handle."},
            {"Stiletto", "A slim needle blade with a thin crossbar and polished steel bolsters."},
    };

    private static final String[][] SKIN_ABOUT = {
            {"Damascus", "Pattern-welded steel folded into flowing grey layers."},
            {"Slaughter", "Polished steel splashed with deep red."},
            {"Case hardened", "Heat-blued steel in gold, blue and violet patches."},
            {"Fade", "Anodised red to violet to blue, running out to the tip."},
            {"Crimson web", "Black carbon laced with a red web."},
            {"Tiger tooth", "Gold with torn black stripes."},
            {"Sapphire", "Deep blue swirled gem finish with sparkles."},
            {"Urban masked", "Grey and carbon urban camo."},
            {"Emerald", "Green swirled gem finish."},
            {"Marble fade", "A red and yellow flame swirl running into a blue tip."},
            {"Ruby", "Deep red swirled gem finish with sparkles."},
            {"Ultraviolet", "Violet-black anodised steel, worn bright at the edge."},
            {"Forest camo", "Olive, moss and khaki woodland camo."},
            {"Night", "Blackened satin steel with a bright ground edge."},
            {"Rust coat", "Orange-brown rust flaking over dark steel."},
            {"Gold leaf", "Polished gold with a faint engraved scroll."},
            {"Bloodstone", "Dark red marble: crimson clouds over near-black steel."},
            {"Stock", "Factory satin steel. Every collection starts here."},
            {"Lightning", "Blued black steel split by forked, glowing bolts."},
            {"Olympus", "White marble veined with grey and gold."},
            {"Inferno", "Charred steel cracked open by glowing lava."},
            {"Nebula", "Violet and teal gas clouds scattered with stars."},
            {"Frost", "Pale ice-blue steel grown over with frost crystals."},
            {"Phantom", "Holographic foil that shifts colour as it turns."},
            {"Toxic", "Black steel eaten through by glowing acid."},
            {"Sunset", "Violet at the guard through pink and orange to a gold tip."},
            {"Thunderhead", "Rolling slate storm clouds with a single bright fork."},
            {"Abyss", "Teal fading to black, lit by rippling caustics."},
    };

    /** Knife types in order, each with the HD indices of its skins. */
    private static final java.util.List<String> TYPES = new java.util.ArrayList<>();
    private static final java.util.List<int[]> SKINS = new java.util.ArrayList<>();

    static {
        String[] all = KnifeArt.allKnives();
        java.util.Map<String, java.util.List<Integer>> by = new java.util.LinkedHashMap<>();
        for (int i = 0; i < KnifeArt.HD_COUNT; i++) {
            by.computeIfAbsent(typeOf(all[FIRST + i]), k -> new java.util.ArrayList<>()).add(i);
        }
        by.forEach((k, v) -> {
            TYPES.add(k);
            SKINS.add(v.stream().mapToInt(Integer::intValue).toArray());
        });
    }

    private final Screen parent;
    private final Chosen chosen;
    private final int[] finishOf;
    private int knife;
    private int group;
    private int hover = -1;
    private int hoverGroup = -1;
    private int px, py, pw, ph;

    public KnifePickerScreen(Screen parent, int knife, int[] finishes, Chosen chosen) {
        super(Component.literal("Switch knife"));
        this.parent = parent;
        this.chosen = chosen;
        this.finishOf = finishes.clone();
        this.knife = Math.max(FIRST, Math.min(KnifeArt.allKnives().length - 1, knife));
        this.group = groupOf(this.knife);
    }

    private static String typeOf(String name) {
        int dot = name.indexOf('\u00b7');
        return dot >= 0 ? name.substring(0, dot).trim() : name;
    }

    private static String skinOf(String name) {
        int dot = name.lastIndexOf('\u00b7');
        return dot >= 0 ? name.substring(dot + 1).trim() : name;
    }

    private static int groupOf(int kind) {
        for (int gi = 0; gi < SKINS.size(); gi++) for (int i : SKINS.get(gi)) if (FIRST + i == kind) return gi;
        return 0;
    }

    private static String find(String[][] table, String key) {
        for (String[] a : table) if (a[0].equalsIgnoreCase(key)) return a[1];
        return "";
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

    // ---------------------------------------------------------------- layout
    private void layout() {
        pw = Math.min(width - 16, 620);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    private int listW() {
        return Math.max(96, pw * 22 / 100);
    }

    private int rowH() {
        return Math.max(14, Math.min(24, (ph - 30 - 30) / Math.max(1, TYPES.size())));
    }

    private int rowY(int gi) {
        return py + 30 + gi * rowH();
    }

    private int gridX() {
        return px + 12 + listW() + 10;
    }

    private int gridW() {
        return (pw - listW() - 44) * 56 / 100;
    }

    private int cols() {
        return SKINS.get(group).length > 6 ? 4 : 3;
    }

    private int tileW() {
        return (gridW() - (cols() - 1) * 4) / cols();
    }

    private int tileH() {
        int n = SKINS.get(group).length, rows = (n + cols() - 1) / cols();
        return Math.max(30, Math.min(110, (ph - 30 - 34 - 14 - (rows - 1) * 4) / rows));
    }

    private int tileX(int t) {
        return gridX() + (t % cols()) * (tileW() + 4);
    }

    private int tileY(int t) {
        return py + 44 + (t / cols()) * (tileH() + 4);
    }

    // ---------------------------------------------------------------- drawing
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1e9f;
        String[] all = KnifeArt.allKnives();
        g.fill(0, 0, width, height, 0xB0030610);
        Draw.shadow(g, px, py, pw, ph, 8, 10, 0.5f);
        Draw.rrect(g, px, py, pw, ph, 8, 0xF2070C1A, 0xF2050914);
        Draw.rrectLine(g, px, py, pw, ph, 8, 0xFF234A8A);
        Draw.bold(g, "ZEUS / SWITCH KNIFE", px + 12, py + 11, 0xFFE9F1FF);
        Draw.textRight(g, com.zlorbie788.zeus.core.Crates.ownedCount() + " / " + KnifeArt.HD_COUNT + " unlocked", px + pw - 12, py + 11, 0xFF8EA2CC);

        // knife types
        hoverGroup = -1;
        int lw = listW(), rh = rowH(), held = groupOf(knife);
        for (int gi = 0; gi < TYPES.size(); gi++) {
            int x = px + 12, y = rowY(gi);
            boolean on = gi == group, hot = mouseX >= x && mouseX < x + lw && mouseY >= y && mouseY < y + rh - 2;
            if (hot) hoverGroup = gi;
            Draw.rrect(g, x, y, lw, rh - 2, 4, on ? 0xFF14306A : hot ? 0xFF0F2148 : 0xFF0A1428);
            if (on) g.fill(x, y + 3, x + 2, y + rh - 5, 0xFF5CA8FF);
            int tc = on ? 0xFFFFFFFF : hot ? 0xFFDCE6FA : 0xFFB0C0E0;
            Draw.text(g, Draw.fit(TYPES.get(gi), lw - 34), x + 8, y + (rh - 2) / 2 - 4, tc);
            int ownedN = 0;
            for (int sk : SKINS.get(gi)) if (com.zlorbie788.zeus.core.Crates.ownsHd(sk)) ownedN++;
            String count = ownedN + "/" + SKINS.get(gi).length;
            Draw.textRight(g, count, x + lw - 6, y + (rh - 2) / 2 - 4, gi == held ? 0xFF6FE3A0 : 0xFF5E73A0);
        }

        // skins of the chosen type
        hover = -1;
        int gx = gridX(), tw = tileW(), th = tileH();
        int[] skins = SKINS.get(group);
        Draw.bold(g, TYPES.get(group).toUpperCase(Locale.ROOT), gx, py + 31, 0xFFE9F1FF);
        Draw.textRight(g, skins.length + " skins", gx + gridW(), py + 31, 0xFF5E73A0);
        for (int t = 0; t < skins.length; t++) {
            int kind = FIRST + skins[t];
            int x = tileX(t), y = tileY(t);
            boolean on = kind == knife, hot = mouseX >= x && mouseX < x + tw && mouseY >= y && mouseY < y + th;
            if (hot) hover = kind;
            Draw.rrect(g, x, y, tw, th, 5, on ? 0xFF14306A : hot ? 0xFF10224A : 0xFF0A1530);
            Draw.rrectLine(g, x, y, tw, th, 5, on ? 0xFF5CA8FF : hot ? 0xFF3A6ACA : 0xFF1E3A70);
            boolean own = com.zlorbie788.zeus.core.Crates.ownsHd(skins[t]);
            int rc = com.zlorbie788.zeus.core.Crates.rarityOf(com.zlorbie788.zeus.core.Crates.idOf(skins[t])).colour;
            g.fill(x + 4, y + th - 3, x + tw - 4, y + th - 1, own ? rc : Draw.darken(rc, 0.6f));
            KnifeArt.showcase(g, kind, 0, x + 3, y + 2, tw - 6, th - 14, own ? time + t * 0.7f : 0f, false);
            if (!own) {
                Draw.rrect(g, x, y, tw, th, 5, 0xB8060810);
                drawLock(g, x + tw / 2, y + (th - 14) / 2 - 2);
            }
            Draw.textCenter(g, Draw.fit(skinOf(all[kind]), tw - 4), x + tw / 2, y + th - 11, !own ? 0xFF5E6A88 : on ? 0xFFFFFFFF : 0xFFB8C8E8);
        }

        // big preview: the skin under the mouse, else the one in hand, else the type's first skin
        int show = hover >= 0 ? hover : hoverGroup >= 0 ? firstOf(hoverGroup) : groupOf(knife) == group ? knife : FIRST + skins[0];
        int rx = gx + gridW() + 12, rw = px + pw - 12 - rx, ry = py + 30;
        int pvh = Math.max(60, ph - 30 - 100);
        Draw.rrect(g, rx, ry, rw, pvh, 7, 0xFF0B1834, 0xFF060C1E);
        Draw.rrectLine(g, rx, ry, rw, pvh, 7, 0xFF234A8A);
        KnifeArt.showcase(g, show, 0, rx + 6, ry + 6, rw - 12, pvh - 12, time, true);
        Draw.bold(g, Draw.fit(skinOf(all[show]).toUpperCase(Locale.ROOT), rw - 50), rx + 2, ry + pvh + 6, 0xFFE9F1FF);
        if (show == knife) Draw.textRight(g, "in hand", rx + rw, ry + pvh + 6, 0xFF6FE3A0);
        else if (!com.zlorbie788.zeus.core.Crates.ownsHd(show - FIRST)) {
            var from = com.zlorbie788.zeus.core.Crates.crateOf(com.zlorbie788.zeus.core.Crates.idOf(show - FIRST));
            Draw.textRight(g, "locked", rx + rw, ry + pvh + 6, 0xFFFF8A7A);
            if (from != null) Draw.textRight(g, "from the " + from.name, rx + rw, ry + pvh + 17, from.colour);
        }
        Draw.text(g, typeOf(all[show]), rx + 2, ry + pvh + 17, 0xFF6F86B8);
        String desc = find(SKIN_ABOUT, skinOf(all[show])) + " " + find(TYPE_ABOUT, typeOf(all[show]));
        int ty = ry + pvh + 30;
        for (String line : wrap(desc.trim(), rw)) {
            if (ty > py + ph - 42) break;
            Draw.text(g, line, rx + 2, ty, 0xFF9FB2D8);
            ty += 10;
        }

        Draw.text(g, "Unlock knives in Zeus Crates  \u00b7  \u2191\u2193 knife  \u2190\u2192 skin", px + 12, py + ph - 18, 0xFF8EA2CC);
        int cbx = px + pw - 12 - 56 - 8 - 96, cby = py + ph - 20;
        boolean hotC = mouseX >= cbx && mouseX < cbx + 96 && mouseY >= cby - 2 && mouseY < cby + 16;
        Draw.rrect(g, cbx, cby - 2, 96, 18, 5, hotC ? 0xFFFFD27A : 0xFFF5C04A, 0xFFB07A18);
        Draw.boldCenter(g, "ZEUS CRATES", cbx + 48, cby + 2, 0xFF1A1206);
        int bx = px + pw - 12 - 56, by = py + ph - 20;
        boolean hotDone = mouseX >= bx && mouseX < bx + 56 && mouseY >= by - 2 && mouseY < by + 16;
        Draw.rrect(g, bx, by - 2, 56, 18, 5, hotDone ? 0xFF1E4C9A : 0xFF14306A);
        Draw.boldCenter(g, "Done", bx + 28, by + 2, 0xFFE9F1FF);
    }

    private static void drawLock(GuiGraphics g, int cx, int cy) {
        int c = 0xFFB8C4DC;
        Draw.rrectLine(g, cx - 4, cy - 8, 8, 9, 4, c);
        Draw.rrect(g, cx - 6, cy - 2, 12, 9, 2, c);
        g.fill(cx - 1, cy + 1, cx + 1, cy + 4, 0xFF0A0E18);
    }

    private static int firstOf(int gi) {
        return FIRST + SKINS.get(gi)[0];
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
            int cbx = px + pw - 12 - 56 - 8 - 96, cby = py + ph - 20;
            if (x >= cbx && x < cbx + 96 && y >= cby - 2 && y < cby + 16) {
                minecraft.setScreen(new CratesScreen(this));
                return true;
            }
            int lw = listW(), rh = rowH();
            for (int gi = 0; gi < TYPES.size(); gi++) {
                int ry = rowY(gi);
                if (x >= px + 12 && x < px + 12 + lw && y >= ry && y < ry + rh - 2) {
                    group = gi;
                    if (doubleClick && com.zlorbie788.zeus.core.Crates.ownsHd(firstOf(gi) - FIRST)) pick(firstOf(gi));
                    return true;
                }
            }
            int tw = tileW(), th = tileH();
            int[] skins = SKINS.get(group);
            for (int t = 0; t < skins.length; t++) {
                int tx = tileX(t), ty = tileY(t);
                if (x >= tx && x < tx + tw && y >= ty && y < ty + th) {
                    if (!com.zlorbie788.zeus.core.Crates.ownsHd(skins[t])) return true;
                    pick(FIRST + skins[t]);
                    if (doubleClick) onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void pick(int kind) {
        if (!com.zlorbie788.zeus.core.Crates.ownsHd(kind - FIRST)) return;
        knife = kind;
        group = groupOf(kind);
        chosen.pick(knife, finishOf[knife]);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256 || key == 257 || key == 335) {
            onClose();
            return true;
        }
        if (key == 264 || key == 265) {          // down / up: next or previous knife type
            int gi = Math.floorMod(group + (key == 264 ? 1 : -1), TYPES.size());
            group = gi;
            for (int sk : SKINS.get(gi)) {
                if (com.zlorbie788.zeus.core.Crates.ownsHd(sk)) {
                    pick(FIRST + sk);
                    break;
                }
            }
            return true;
        }
        if (key == 262 || key == 263) {          // right / left: next or previous skin
            int[] skins = SKINS.get(group);
            int at = 0;
            for (int t = 0; t < skins.length; t++) if (FIRST + skins[t] == knife) at = t;
            for (int n = 0; n < skins.length; n++) {
                at = Math.floorMod(at + (key == 262 ? 1 : -1), skins.length);
                if (com.zlorbie788.zeus.core.Crates.ownsHd(skins[at])) break;
            }
            pick(FIRST + skins[at]);
            return true;
        }
        return super.keyPressed(event);
    }
}
