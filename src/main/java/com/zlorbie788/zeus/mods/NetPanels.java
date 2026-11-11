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

import java.util.Locale;

public final class NetPanels {
    private NetPanels() {}

    private static int pingColour(int ms) {
        return ms < 60 ? 0xFF5EE6A0 : ms < 120 ? 0xFFFFC857 : ms < 200 ? 0xFFFF8F4D : 0xFFFF5C6C;
    }

    public static final class NetInfo extends Module {
        private final Setting.Choice corner = Hud.corner(this, 0);
        private final Setting.Bool graph = add(new Setting.Bool("Graph", "A bar for every sample of the last while, coloured by how good the ping was.", true));
        private final Setting.Num seconds = add(new Setting.Num("Memory", "How many seconds the graph and the average cover.", 60, 15, 180, 5, " s")).showIf(() -> this.graph.on());
        private final Setting.Bool spread = add(new Setting.Bool("Lowest, average, highest", "The range of the ping over the memory.", true));
        private final Setting.Bool jitter = add(new Setting.Bool("Jitter", "How much the ping jumps around. High jitter feels like lag even when the ping is low.", true));
        private final Setting.Bool warn = add(new Setting.Bool("Warn when it is bad", "Tints the box red when the ping is over 250 ms or very jumpy.", true));
        private final int[] ring = new int[180];
        private int count;
        private int head;
        private int ticks;

        public NetInfo() {
            super("netinfo", "Net Info", "Your ping, how steady it is and a graph of the last minute.", Category.PANELS);
            anywhere();
        }

        @Override
        protected void onEnable() {
            count = 0;
            head = 0;
        }

        private int ping(Minecraft mc) {
            try {
                if (mc.getConnection() != null && mc.player != null) {
                    PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                    if (info != null) return Math.max(0, info.getLatency());
                }
            } catch (RuntimeException ignored) {
            }
            return -1;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (++ticks % 20 != 0) return;
            int p = ping(mc);
            if (p < 0) return;
            ring[head] = p;
            head = (head + 1) % ring.length;
            if (count < ring.length) count++;
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            int n = Math.min(count, seconds.i());
            int now = n == 0 ? Math.max(0, ping(mc)) : ring[(head - 1 + ring.length) % ring.length];
            int lo = Integer.MAX_VALUE;
            int hi = 0;
            long sum = 0;
            for (int i = 0; i < n; i++) {
                int v = ring[(head - 1 - i + ring.length * 2) % ring.length];
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
                sum += v;
            }
            int avg = n == 0 ? now : (int) (sum / n);
            double var = 0;
            for (int i = 0; i < n; i++) {
                double d = ring[(head - 1 - i + ring.length * 2) % ring.length] - avg;
                var += d * d;
            }
            int jit = n < 2 ? 0 : (int) Math.round(Math.sqrt(var / n));
            if (n == 0) lo = now;

            int rows = 1 + (spread.on() ? 1 : 0) + (jitter.on() ? 1 : 0);
            int gw = graph.on() ? 60 : 0;
            int bw = Math.max(Math.max(gw + Hud.PAD * 2, 100), Hud.captionWidth("Ping") + Hud.PAD * 2 + 20);
            int bh = rows * 12 + Hud.TOP + 3 + (graph.on() ? 20 : 0);
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            Hud.panel(g, at[0], at[1], bw, bh);
            if (warn.on() && (now > 250 || jit > 80)) g.fill(at[0] + 2, at[1], at[0] + bw, at[1] + bh, Draw.rgba(0xFFFF3B4D, 0.14f));
            Hud.header(g, at[0], at[1], bw, "Ping", null, pingColour(now));
            int y = at[1] + Hud.TOP;
            Draw.text(g, "Now", at[0] + Hud.PAD, y, Accent.DIM);
            Draw.textRight(g, now + " ms", at[0] + bw - Hud.PAD, y, pingColour(now));
            y += 12;
            if (spread.on()) {
                Draw.text(g, "Low / avg / high", at[0] + Hud.PAD, y, Accent.DIM);
                Draw.textRight(g, lo + "/" + avg + "/" + hi, at[0] + bw - Hud.PAD, y, Accent.TEXT);
                y += 12;
            }
            if (jitter.on()) {
                Draw.text(g, "Jitter", at[0] + Hud.PAD, y, Accent.DIM);
                Draw.textRight(g, "±" + jit + " ms", at[0] + bw - Hud.PAD, y, jit > 60 ? 0xFFFF8F4D : Accent.TEXT);
                y += 12;
            }
            if (graph.on()) {
                int gx = at[0] + Hud.PAD;
                int gy = at[1] + bh - 5;
                int gwid = bw - 14;
                int shown = Math.min(n, gwid / 2);
                int top = Math.max(100, hi);
                for (int i = 0; i < shown; i++) {
                    int v = ring[(head - 1 - i + ring.length * 2) % ring.length];
                    int bar = Math.max(1, Math.round(14f * Math.min(1f, v / (float) top)));
                    int x = gx + gwid - (i + 1) * 2;
                    g.fill(x, gy - bar, x + 1, gy, Draw.rgba(pingColour(v), 0.9f));
                }
                g.fill(gx, gy, gx + gwid, gy + 1, Draw.rgba(Accent.LINE, 1f));
            }
        }

        @Override
        public String info() {
            return count == 0 ? null : ring[(head - 1 + ring.length) % ring.length] + " ms";
        }
    }

    public static final class Session extends Module {
        private final Setting.Choice corner = Hud.corner(this, 2);
        private final Setting.Bool time = add(new Setting.Bool("Time played", "How long since you joined this world or server.", true));
        private final Setting.Bool distance = add(new Setting.Bool("Distance", "How far you have travelled (flat distance, teleports are not counted).", true));
        private final Setting.Bool speed = add(new Setting.Bool("Speed", "Your speed now and your top speed in blocks per second.", true));
        private final Setting.Bool pos = add(new Setting.Bool("Blocks per minute", "Your average travel speed over the last minute.", false));
        private long joined;
        private double dist;
        private double top;
        private double lastX;
        private double lastZ;
        private boolean have;
        private double now;
        private final double[] minute = new double[60];
        private int mi;
        private int ticks;
        private double secDist;
        private Object lastLevel;

        public Session() {
            super("session", "Session", "Time played, distance travelled and your top speed since you joined.", Category.PANELS);
            anywhere();
        }

        @Override
        protected void onEnable() {
            reset();
        }

        private void reset() {
            joined = System.currentTimeMillis();
            dist = 0;
            top = 0;
            have = false;
            java.util.Arrays.fill(minute, 0);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) return;
            if (mc.level != lastLevel) {
                lastLevel = mc.level;
                if (joined == 0 || System.currentTimeMillis() - joined > 2000) reset();
            }
            double x = mc.player.getX();
            double z = mc.player.getZ();
            if (have) {
                double d = Math.hypot(x - lastX, z - lastZ);
                if (d < 12) {
                    dist += d;
                    secDist += d;
                    now = d * 20.0;
                    top = Math.max(top, now);
                }
            }
            lastX = x;
            lastZ = z;
            have = true;
            if (++ticks % 20 == 0) {
                minute[mi] = secDist;
                mi = (mi + 1) % minute.length;
                secDist = 0;
            }
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            if (time.on()) {
                long s = (System.currentTimeMillis() - joined) / 1000;
                rows.add(new String[] {"Time", s >= 3600 ? String.format(Locale.ROOT, "%dh %02dm", s / 3600, s % 3600 / 60) : String.format(Locale.ROOT, "%dm %02ds", s / 60, s % 60)});
            }
            if (distance.on()) rows.add(new String[] {"Distance", dist >= 1000 ? String.format(Locale.ROOT, "%.2f km", dist / 1000) : Math.round(dist) + " m"});
            if (speed.on()) {
                rows.add(new String[] {"Speed", String.format(Locale.ROOT, "%.1f b/s", now)});
                rows.add(new String[] {"Top speed", String.format(Locale.ROOT, "%.1f b/s", top)});
            }
            if (pos.on()) {
                double sum = 0;
                for (double v : minute) sum += v;
                rows.add(new String[] {"Per minute", Math.round(sum) + " m"});
            }
            if (rows.isEmpty()) return;
            int bw = Hud.captionWidth("Session") + Hud.PAD * 2;
            for (String[] r : rows) bw = Math.max(bw, Draw.width(r[0]) + Draw.width(r[1]) + Hud.PAD * 2 + 14);
            int bh = rows.size() * 12 + Hud.TOP + 3;
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            Hud.panel(g, at[0], at[1], bw, bh);
            Hud.header(g, at[0], at[1], bw, "Session", null, Hud.accent());
            for (int i = 0; i < rows.size(); i++) {
                int y = at[1] + Hud.TOP + i * 12;
                Draw.text(g, rows.get(i)[0], at[0] + Hud.PAD, y, Accent.DIM);
                Draw.textRight(g, rows.get(i)[1], at[0] + bw - Hud.PAD, y, Accent.TEXT);
            }
        }
    }

    private static final java.util.Map<String, Integer> POPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> LAST_POP = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean popsOn;

    public static void totemPopped(net.minecraft.world.entity.Entity e) {
        if (!popsOn || e == null) return;
        String name = e.getName().getString();
        POPS.merge(name, 1, Integer::sum);
        LAST_POP.put(name, System.currentTimeMillis());
    }

    public static final class PopCounter extends Module {
        private final Setting.Choice corner = Hud.corner(this, 1);
        private final Setting.Num rows = add(new Setting.Num("Players listed", "How many players are listed, most pops first.", 5, 1, 10, 1, ""));
        private final Setting.Bool onlyNear = add(new Setting.Bool("Only players near you", "Hides players that are not in your view distance any more.", false));
        private final Setting.Bool chat = add(new Setting.Bool("Message when it happens", "A short pop-up each time someone pops a totem.", false));
        private final Setting.Num fadeAfter = add(new Setting.Num("Forget after", "A player drops off the list this many minutes after their last pop. 0 keeps everyone.", 10, 0, 60, 1, " min"));
        private int lastTotal;

        public PopCounter() {
            super("popcounter", "Pop Counter", "Counts totem pops per player, so you can see who is nearly out of totems.", Category.PANELS);
            anywhere();
        }

        @Override
        protected void onEnable() {
            popsOn = true;
            POPS.clear();
            LAST_POP.clear();
            lastTotal = 0;
        }

        @Override
        protected void onDisable() {
            popsOn = false;
        }

        @Override
        protected void onTick(Minecraft mc) {
            int total = 0;
            for (int v : POPS.values()) total += v;
            if (chat.on() && total > lastTotal) {
                String who = "";
                long best = 0;
                for (var e : LAST_POP.entrySet()) if (e.getValue() > best) {
                    best = e.getValue();
                    who = e.getKey();
                }
                Zeus.toast(who + " popped a totem (" + POPS.getOrDefault(who, 0) + ")", true);
            }
            lastTotal = total;
            if (fadeAfter.i() > 0) {
                long cut = System.currentTimeMillis() - fadeAfter.i() * 60_000L;
                LAST_POP.entrySet().removeIf(e -> {
                    if (e.getValue() < cut) {
                        POPS.remove(e.getKey());
                        return true;
                    }
                    return false;
                });
            }
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || mc.level == null) return;
            java.util.List<java.util.Map.Entry<String, Integer>> list = new java.util.ArrayList<>(POPS.entrySet());
            if (onlyNear.on()) {
                java.util.Set<String> near = new java.util.HashSet<>();
                for (var p : mc.level.players()) near.add(p.getName().getString());
                list.removeIf(e -> !near.contains(e.getKey()));
            }
            if (list.isEmpty()) return;
            list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            int n = Math.min(rows.i(), list.size());
            int bw = 70;
            for (int i = 0; i < n; i++) bw = Math.max(bw, Draw.width(list.get(i).getKey()) + 34);
            int bh = n * 11 + 17;
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            Hud.panel(g, at[0], at[1], bw, bh);
            Draw.text(g, "Totem pops", at[0] + 7, at[1] + 4, Accent.FAINT);
            for (int i = 0; i < n; i++) {
                int y = at[1] + 15 + i * 11;
                var e = list.get(i);
                boolean me = e.getKey().equals(mc.player.getName().getString());
                Draw.text(g, e.getKey(), at[0] + 7, y, me ? com.zlorbie788.zeus.mods.Hud.accent() : Accent.TEXT);
                int c = e.getValue();
                Draw.textRight(g, Integer.toString(c), at[0] + bw - 5, y, c >= 4 ? 0xFFFF5C6C : c >= 2 ? 0xFFFFC857 : Accent.TEXT);
            }
        }
    }
}
