package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;

public final class Scoreboards {
    private Scoreboards() {}

    private static volatile boolean hideHidden;
    private static volatile boolean hideFake;

    public static boolean hiding() {
        return hideHidden || hideFake;
    }

    public static final class Hide extends Module {
        public Hide() {
            super("hidescoreboard", "Hide Scoreboard", "Takes the scoreboard the server shows on the right side of your screen away.", Category.INTERFACE);
        }

        @Override
        protected void onEnable() {
            hideHidden = true;
        }

        @Override
        protected void onDisable() {
            hideHidden = false;
        }
    }

    private static final String[] TITLE_NAMES = {"White", "Donut blue", "Blue", "Gold", "Red", "Green", "Aqua", "Purple", "Pink", "Grey", "Rainbow"};
    private static final int DONUT_A = 0xFF007DFC;
    private static final int DONUT_B = 0xFF00C8FC;
    private static final int[] COLOURS = {0xFF00A3FF, 0xFFFFAA00, 0xFFFF5555, 0xFF55FF55, 0xFF55FFFF, 0xFFAA55FF, 0xFFFF77CC, 0xFFFFFFFF, 0xFFAAAAAA, 0};

    private static final int GREEN = 0xFF00FF00;
    private static final int PURPLE = 0xFFA800FF;
    private static final int RED = 0xFFFF0000;
    private static final int ORANGE = 0xFFFF7A00;
    private static final int YELLOW = 0xFFFFE600;
    private static final int PING = 0xFF1A9DE6;
    private static final int PING_LABEL = 0xFFA9A9A9;

    private static final int LINE = 9;

    private static final int ICON_X = 3;
    private static final int ICON_GAP = 6;

    private static final String[] DOLLAR = {"..#..", ".####", "#....", ".###.", "....#", "####.", "..#.."};
    private static final String[] STAR = {"...#...", "..###..", "#######", "#######", ".#####.", ".#####.", ".##.##."};
    private static final String[] SWORD = {"......##", ".....#.#", "....#.#.", ".#.#.#..", "..#.#...", "..##....", ".#..#...", "#......."};
    private static final String[] SKULL = {".#####.", "#######", "#..#..#", "#..#..#", "###.###", ".##.##.", ".#.#.#."};
    private static final String[] CLOCK = {".#####.", "#..#..#", "#..#..#", "#..##.#", "#.....#", "#.....#", ".#####."};

    private static final int[] ADVANCE = {5, 7, 7, 7, 7};
    private static final String[][] PICS = {DOLLAR, STAR, SWORD, SKULL, CLOCK};
    private static final int[] PIC_COLOURS = {GREEN, PURPLE, RED, ORANGE, YELLOW};
    private static final String[] GLYPHS = {"$", "\u2605", "\uD83D\uDDE1", "\u2620", "\u231A"};
    private static final String[] LABELS = {"Money", "Shards", "Kills", "Deaths", "Playtime"};

    public static final class Fake extends Module {
        private final Setting.Choice titleMode = add(new Setting.Choice("Title is", "Player name: your own name, like the real board. Custom: the text below.", 0, "Player name", "Custom"));
        private final Setting.Text title = add(new Setting.Text("Title", "The text at the top. You can use & colour codes, for example &6&lMy Server.", "Zeus", 40)).showIf(() -> this.titleMode.index() == 1);
        private final Setting.Choice titleColour = add(new Setting.Choice("Title colour", "The colour of the title when the text has no colour codes of its own. White is the real one.", 0, TITLE_NAMES));
        private final Setting.Bool titleBold = add(new Setting.Bool("Bold title", "The real title is not bold.", false));
        private final Setting.Bool showMoney = add(new Setting.Bool("Show money", "", true));
        private final Setting.Text money = add(new Setting.Text("Money", "Whatever you like: 2.2B, 497M, 350k.", "2.2B", 16)).showIf(() -> this.showMoney.on());
        private final Setting.Bool showShards = add(new Setting.Bool("Show shards", "", true));
        private final Setting.Text shards = add(new Setting.Text("Shards", "", "8.4K", 16)).showIf(() -> this.showShards.on());
        private final Setting.Bool showKills = add(new Setting.Bool("Show kills", "", true));
        private final Setting.Text kills = add(new Setting.Text("Kills", "", "260", 16)).showIf(() -> this.showKills.on());
        private final Setting.Bool showDeaths = add(new Setting.Bool("Show deaths", "", true));
        private final Setting.Text deaths = add(new Setting.Text("Deaths", "", "342", 16)).showIf(() -> this.showDeaths.on());
        private final Setting.Bool showPlay = add(new Setting.Bool("Show playtime", "", true));
        private final Setting.Text play = add(new Setting.Text("Playtime", "", "11d 19h", 16)).showIf(() -> this.showPlay.on());
        private final Setting.Bool labels = add(new Setting.Bool("Words next to the numbers", "Writes Money, Shards, Kills and so on before each number. The real board only has the pictures.", false));
        private final Setting.Bool colouredNumbers = add(new Setting.Bool("Coloured numbers", "Colours each number like its picture. The real board has white numbers.", false));
        private final Setting.Bool showPing = add(new Setting.Bool("Show ping", "A Ping line at the bottom. The real board has none.", false));
        private final Setting.Choice pingMode = add(new Setting.Choice("Ping is", "Real: your actual ping to the server. Custom: the number below.", 0, "Real", "Custom")).showIf(() -> this.showPing.on());
        private final Setting.Text customPing = add(new Setting.Text("Custom ping", "A number of milliseconds.", "27", 5)).showIf(() -> this.showPing.on() && this.pingMode.index() == 1);
        private final Setting.Bool replace = add(new Setting.Bool("Replace the server's scoreboard", "Hides the scoreboard the server sends, so only this one is on your screen.", true));
        private final Setting.Choice side = add(new Setting.Choice("Side", "Which edge of the screen it sits on.", 1, "Left", "Right"));
        private final Setting.Num height = add(new Setting.Num("Height on the screen", "0% is the top, 50% is where the real one sits (a little under the middle), 100% the bottom.", 50, 0, 100, 1, "%"));
        private final Setting.Num margin = add(new Setting.Num("Distance from the edge", "", 1, 0, 80, 1, " px"));
        private final Setting.Num size = add(new Setting.Num("Size", "", 100, 50, 250, 5, "%"));
        private final Setting.Num bg = add(new Setting.Num("Background", "How dark the lines are. 0% is see-through. The title is a little darker, like the real one.", 30, 0, 100, 1, "%"));
        private final Setting.Choice icons = add(new Setting.Choice("Icons", "Drawn: the small pictures of the real board, made by Zeus (default). Game font: the characters the game itself has (star, skull, watch, sword); on Donut SMP with its resource pack they become the server's own pictures.", 0, "Drawn", "Game font"));
        private final Setting.Bool shadow = add(new Setting.Bool("Text shadow", "", true));

        public Fake() {
            super("fakescoreboard", "Fake Scoreboard", "A copy of the Donut SMP scoreboard: your name on top, then money, shards, kills, deaths and playtime with the numbers you choose.", Category.INTERFACE);
            hudCard();
            replace.onChange(() -> hideFake = enabled() && replace.on());
        }

        @Override
        protected void onEnable() {
            hideFake = replace.on();
        }

        @Override
        protected void onDisable() {
            hideFake = false;
        }

        private record Seg(String text, int colour, boolean bold) {}

        private static int code(char c) {
            return switch (Character.toLowerCase(c)) {
                case '0' -> 0xFF000000;
                case '1' -> 0xFF0000AA;
                case '2' -> 0xFF00AA00;
                case '3' -> 0xFF00AAAA;
                case '4' -> 0xFFAA0000;
                case '5' -> 0xFFAA00AA;
                case '6' -> 0xFFFFAA00;
                case '7' -> 0xFFAAAAAA;
                case '8' -> 0xFF555555;
                case '9' -> 0xFF5555FF;
                case 'a' -> 0xFF55FF55;
                case 'b' -> 0xFF55FFFF;
                case 'c' -> 0xFFFF5555;
                case 'd' -> 0xFFFF55FF;
                case 'e' -> 0xFFFFFF55;
                case 'f' -> 0xFFFFFFFF;
                default -> 0;
            };
        }

        private static List<Seg> parse(String s, int base, boolean bold0) {
            List<Seg> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            int colour = base;
            boolean bold = bold0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '&' && i + 1 < s.length()) {
                    char n = s.charAt(i + 1);
                    int col = 0;
                    int skip = 1;
                    if (n == '#' && i + 7 < s.length() + 0 && s.substring(i + 2).matches("(?s)[0-9a-fA-F]{6}.*")) {
                        col = 0xFF000000 | Integer.parseInt(s.substring(i + 2, i + 8), 16);
                        skip = 7;
                    } else {
                        col = code(n);
                    }
                    if (col != 0 || n == 'r' || n == 'R' || n == 'l' || n == 'L') {
                        if (cur.length() > 0) {
                            out.add(new Seg(cur.toString(), colour, bold));
                            cur.setLength(0);
                        }
                        if (col != 0) colour = col;
                        else if (n == 'r' || n == 'R') {
                            colour = base;
                            bold = bold0;
                        } else bold = true;
                        i += skip;
                        continue;
                    }
                }
                cur.append(c);
            }
            if (cur.length() > 0) out.add(new Seg(cur.toString(), colour, bold));
            return out;
        }

        private int pickTitle(float rainbowOffset) {
            int i = titleColour.index();
            if (i == 0) return 0xFFFFFFFF;
            if (i == 1) return DONUT_A;
            if (i == TITLE_NAMES.length - 1) return Accent.rainbowAt(rainbowOffset);
            int k = i - 2;
            return COLOURS[k >= 7 ? 8 : k];
        }

        private List<Seg> titleSegs(Minecraft mc) {
            String t = titleMode.index() == 0 ? mc.player.getName().getString() : title.get();
            boolean b = titleBold.on();
            if (titleColour.index() != 1 || t.indexOf('&') >= 0) return parse(t, pickTitle(0f), b);
            List<Seg> out = new ArrayList<>();
            int n = Math.max(1, t.length() - 1);
            for (int i = 0; i < t.length(); i++) {
                out.add(new Seg(String.valueOf(t.charAt(i)), Draw.mix(DONUT_A, DONUT_B, i / (float) n), b));
            }
            return out;
        }

        private static int width(List<Seg> segs) {
            int w = 0;
            for (Seg s : segs) w += s.bold ? Draw.widthBold(s.text) : Draw.width(s.text);
            return w;
        }

        private void draw(GuiGraphics g, List<Seg> segs, int x, int y) {
            int cx = x;
            for (Seg s : segs) {
                if (s.bold) Draw.bold(g, s.text, cx, y, s.colour);
                else if (shadow.on()) Draw.textShadow(g, s.text, cx, y, s.colour);
                else Draw.text(g, s.text, cx, y, s.colour);
                cx += s.bold ? Draw.widthBold(s.text) : Draw.width(s.text);
            }
        }

        private void icon(GuiGraphics g, String[] bits, int x, int y, int colour) {
            int drop = 0xFF000000 | (Draw.darken(colour, 0.75f) & 0xFFFFFF);
            for (int pass = shadow.on() ? 0 : 1; pass < 2; pass++) {
                int o = pass == 0 ? 1 : 0;
                int c = pass == 0 ? drop : colour;
                for (int r = 0; r < bits.length; r++) {
                    String row = bits[r];
                    int q = 0;
                    while (q < row.length()) {
                        if (row.charAt(q) == '.') {
                            q++;
                            continue;
                        }
                        int q0 = q;
                        while (q < row.length() && row.charAt(q) != '.') q++;
                        g.fill(x + q0 + o, y + r + o, x + q + o, y + r + 1 + o, c);
                    }
                }
            }
        }

        private int ping(Minecraft mc) {
            if (pingMode.index() == 1) {
                try {
                    return Math.max(0, Integer.parseInt(customPing.get().trim()));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            try {
                if (mc.getConnection() != null && mc.player != null) {
                    PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                    if (info != null) return Math.max(0, info.getLatency());
                }
            } catch (RuntimeException ignored) {
            }
            return 0;
        }

        private boolean realFont() {
            return icons.index() == 1;
        }

        private int iconSpace(Row r) {
            if (!realFont()) return ADVANCE[r.pic] + ICON_GAP;
            return (r.pic == 0 ? Draw.widthBold(GLYPHS[0]) : Draw.width(GLYPHS[r.pic])) + Draw.width(" ");
        }

        private record Row(int kind, List<Seg> text, int pic, String label, String value, int valueColour) {}

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            if (mc.options.hideGui) return;
            List<Row> rows = new ArrayList<>();
            List<Seg> head = titleSegs(mc);
            rows.add(new Row(0, head, 0, null, null, 0));
            boolean[] on = {showMoney.on(), showShards.on(), showKills.on(), showDeaths.on(), showPlay.on()};
            String[] vals = {money.get(), shards.get(), kills.get(), deaths.get(), play.get()};
            boolean coloured = colouredNumbers.on();
            for (int i = 0; i < 5; i++) {
                if (on[i]) rows.add(new Row(1, null, i, labels.on() ? LABELS[i] : null, vals[i], coloured ? PIC_COLOURS[i] : 0xFFFFFFFF));
            }
            if (showPing.on()) rows.add(new Row(3, null, 0, "Ping:", ping(mc) + "ms", PING));

            int titleW = width(head);
            int content = 0;
            for (int i = 1; i < rows.size(); i++) {
                Row r = rows.get(i);
                int rw = switch (r.kind) {
                    case 0 -> ICON_X + width(r.text);
                    case 1 -> ICON_X + iconSpace(r) + (r.label != null ? Draw.width(r.label) + Draw.width(" ") : 0) + Draw.width(r.value);
                    case 3 -> ICON_X + Draw.width(r.label) + Draw.width(" ") + Draw.width(r.value);
                    default -> 0;
                };
                content = Math.max(content, rw);
            }
            int pw = Math.max(titleW + 8, content + 4);
            int ph = rows.size() * LINE + 1;
            float sc = (float) size.get() / 100f;
            int sw = Math.round(pw * sc);
            int sh = Math.round(ph * sc);
            int x = side.index() == 1 ? w - sw - margin.i() : margin.i();
            int vanilla = Math.round(h / 2f - sh * 2f / 3f);
            int y = vanilla + Math.round(((float) height.get() - 50f) / 100f * h);
            y = Math.max(0, Math.min(h - sh, y));

            Hud.record(x, y, sw, sh);
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(sc, sc);
            float ba = (float) bg.get() / 100f;
            if (bg.get() > 0) {
                g.fill(0, 0, pw, LINE + 1, Draw.rgba(0xFF000000, Math.min(1f, ba * 4f / 3f)));
                g.fill(0, LINE + 1, pw, ph, Draw.rgba(0xFF000000, ba));
            }
            int cy = 1;
            for (Row r : rows) {
                switch (r.kind) {
                    case 0 -> {
                        int rw = width(r.text);
                        int tx = r == rows.get(0) ? (pw - rw) / 2 : ICON_X;
                        draw(g, r.text, tx, cy);
                    }
                    case 1 -> {
                        int ix = ICON_X;
                        if (realFont()) {
                            String gl = GLYPHS[r.pic];
                            if (r.pic == 0) {
                                if (shadow.on()) Draw.bold(g, gl, ix + 1, cy + 1, 0xFF003F00);
                                Draw.bold(g, gl, ix, cy, PIC_COLOURS[0]);
                            } else if (shadow.on()) Draw.textShadow(g, gl, ix, cy, PIC_COLOURS[r.pic]);
                            else Draw.text(g, gl, ix, cy, PIC_COLOURS[r.pic]);
                        } else {
                            icon(g, PICS[r.pic], ix, cy, PIC_COLOURS[r.pic]);
                        }
                        int tx = ix + iconSpace(r);
                        if (r.label != null) {
                            Draw.textShadow(g, r.label, tx, cy, 0xFFFFFFFF);
                            tx += Draw.width(r.label) + Draw.width(" ");
                        }
                        if (shadow.on()) Draw.textShadow(g, r.value, tx, cy, r.valueColour);
                        else Draw.text(g, r.value, tx, cy, r.valueColour);
                    }
                    case 3 -> {
                        int tx = ICON_X;
                        Draw.textShadow(g, r.label, tx, cy, PING_LABEL);
                        tx += Draw.width(r.label) + Draw.width(" ");
                        Draw.textShadow(g, r.value, tx, cy, r.valueColour);
                    }
                    default -> {}
                }
                cy += LINE;
            }
            g.pose().popMatrix();
        }
    }
}
