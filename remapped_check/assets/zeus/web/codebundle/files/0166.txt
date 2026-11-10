package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force anti-xray: finds the ores a server tried to hide, from the part of the chunk packet it cannot cheaply fake.
 *
 * <p>Anti-xray works by rewriting a section's block <em>data</em>: every hidden ore becomes stone, and fake ores are sprinkled
 * in. The section's <em>palette</em> is left alone, because rebuilding it per player costs the server real work. So the
 * server says "this 16x16x16 section contains diamond ore" while showing you none. That is the leak.</p>
 *
 * <p>A plugin can re-randomise on every send, which would make one reading worthless, so each section is read many times
 * and only an ore kind that leaks in most readings is reported. A section whose leaks keep changing is marked unstable and
 * shown differently, which is also how you can tell obfuscation from a real ore.</p>
 *
 * <p>A server in <em>replace</em> mode never puts the ore in the packet at all. Nothing client-side can recover data that
 * was never sent, so on those servers this reports nothing rather than pretending.</p>
 */
final class XrayLeak {
    private static final Block[] ORES = {
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
            Blocks.ANCIENT_DEBRIS
    };

    private static final String[] NAMES = {
            "coal", "coal", "iron", "iron", "copper", "copper", "gold", "gold",
            "redstone", "redstone", "lapis", "lapis", "diamond", "diamond",
            "emerald", "emerald", "nether gold", "quartz", "debris"
    };

    private static final int[] COLORS = {
            0xFF9AA3AD, 0xFF9AA3AD, 0xFFE0BFA8, 0xFFE0BFA8, 0xFFE08A5A, 0xFFE08A5A,
            0xFFFFD84D, 0xFFFFD84D, 0xFFFF4D5E, 0xFFFF4D5E, 0xFF4DA3FF, 0xFF4DA3FF,
            0xFF35E0D0, 0xFF35E0D0, 0xFF4DFF88, 0xFF4DFF88, 0xFFFFC46B, 0xFFFFF1D0, 0xFF8B5A2B
    };

    private static final Map<Block, Integer> INDEX = new ConcurrentHashMap<>();

    private static int index(Block block) {
        return INDEX.computeIfAbsent(block, b -> {
            for (int i = 0; i < ORES.length; i++) if (ORES[i] == b) return i;
            return -1;
        });
    }

    /** One 16x16x16 section that keeps claiming an ore it does not show. */
    private static final class Leak {
        final int x;
        final int y;
        final int z;
        int seen;
        long at;
        final int[] hits = new int[ORES.length];

        Leak(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Setting.Num range;
    private final Setting.Num resample;
    private final Setting.Num minSamples;
    private final Setting.Num markerRange;
    private final Setting.Bool boxes;
    private final Setting.Bool tracers;
    private final Setting.Info status;

    private final Map<Long, Leak> leaks = new HashMap<>();
    private final List<long[]> queue = new ArrayList<>();
    private int qi;
    private int tick;
    private int sampled;
    private int unstable;

    XrayLeak(Module owner) {
        this.range = owner.addSetting(new Setting.Num("Leak range", "How many chunks around you are read for the palette leak.", 6, 2, 12, 1, " chunks"));
        this.resample = owner.addSetting(new Setting.Num("Read again every", "How often the sections are read again. Each reading is one more vote on whether a leaked ore is real.", 20, 10, 200, 5, " ticks"));
        this.minSamples = owner.addSetting(new Setting.Num("Readings needed", "How many readings an ore kind has to leak in before it is drawn. More readings beat plugins that re-randomise every send.", 3, 2, 12, 1, ""));
        this.markerRange = owner.addSetting(new Setting.Num("Leak marker range", "Leaks closer than this are drawn.", 8, 2, 24, 1, " chunks"));
        this.boxes = owner.addSetting(new Setting.Bool("Leak boxes", "Draw a box around each section that hides ores.", true));
        this.tracers = owner.addSetting(new Setting.Bool("Leak line", "A line to the nearest leak.", true));
        this.status = owner.addSetting(new Setting.Info("Leak scanner", this::lines));
    }

    void reset() {
        leaks.clear();
        queue.clear();
        qi = 0;
        tick = 0;
        sampled = 0;
        unstable = 0;
    }

    void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (tick++ % Math.max(1, resample.i()) == 0 && qi >= queue.size()) buildQueue(mc);
        long deadline = System.nanoTime() + 2_000_000L;
        while (qi < queue.size() && System.nanoTime() < deadline) {
            long[] c = queue.get(qi++);
            sample(mc, (int) c[0], (int) c[1]);
        }
    }

    private void buildQueue(Minecraft mc) {
        queue.clear();
        qi = 0;
        int rc = range.i();
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        for (int dx = -rc; dx <= rc; dx++) {
            for (int dz = -rc; dz <= rc; dz++) {
                queue.add(new long[] {pcx + dx, pcz + dz});
            }
        }
        queue.sort((a, b) -> Long.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        leaks.keySet().removeIf(k -> {
            long kk = k;
            int lx = (int) (kk >> 40);
            int lz = (int) kk;
            return Math.abs(lx - pcx) > rc + 2 || Math.abs(lz - pcz) > rc + 2;
        });
    }

    /** One reading of one chunk: for every section, what the palette claims against what is really there. */
    private void sample(Minecraft mc, int cx, int cz) {
        LevelChunk chunk = Env.chunk(mc, cx, cz);
        if (chunk == null) return;
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            if (sec == null || sec.hasOnlyAir()) continue;
            if (!sec.maybeHas(st -> index(st.getBlock()) >= 0)) {
                leaks.remove(key(cx, minSectionY + i, cz));
                continue;
            }
            int claimed = 0;
            for (int k = 0; k < ORES.length; k++) {
                Block ore = ORES[k];
                if (sec.maybeHas(st -> st.is(ore))) claimed |= 1 << k;
            }
            if (claimed == 0) {
                leaks.remove(key(cx, minSectionY + i, cz));
                continue;
            }
            int shown = 0;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int k = index(sec.getBlockState(x, y, z).getBlock());
                        if (k >= 0) shown |= 1 << k;
                    }
                }
            }
            sampled++;
            int hidden = claimed & ~shown;
            long k2 = key(cx, minSectionY + i, cz);
            if (hidden == 0) {
                leaks.remove(k2);
                continue;
            }
            final int sy = minSectionY + i;
            Leak leak = leaks.computeIfAbsent(k2, k3 -> new Leak(cx << 4, sy << 4, cz << 4));
            leak.seen++;
            leak.at = System.currentTimeMillis();
            for (int k = 0; k < ORES.length; k++) if ((hidden & (1 << k)) != 0) leak.hits[k]++;
        }
    }

    /** The ore kinds that leaked in enough readings, as a bit mask. */
    private int confirmed(Leak leak) {
        int need = Math.max(minSamples.i(), (int) Math.ceil(leak.seen * 0.6));
        int mask = 0;
        for (int k = 0; k < ORES.length; k++) if (leak.hits[k] >= need) mask |= 1 << k;
        return mask;
    }

    private static String names(int mask) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < ORES.length; k++) {
            if ((mask & (1 << k)) == 0) continue;
            if (b.length() > 0) b.append(", ");
            b.append(NAMES[k]);
        }
        return b.length() == 0 ? "?" : b.toString();
    }

    private static int color(int mask) {
        for (int k = 0; k < ORES.length; k++) if ((mask & (1 << k)) != 0 && k >= 12) return COLORS[k];
        for (int k = 0; k < ORES.length; k++) if ((mask & (1 << k)) != 0) return COLORS[k];
        return 0xFFFF9F43;
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        if (leaks.isEmpty()) {
            out.add(sampled == 0 ? "Waiting for chunks with ores in them." : sampled + " sections read, no palette leak found.");
            out.add("A server that rewrites ore blocks completely (Paper engine-mode 1) cannot be beaten by any client: the ore was never sent.");
            return out;
        }
        int live = 0;
        int hot = 0;
        for (Leak leak : leaks.values()) {
            int mask = confirmed(leak);
            if (mask == 0) continue;
            live++;
            if (isUnstable(leak)) hot++;
        }
        unstable = hot;
        out.add(live + " sections hide ores they claim to have (" + hot + " unstable), " + sampled + " readings taken");
        out.add("Unstable means the leak changes between readings: the plugin re-randomises, and only what repeats is drawn.");
        return out;
    }

    /** The same section showing different hidden kinds over time means the plugin shuffles them. */
    private static boolean isUnstable(Leak leak) {
        int kinds = 0;
        for (int k = 0; k < ORES.length; k++) if (leak.hits[k] > 0) kinds++;
        return kinds > 2 && leak.seen > 2;
    }

    void draw(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (leaks.isEmpty() || mc.player == null) return;
        if (!Projector.prepare(mc, partial, w, h)) return;
        int rc = markerRange.i();
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        float[] s = new float[2];
        int drawn = 0;
        double bestDist = Double.MAX_VALUE;
        double bx = 0, by = 0, bz = 0;
        int bestColor = 0;
        for (Leak leak : leaks.values()) {
            int mask = confirmed(leak);
            if (mask == 0) continue;
            double cx = leak.x + 8 - px;
            double cz = leak.z + 8 - pz;
            double d = Math.sqrt(cx * cx + cz * cz) / 16.0;
            if (d > rc) continue;
            int col = color(mask);
            if (boxes.on() && drawn < 40) {
                Wire.box(g, leak.x, leak.y, leak.z, leak.x + 16, leak.y + 16, leak.z + 16, 1.4f, Draw.rgba(col, 0.75f), true);
                if (Projector.project(leak.x + 8, leak.y + 8, leak.z + 8, s)) {
                    Draw.text(g, names(mask) + " hidden", (int) s[0] + 4, (int) s[1], col);
                }
                drawn++;
            }
            if (d < bestDist) {
                bestDist = d;
                bx = leak.x + 8;
                by = leak.y + 8;
                bz = leak.z + 8;
                bestColor = col;
            }
        }
        if (tracers.on() && bestDist < Double.MAX_VALUE) {
            com.zlorbie788.zeus.render.EspKit.tracerLine(g, w, h, 0, bx, by, bz, bestColor, 0.9f);
        }
        if (unstable > 0) {
            Draw.text(g, "Leak: " + unstable + " sections re-randomise", 6, h - 120, 0xFFFFD84D);
        }
    }

    /** Packs a section: 24 bits of chunk x, 8 of section y, 24 of chunk z. */
    private static long key(int cx, int cy, int cz) {
        return ((long) (cx & 0xFFFFFF) << 40) | ((long) (cy & 0xFF) << 32) | (cz & 0xFFFFFFFFL);
    }
}
