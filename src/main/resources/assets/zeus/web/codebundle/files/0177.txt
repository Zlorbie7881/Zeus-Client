package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.EspKit;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerBypass extends Module {
    private final Setting.Bool vanish = add(new Setting.Bool("Vanished players", "Watch for a player entity that disappears while the player is still in the tab list.", true));
    private final Setting.Bool lights = add(new Setting.Bool("Light with no owner", "Deep chunk sections that are lit but hold no light source you can see: somebody's torch, or somebody's base.", true));
    private final Setting.Bool activity = add(new Setting.Bool("Blocks changing", "Chunks where blocks are changing with nobody visible in them.", true));
    private final Setting.Num range = add(new Setting.Num("Range", "How many chunks around you are checked.", 10, 3, 20, 1, " chunks"));
    private final Setting.Num minLight = add(new Setting.Num("Light level", "How bright a lit pocket has to be before it counts.", 7, 3, 15, 1, "")).showIf(() -> this.lights.on());
    private final Setting.Bool draw = add(new Setting.Bool("Mark them in the world", "A box and a name at every guess.", true));
    private final Setting.Bool alert = add(new Setting.Bool("Tell me", "A toast the first time a vanished player is caught.", true));
    private final Setting.Info found = add(new Setting.Info("What it found", this::lines));

    private final Map<UUID, double[]> vanished = new HashMap<>();

    private final Map<UUID, double[]> seenAt = new HashMap<>();

    private final Map<Long, double[]> guesses = new HashMap<>();
    private final List<long[]> queue = new ArrayList<>();
    private int qi;
    private int tick;

    public PlayerBypass() {
        super("playerbypass", "Player Bypass", "Finds vanished players and hidden activity from light, tab list and block changes.", Category.SCANNER);
        light();
        hudCard();
    }

    @Override
    protected void onEnable() {
        vanished.clear();
        seenAt.clear();
        guesses.clear();
        queue.clear();
        qi = 0;
    }

    @Override
    protected void onDisable() {
        vanished.clear();
        seenAt.clear();
        guesses.clear();
        queue.clear();
        qi = 0;
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (vanish.on()) watchVanish(mc);
        if (!lights.on() && !activity.on()) return;
        if (tick++ % 40 == 0 && qi >= queue.size()) buildQueue(mc);
        long deadline = System.nanoTime() + 1_500_000L;
        while (qi < queue.size() && System.nanoTime() < deadline) {
            long[] c = queue.get(qi++);
            check(mc, (int) c[0], (int) c[1]);
        }
    }

    private void watchVanish(Minecraft mc) {
        Set<UUID> now = new HashSet<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            now.add(p.getUUID());
            vanished.remove(p.getUUID());
            seenAt.put(p.getUUID(), new double[] {p.getX(), p.getY(), p.getZ(), p.getHealth()});
        }
        if (mc.getConnection() == null) return;
        Set<UUID> online = new HashSet<>();
        for (var info : mc.getConnection().getOnlinePlayers()) online.add(info.getProfile().id());
        for (Map.Entry<UUID, double[]> e : seenAt.entrySet()) {
            UUID id = e.getKey();
            if (now.contains(id) || !online.contains(id)) continue;
            double[] p = e.getValue();
            if (p[3] <= 0) continue;
            if (vanished.putIfAbsent(id, new double[] {p[0], p[1], p[2]}) == null && alert.on()) {
                com.zlorbie788.zeus.core.Zeus.toast("A player left the world without leaving the server: vanished", true);
            }
        }
        vanished.keySet().removeIf(id -> !online.contains(id));
        seenAt.keySet().removeIf(id -> !online.contains(id));
    }

    private void buildQueue(Minecraft mc) {
        queue.clear();
        qi = 0;
        int rc = range.i();
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        for (int dx = -rc; dx <= rc; dx++) {
            for (int dz = -rc; dz <= rc; dz++) queue.add(new long[] {pcx + dx, pcz + dz});
        }
        queue.sort(Comparator.comparingLong(a -> a[0] * a[0] + a[1] * a[1]));
    }

    private void check(Minecraft mc, int cx, int cz) {
        LevelChunk chunk = Env.chunk(mc, cx, cz);
        long key = key(cx, cz);
        if (chunk == null) {
            guesses.remove(key);
            return;
        }
        double lit = 0;
        int lightAt = 0;
        if (lights.on()) {
            LevelChunkSection[] sections = chunk.getSections();
            int minY = chunk.getMinSectionY() << 4;
            int surface = Brain.surface(mc, (cx << 4) + 8, (cz << 4) + 8);
            int need = minLight.i();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection sec = sections[i];
                if (sec == null || sec.hasOnlyAir()) continue;
                int by = minY + i * 16;
                if (surface != Integer.MIN_VALUE && by > surface - 12) continue;
                if (sec.maybeHas(st -> st.is(Blocks.TORCH) || st.is(Blocks.LANTERN) || st.is(Blocks.GLOWSTONE)
                        || st.is(Blocks.SEA_LANTERN) || st.is(Blocks.SHROOMLIGHT) || st.is(Blocks.CAMPFIRE)
                        || st.is(Blocks.LAVA) || st.is(Blocks.FIRE))) continue;
                for (int y = 0; y < 16; y += 4) {
                    for (int z = 0; z < 16; z += 4) {
                        for (int x = 0; x < 16; x += 4) {
                            if (!sec.getBlockState(x, y, z).isAir()) continue;
                            int light = mc.level.getBrightness(LightLayer.BLOCK, new net.minecraft.core.BlockPos((cx << 4) + x, by + y, (cz << 4) + z));
                            if (light >= need) {
                                lit++;
                                if (lightAt == 0) lightAt = by + y;
                            }
                        }
                    }
                }
            }
        }
        double heat = activity.on() ? Activity.heat(cx, cz) : 0;
        if (lit == 0 && heat <= 0) {
            guesses.remove(key);
            return;
        }
        guesses.put(key, new double[] {lit, heat, cx * 16 + 8, lightAt, cz * 16 + 8});
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (!enabled()) {
            out.add("Switch it on to watch for hidden players.");
            return out;
        }
        if (mc.player == null) return out;
        for (Map.Entry<UUID, double[]> e : vanished.entrySet()) {
            double[] p = e.getValue();
            out.add(String.format(Locale.ROOT, "Vanished nearby: last sent at %d, %d (%dm away)",
                    Math.round(p[0]), Math.round(p[2]), Math.round(Math.hypot(p[0] - mc.player.getX(), p[2] - mc.player.getZ()))));
        }
        List<Map.Entry<Long, double[]>> list = new ArrayList<>(guesses.entrySet());
        list.sort((a, b) -> Double.compare(score(b.getValue()), score(a.getValue())));
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            double[] g = list.get(i).getValue();
            String why = g[0] > 0 ? "lit air" + (g[1] > 0 ? " and blocks changing" : "") : "blocks changing";
            int d = (int) Math.round(Math.hypot(g[2] - mc.player.getX(), g[4] - mc.player.getZ()));
            out.add(String.format(Locale.ROOT, "%s   %d, %d   y %d   %dm", why, Math.round(g[2]), Math.round(g[4]), (int) g[3], d));
        }
        if (list.isEmpty() && vanished.isEmpty()) out.add("Nothing hidden in " + guesses.size() + " chunks checked.");
        return out;
    }

    private static double score(double[] g) {
        return g[0] * 2 + g[1];
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (!draw.on() || mc.player == null) return;
        if (guesses.isEmpty() && vanished.isEmpty()) return;
        if (!Projector.prepare(mc, partial, w, h)) return;
        float[] s = new float[2];
        int shown = 0;
        for (double[] g2 : guesses.values()) {
            if (shown++ >= 12) break;
            int col = g2[0] > 0 ? 0xFFFFD84D : 0xFF4DA3FF;
            double y0 = Math.max(-64, g2[3] - 4);
            Wire.box(g, g2[2] - 6, y0, g2[4] - 6, g2[2] + 6, y0 + 8, g2[4] + 6, 1.2f, Draw.rgba(col, 0.6f), true);
            if (Projector.project(g2[2], g2[3] + 1, g2[4], s)) {
                Draw.text(g, g2[0] > 0 ? "lit, nobody visible" : "blocks changing", (int) s[0] + 4, (int) s[1], col);
            }
        }
        for (double[] p : vanished.values()) {
            Wire.box(g, p[0] - 1, p[1], p[2] - 1, p[0] + 1, p[1] + 2, p[2] + 1, 1.6f, Draw.rgba(0xFFFF4D5E, 0.9f), true);
        }
    }

    @Override
    public String info() {
        int n = guesses.size() + vanished.size();
        return n == 0 ? null : Integer.toString(n);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
