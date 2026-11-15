package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Prof;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import com.zlorbie788.zeus.ui.Perf;
import com.zlorbie788.zeus.ui.UiSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Performance {
    private Performance() {}

    public static final class Page extends Module {
        public Page() {
            super("performance", "Performance", "How much of your frame rate Zeus may use: Balanced, Light or Minimal, and automatic lightening when the game lags.", Category.INTERFACE);
            panelOnly();
            alias("lag", "fps", "optimize", "optimise", "lightweight", "low end");
            add(new Setting.Info("Right now", Page::lines));
            add(new Setting.Action("Balanced", "Everything as designed. Scanners tick every tick.", () -> choose(0)));
            add(new Setting.Action("Light (recommended)", "Scanners tick every other tick, ESP boxes and lines are drawn with fewer pieces and no halos, HUD boxes are flat.", () -> choose(1)));
            add(new Setting.Action("Minimal", "Scanners tick every fourth tick, nothing but plain lines and flat boxes. For weak computers.", () -> choose(2)));
            add(new Setting.Action("Show what costs the most", "Switches the Performance Monitor on. It lists the modules that use the most of your frame time.", () -> {
                Module m = Zeus.get("perfmonitor");
                if (m != null && !m.enabled()) m.setEnabled(true);
            }));
            add(new Setting.Action("Switch off the 3 costliest", "Reads the Performance Monitor and switches off the three modules that use the most. Switch the monitor on and play for a few seconds first.", Page::killTop));
        }

        private static void choose(int mode) {
            UiSettings.GAME_MODE.set(mode);
            Perf.refresh();
            Zeus.toast("Performance mode: " + UiSettings.GAME_MODE.value(), true);
        }

        private static void killTop() {
            int n = 0;
            for (Prof.Row r : Prof.report()) {
                if (n >= 3) break;
                Module m = r.module();
                if (m.category == Category.INTERFACE || m.category == Category.PANELS || !m.enabled() || !m.toggleable()) continue;
                m.setEnabled(false);
                n++;
            }
            Zeus.toast(n == 0 ? "Nothing to switch off yet. Switch the Performance Monitor on and play a few seconds first." : "Switched off " + n + " costly module" + (n == 1 ? "" : "s"), n > 0);
        }

        private static List<String> lines() {
            List<String> out = new ArrayList<>();
            String[] names = {"Balanced", "Light", "Minimal"};
            out.add("Chosen: " + UiSettings.GAME_MODE.value() + (UiSettings.AUTO_LIGHTEN.on() ? " (lightens by itself when the game lags)" : ""));
            out.add("In effect: " + names[Math.max(0, Math.min(2, Perf.level()))] + ", scanners tick every " + Perf.stride() + (Perf.stride() == 1 ? " tick" : " ticks"));
            out.add("Frame rate: " + Brain.ctx().fps);
            int on = 0;
            for (Module m : Zeus.all()) if (m.enabled() && m.toggleable()) on++;
            out.add(on + " modules are on");
            return out;
        }
    }

    public static final class Monitor extends Module {
        private final Setting.Choice corner = Hud.corner(this, 1);
        private final Setting.Num rows = add(new Setting.Num("Modules listed", "How many of the costliest modules are listed.", 6, 3, 12, 1, ""));
        private final Setting.Bool hideZeus = add(new Setting.Bool("Hide the small ones", "Leaves out modules that use less than a tenth of a percent.", true));

        public Monitor() {
            super("perfmonitor", "Performance Monitor", "Shows how much of your frame time each Zeus module uses. Off means no measuring at all.", Category.PANELS);
            anywhere();
            alias("profiler", "fps cost", "lag monitor");
        }

        @Override
        protected void onEnable() {
            Prof.hold("monitor", true);
        }

        @Override
        protected void onDisable() {
            Prof.hold("monitor", false);
        }

        private long lastNs;
        private float frameMs = 16.7f;

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            long now = System.nanoTime();
            if (lastNs != 0) frameMs += ((now - lastNs) / 1e6f - frameMs) * 0.05f;
            lastNs = now;
            List<Prof.Row> report = Prof.report();
            List<Prof.Row> pick = new ArrayList<>(rows.i());
            double zeusMs = 0;
            for (Prof.Row r : report) zeusMs += r.msPerFrame();
            for (Prof.Row r : report) {
                if (pick.size() >= rows.i()) break;
                if (hideZeus.on() && r.percent() < 0.1) break;
                pick.add(r);
            }
            double total = Prof.total();
            int tc = total < 3 ? Accent.OK : total < 8 ? Accent.WARN : Accent.BAD;
            String head = String.format(Locale.ROOT, "%.1f ms frame  \u00b7  Zeus %.2f ms", frameMs, zeusMs);
            int bw = Math.max(Draw.width(head) + 16, 150);
            for (Prof.Row r : pick) bw = Math.max(bw, Draw.width(r.module().name) + Draw.width("00.0%  0.00 ms") + 24);
            int rowH = 14;
            int bh = 8 + 12 + 6 + Math.max(1, pick.size()) * rowH + 4;
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            int x = at[0], y = at[1];
            Hud.panel(g, x, y, bw, bh);
            // headline: frame time and how much of it is Zeus, with a share bar
            Draw.text(g, head, x + 8, y + 6, Accent.TEXT);
            int barW = bw - 16;
            float share = (float) Math.min(1.0, zeusMs / Math.max(0.1, frameMs));
            Draw.rrect(g, x + 8, y + 18, barW, 3, 1, Draw.rgba(0xFFFFFFFF, 0.10f));
            Draw.rrect(g, x + 8, y + 18, Math.max(2, Math.round(barW * share)), 3, 1, tc);
            int ry = y + 26;
            if (pick.isEmpty()) Draw.text(g, "Measuring...", x + 8, ry + 2, Accent.DIM);
            double top = pick.isEmpty() ? 1 : Math.max(0.01, pick.get(0).percent());
            for (Prof.Row r : pick) {
                int c = r.percent() < 1 ? Accent.OK : r.percent() < 4 ? Accent.WARN : Accent.BAD;
                String val = String.format(Locale.ROOT, "%.1f%%  %.2f ms", r.percent(), r.msPerFrame());
                Draw.text(g, Draw.fit(r.module().name, barW - Draw.width(val) - 6), x + 8, ry, Accent.TEXT);
                Draw.textRight(g, val, x + bw - 8, ry, c);
                Draw.rrect(g, x + 8, ry + 10, barW, 2, 1, Draw.rgba(0xFFFFFFFF, 0.06f));
                Draw.rrect(g, x + 8, ry + 10, Math.max(2, (int) Math.round(barW * r.percent() / top)), 2, 1, Draw.rgba(c, 0.9f));
                ry += rowH;
            }
        }
    }

    public static final class Guard extends Module {
        private final Setting.Num below = add(new Setting.Num("Switch off below", "The frame rate under which the guard acts. It needs to stay under for three seconds.", 40, 15, 200, 5, " fps"));
        private final Setting.Num above = add(new Setting.Num("Switch back on above", "The frame rate that has to hold for ten seconds before the modules it switched off come back.", 70, 20, 300, 5, " fps"));
        private final Setting.Num batch = add(new Setting.Num("Switch off at a time", "How many modules go off each time the frame rate is still too low.", 2, 1, 5, 1, ""));
        private final Setting.Num most = add(new Setting.Num("Switch off at most", "The most modules the guard keeps off at once.", 5, 1, 12, 1, ""));
        private final Setting.Num retry = add(new Setting.Num("Wait before trying again", "Seconds it waits after bringing modules back before it may switch anything off again.", 30, 10, 300, 5, " s"));
        private final Setting.Choice scope = add(new Setting.Choice("Which modules", "Heavy only: ESPs, finders and scanners. Any: every module except the ones in your keep list (the guard itself, the menu and the HUD pages are never touched).", 0, "Heavy only", "Any"));
        private final Setting.Text keep = add(new Setting.Text("Never switch off", "Module names or ids, separated by commas, that the guard must leave alone.", "", 120));
        private final Setting.Bool tell = add(new Setting.Bool("Tell me", "Shows a message when it switches modules off or back on.", true));
        private volatile String status = "Watching your frame rate.";
        private final Setting.Info state = addSetting(new Setting.Info("Now", () -> List.of(status)));

        private final List<Module> off = new ArrayList<>();
        private int low;
        private int good;
        private int sinceOff;
        private int cooldown;
        private int bounces;
        private boolean giveUp;
        private long lastAt;

        public Guard() {
            super("perfguard", "Performance Guard", "Switches the costliest heavy modules off when your frame rate drops, and back on when it recovers.", Category.INTERFACE);
            anywhere();
            alias("auto disable", "auto off", "fps guard", "lag guard", "heavy modules");
        }

        @Override
        protected void onEnable() {
            Prof.hold("guard", true);
            off.clear();
            low = good = sinceOff = cooldown = bounces = 0;
            giveUp = false;
            status = "Watching your frame rate.";
        }

        @Override
        protected void onDisable() {
            Prof.hold("guard", false);
            restore(false);
        }

        private boolean kept(Module m) {
            if (m == this || !m.enabled() || !m.toggleable() || m.category == Category.INTERFACE || m.category == Category.PANELS) return true;
            if (scope.index() == 0 && !m.isLight()) return true;
            String list = keep.get().toLowerCase(Locale.ROOT);
            if (list.isBlank()) return false;
            for (String part : list.split(",")) {
                String p = part.trim();
                if (!p.isEmpty() && (p.equals(m.id.toLowerCase(Locale.ROOT)) || p.equals(m.name.toLowerCase(Locale.ROOT)))) return true;
            }
            return false;
        }

        private void restore(boolean say) {
            int n = 0;
            for (Module m : off) {
                if (!m.enabled()) {
                    m.setEnabledQuiet(true);
                    n++;
                }
            }
            off.clear();
            if (say && n > 0 && tell.on()) Zeus.toast("Performance Guard: " + n + " module" + (n == 1 ? "" : "s") + " back on", true);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.level == null || mc.player == null || mc.screen != null) return;
            if (++sinceOff < 20) return;
            sinceOff = 0;
            int fps = Brain.ctx().fps;
            if (fps <= 0) return;
            if (cooldown > 0) cooldown--;
            off.removeIf(Module::enabled);
            if (fps < below.i()) {
                good = 0;
                if (++low >= 3 && cooldown == 0 && off.size() < most.i()) {
                    low = 0;
                    switchOff(fps);
                }
            } else {
                low = 0;
                if (!off.isEmpty() && fps >= above.i()) {
                    if (++good >= 10 && !giveUp) {
                        good = 0;
                        bounces++;
                        if (bounces >= 3) {
                            giveUp = true;
                            status = "These keep pulling the frame rate down, so they stay off. Switch them on yourself if you want them.";
                            if (tell.on()) Zeus.toast("Performance Guard: the modules stay off (they keep lowering your frame rate)", false);
                            off.clear();
                        } else {
                            restore(true);
                            cooldown = retry.i();
                            status = "Modules are back on. Watching.";
                        }
                    }
                } else good = 0;
            }
            if (off.isEmpty() && !giveUp && status.startsWith("Switched off")) status = "Watching your frame rate.";
        }

        private void switchOff(int fps) {
            List<Module> candidates = new ArrayList<>();
            for (Module m : Zeus.all()) if (!kept(m)) candidates.add(m);
            if (candidates.isEmpty()) {
                status = "Frame rate " + fps + ", but nothing is left that the guard may switch off.";
                return;
            }
            candidates.sort((a, b) -> Float.compare(b.costPct, a.costPct));
            int n = 0;
            StringBuilder names = new StringBuilder();
            for (Module m : candidates) {
                if (n >= batch.i() || off.size() >= most.i()) break;
                m.setEnabledQuiet(false);
                off.add(m);
                names.append(n == 0 ? "" : ", ").append(m.name);
                n++;
            }
            status = "Switched off " + names + " at " + fps + " fps.";
            if (tell.on()) Zeus.toast("Performance Guard: " + names + " off (" + fps + " fps)", false);
        }
    }
}
