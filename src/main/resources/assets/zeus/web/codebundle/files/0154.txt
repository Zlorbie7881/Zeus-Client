package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import com.zlorbie788.zeus.ui.Logo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Interface {
    private Interface() {}

    public static final class HudEditorLauncher extends Module {
        public HudEditorLauncher() {
            super("hudeditor", "HUD Editor", "Open the global editor to move, scale, hide and arrange Zeus HUD widgets.", Category.INTERFACE);
            panelOnly();
            add(new Setting.Action("Open HUD editor", "Opens the live HUD layout editor.", () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new com.zlorbie788.zeus.ui.HudEditorScreen(mc.screen));
            }));
        }
    }

    public static final class Watermark extends Module {
        public Watermark() {
            super("watermark", "Watermark", "The Zeus logo and name in the corner.", Category.PANELS);
            anywhere().onByDefault();
        }

        private com.zlorbie788.zeus.ui.Tex.Img logo;
        private boolean logoTried;

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            int x = 6;
            int y = 6;
            String text = "ZEUS";
            int tw = Draw.widthBold(text);
            int boxH = 20;
            int slot = 16;
            int bw = 2 + slot + 5 + tw + 7;
            Hud.record(x, y, bw, boxH);
            Hud.panel(g, x, y, bw, boxH);
            if (!logoTried) {
                logoTried = true;
                logo = com.zlorbie788.zeus.ui.Tex.resource("textures/gui/logo_z.png");
            }
            if (logo != null) logo.draw(g, x + 2, y + 2, slot, slot, 0xFFFFFFFF);
            else Logo.bolt(g, x + 2, y + 3, 14, Hud.accent());
            g.fill(x + 2 + slot + 2, y + 5, x + 2 + slot + 3, y + boxH - 5, Draw.rgba(Hud.accent(), 0.35f));
            Draw.bold(g, text, x + 2 + slot + 5, y + 6, 0xFFF2F6FF);
        }
    }

    public static final class ModuleList extends Module {
        private final Setting.Bool glow = add(new Setting.Bool("Colour bar", "A small bar in the module's category colour at the edge of every entry.", true));
        private final Setting.Bool details = add(new Setting.Bool("Show details", "Extra information after the name, like the speed.", true));
        private final Setting.Bool cost = add(new Setting.Bool("Show what each costs", "A small percentage after each name: how much of your frame time that module uses. Needs \"Show what each module costs\" in the Interface settings (on by default).", false));

        public ModuleList() {
            super("modulelist", "Module List", "The list of what is switched on, top right.", Category.PANELS);
            anywhere().onByDefault();
        }

        private Module[] rows = new Module[0];
        private String[] infos = new String[0];
        private int[] widths = new int[0];
        private int[] nameWidths = new int[0];
        private int count;
        private int version = -1;
        private long builtAt;
        private boolean builtDetails;

        private void rebuild() {
            long now = System.nanoTime();
            boolean det = details.on() || cost.on();
            if (version == Zeus.activeVersion() && det == builtDetails && (!det || now - builtAt < 250_000_000L)) return;
            version = Zeus.activeVersion();
            builtDetails = det;
            builtAt = now;
            List<Module> on = new ArrayList<>();
            for (Module m : Zeus.all()) {
                if (m.enabled() && m.toggleable() && m != this && m.category != Category.INTERFACE && m.category != Category.PANELS) on.add(m);
            }
            count = on.size();
            if (rows.length < count) {
                rows = new Module[count + 8];
                infos = new String[count + 8];
                widths = new int[count + 8];
                nameWidths = new int[count + 8];
            }
            String[] in = new String[count];
            int[] w = new int[count];
            int[] nw = new int[count];
            Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) {
                Module m = on.get(i);
                String info = details.on() ? m.info() : null;
                if (cost.on() && m.costPct >= 0.1f) {
                    String c = String.format(java.util.Locale.ROOT, "%.1f%%", m.costPct);
                    info = info == null ? c : info + " " + c;
                }
                in[i] = info;
                nw[i] = Draw.width(m.name);
                w[i] = nw[i] + (in[i] != null ? 4 + Draw.width(in[i]) : 0);
                order[i] = i;
            }
            java.util.Arrays.sort(order, (a, b) -> Integer.compare(w[b], w[a]));
            for (int i = 0; i < count; i++) {
                int k = order[i];
                rows[i] = on.get(k);
                infos[i] = in[k];
                widths[i] = w[k];
                nameWidths[i] = nw[k];
            }
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            rebuild();
            if (count == 0) {
                Hud.listHeight = 0;
                return;
            }
            // one slim pill per module, widest first, right-aligned to the screen edge; the category
            // colour is a small bar on the outside edge, the name is white and any detail is dim
            int rowH = 12;
            int gap = 1;
            int padX = 5;
            int barW = glow.on() ? 2 : 0;
            int widest = 0;
            for (int i = 0; i < count; i++) widest = Math.max(widest, widths[i]);
            int right = w - 6;
            int y = 6;
            int total = count * (rowH + gap) - gap;
            Hud.record(right - widest - padX * 2 - barW, y, widest + padX * 2 + barW, total);
            int bg = Hud.panelBg();
            for (int i = 0; i < count; i++) {
                int ry = y + i * (rowH + gap);
                int rw = widths[i] + padX * 2;
                int rx = right - barW - rw;
                int c = rows[i].category.color();
                Draw.rrect(g, rx, ry, rw + barW, rowH, 3, Draw.lighten(bg, 0.04f), Draw.darken(bg, 0.12f));
                if (barW > 0) Draw.rrect(g, right - barW, ry + 2, barW, rowH - 4, 1, c);
                Draw.text(g, rows[i].name, rx + padX, ry + 2, 0xFFF2F5FC);
                if (infos[i] != null) Draw.text(g, infos[i], rx + padX + nameWidths[i] + 4, ry + 2, 0xFF8B97B3);
            }
            Hud.listHeight = total;
        }
    }

    /** A compact card of label / value rows (no title bar): dim labels on the left, values on the right. */
    private static void card(GuiGraphics g, int x, int y, int bw, List<String[]> rows, String strongLabel) {
        int bh = rows.size() * 11 + 7;
        Hud.record(x, y, bw, bh);
        Hud.panel(g, x, y, bw, bh);
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            int ry = y + 5 + i * 11;
            Draw.text(g, r[0], x + 7, ry, Accent.DIM);
            if (r[0].equals(strongLabel)) Draw.bold(g, r[1], x + bw - 7 - Draw.widthBold(r[1]), ry, Accent.TEXT);
            else Draw.text(g, r[1], x + bw - 7 - Draw.width(r[1]), ry, Accent.TEXT);
        }
    }

    private static int cardWidth(List<String[]> rows, String strongLabel) {
        int w = 0;
        for (String[] r : rows) w = Math.max(w, Draw.width(r[0]) + 12 + (r[0].equals(strongLabel) ? Draw.widthBold(r[1]) : Draw.width(r[1])));
        return w + 14;
    }

    private static void row(GuiGraphics g, Minecraft mc, int x, int y, int w, String label, String value, boolean strong) {
        Draw.text(g, label, x, y, Accent.DIM);
        if (strong) Draw.bold(g, value, x + w - Draw.widthBold(value), y, Accent.TEXT);
        else Draw.text(g, value, x + w - Draw.width(value), y, Draw.mix(Accent.TEXT, Accent.DIM, 0.3f));
    }

    public static final class InfoHud extends Module {
        private final Setting.Bool coords = add(new Setting.Bool("Coordinates", "Where you are.", true));
        private final Setting.Bool facing = add(new Setting.Bool("Direction", "Which way you face.", true));
        private final Setting.Bool speed = add(new Setting.Bool("Speed", "How fast you move, in blocks per second.", true));
        private final Setting.Bool fps = add(new Setting.Bool("Frames per second", "How smooth the game runs.", true));
        private final Setting.Bool ping = add(new Setting.Bool("Ping", "Round trip time to the server (0 in singleplayer).", true));
        private long last;
        private float smooth = 60f;
        private double lx;
        private double lz;
        private double bps;

        public InfoHud() {
            super("infohud", "Info", "Coordinates, direction, speed and frame rate at the bottom left.", Category.PANELS);
            anywhere().onByDefault();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null) return;
            bps = Math.sqrt(sq(mc.player.getX() - lx) + sq(mc.player.getZ() - lz)) * 20.0;
            lx = mc.player.getX();
            lz = mc.player.getZ();
        }

        private static double sq(double v) {
            return v * v;
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            long now = System.nanoTime();
            if (last != 0) smooth += ((1e9f / Math.max(1, now - last)) - smooth) * 0.05f;
            last = now;
            if (now - linesAt > 100_000_000L || cached.isEmpty()) {
                linesAt = now;
                cached = buildRows(mc);
                // the width only grows while the panel is up, so it does not twitch as numbers change
                boxW = Math.max(boxW, cardWidth(cached, null));
            }
            List<String[]> rows = cached;
            if (rows.isEmpty()) return;
            int bh = rows.size() * 11 + 7;
            card(g, 6, h - bh - 6 - 40, boxW, rows, null);
        }

        private List<String[]> cached = List.of();
        private long linesAt;
        private int boxW;

        private List<String[]> buildRows(Minecraft mc) {
            List<String[]> rows = new ArrayList<>(5);
            // the Position panel already shows where you are: no second copy here
            boolean positionOn = false;
            for (com.zlorbie788.zeus.core.Module m : com.zlorbie788.zeus.core.Zeus.all()) {
                if (m instanceof Coordinates && m.enabled()) positionOn = true;
            }
            if (coords.on() && !positionOn) rows.add(new String[]{"XYZ", String.format(Locale.ROOT, "%.0f  %.0f  %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ())});
            if (facing.on()) {
                float yaw = ((mc.player.getYRot() % 360f) + 360f) % 360f;
                String[] names = {"South", "South-west", "West", "North-west", "North", "North-east", "East", "South-east"};
                rows.add(new String[]{"Facing", names[Math.round(yaw / 45f) % 8]});
            }
            if (speed.on()) rows.add(new String[]{"Speed", String.format(Locale.ROOT, "%.1f b/s", bps)});
            if (fps.on()) rows.add(new String[]{"FPS", Integer.toString(Math.round(smooth))});
            if (ping.on() && mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null && mc.getSingleplayerServer() == null) rows.add(new String[]{"Ping", info.getLatency() + " ms"});
            }
            return rows;
        }
    }

    public static final class Coordinates extends Module {
        private final Setting.Bool decimals = add(new Setting.Bool("Decimals", "Show the fraction of a block too.", false));
        private final Setting.Bool dimension = add(new Setting.Bool("Dimension", "A line under the position with the dimension you are in.", false));
        private final Setting.Bool chunk = add(new Setting.Bool("Chunk", "A line under the position with the chunk and where you are inside it.", false));
        private final Setting.Bool other = add(new Setting.Bool("Other side", "The same spot on the other side: divided by eight for the Nether, times eight for the Overworld.", false));

        public Coordinates() {
            super("coordinates", "Coordinates", "Your X, Y and Z, in their own small panel.", Category.PANELS);
            anywhere().onByDefault();
            alias("coords", "xyz", "position");
        }

        private List<String[]> cached = List.of();
        private long linesAt;
        private int boxW;

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            long now = System.nanoTime();
            if (now - linesAt > 100_000_000L || cached.isEmpty()) {
                linesAt = now;
                cached = buildRows(mc);
                int want = cardWidth(cached, "XYZ");
                boxW = want > boxW || want < boxW - 24 ? want : boxW;
            }
            List<String[]> rows = cached;
            if (rows.isEmpty()) return;
            card(g, 6, 30, boxW, rows, "XYZ");
        }

        private List<String[]> buildRows(Minecraft mc) {
            List<String[]> rows = new ArrayList<>(4);
            var p = mc.player;
            rows.add(new String[]{"XYZ", decimals.on()
                    ? String.format(Locale.ROOT, "%.1f  %.1f  %.1f", p.getX(), p.getY(), p.getZ())
                    : String.format(Locale.ROOT, "%d  %d  %d", p.getBlockX(), p.getBlockY(), p.getBlockZ())});
            if (dimension.on()) {
                String path = p.level().dimension().identifier().getPath();
                String name = path.equals("overworld") ? "Overworld" : path.equals("the_nether") ? "Nether" : path.equals("the_end") ? "The End" : path;
                rows.add(new String[]{"World", name});
            }
            if (chunk.on()) {
                rows.add(new String[]{"Chunk", (p.getBlockX() >> 4) + ", " + (p.getBlockZ() >> 4) + "   (" + (p.getBlockX() & 15) + ", " + (p.getBlockZ() & 15) + ")"});
            }
            if (other.on()) {
                String path = p.level().dimension().identifier().getPath();
                boolean nether = path.equals("the_nether");
                double ox = nether ? p.getX() * 8.0 : p.getX() / 8.0;
                double oz = nether ? p.getZ() * 8.0 : p.getZ() / 8.0;
                rows.add(new String[]{nether ? "Overworld" : "Nether", String.format(Locale.ROOT, "%.0f, %.0f", ox, oz)});
            }
            return rows;
        }
    }

    public static final class ThemeStudio extends Module {
        private final Setting.Pick theme = add(new Setting.Pick("Theme", "A named palette applied to the panel, the HUD and the module list.", com.zlorbie788.zeus.ui.Themes.IDS[0], com.zlorbie788.zeus.ui.Themes.ids(), com.zlorbie788.zeus.ui.Themes.labels()));
        private final Setting.Num tint = add(new Setting.Num("Surface tint", "How far the theme colour reaches into the HUD and panel backgrounds.", 55, 0, 100, 5, "%"));
        private final Setting.Bool glow = add(new Setting.Bool("Accent glow", "Soft glow behind accent bars, stripes and panels.", true));
        private final Setting.Action reset = add(new Setting.Action("Reset to default", "Storm blue, balanced tint and glow on.", this::reset));

        private boolean syncing;

        public ThemeStudio() {
            super("themestudio", "Theme Studio", "Switch between named palettes and set how far the theme reaches into the HUD.", Category.INTERFACE);
            anywhere();
            theme.onChange(this::apply);
            tint.onChange(this::apply);
            glow.onChange(this::apply);
        }

        private void apply() {
            if (syncing) return;
            syncing = true;
            try {
                com.zlorbie788.zeus.ui.UiSettings.ACCENT.set(theme.index());
                com.zlorbie788.zeus.ui.UiSettings.THEME_TINT.set(tint.get());
                com.zlorbie788.zeus.ui.UiSettings.HUD_GLOW.set(glow.on());
            } finally {
                syncing = false;
            }
            Zeus.markDirty();
        }

        private void reset() {
            syncing = true;
            try {
                theme.set(0);
                tint.set(55);
                glow.set(true);
            } finally {
                syncing = false;
            }
            apply();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (theme.index() != com.zlorbie788.zeus.ui.UiSettings.ACCENT.index()) theme.set(com.zlorbie788.zeus.ui.UiSettings.ACCENT.index());
            if (Math.abs(tint.get() - com.zlorbie788.zeus.ui.UiSettings.THEME_TINT.get()) > 0.001) tint.set(com.zlorbie788.zeus.ui.UiSettings.THEME_TINT.get());
            if (glow.on() != com.zlorbie788.zeus.ui.UiSettings.HUD_GLOW.on()) glow.set(com.zlorbie788.zeus.ui.UiSettings.HUD_GLOW.on());
        }
    }
}
