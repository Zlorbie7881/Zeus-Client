package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Env;

import com.zlorbie788.zeus.core.Zeus;

import com.zlorbie788.zeus.core.Category;

import com.zlorbie788.zeus.core.Module;

import com.zlorbie788.zeus.core.Setting;

import com.zlorbie788.zeus.render.Projector;

import com.zlorbie788.zeus.ui.Draw;

import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.core.registries.BuiltInRegistries;

import com.zlorbie788.zeus.ui.BlockPickerScreen;

import com.zlorbie788.zeus.ui.BlockPickerScreen;

import net.minecraft.world.level.block.Block;

import net.minecraft.world.level.chunk.LevelChunk;

import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;

import java.util.HashMap;

import java.util.List;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import java.util.Set;

import java.util.LinkedHashSet;

public final class BlockEsp extends Module {

    private static final int[] COLORS = {

        0xFF9AA0A6, 0xFFD8AF93, 0xFFE77C56, 0xFFFFD700, 0xFFFF3B3B, 0xFF4A74FF, 0xFF4DEEEA,

        0xFF2EE66B, 0xFFFFA24D, 0xFFB06A4A, 0xFFFF7A18, 0xFFFF66FF, 0xFFFFFFFF

    };

    private final Setting.Num radius = add(new Setting.Num("Range", "How far around you it looks (only loaded chunks can be searched).", 48, 16, 256, 8, " blocks"));

    private final Setting.Bool[] kinds = new Setting.Bool[XRay.KINDS.length];

    private final com.zlorbie788.zeus.render.EspKit kit;

    private final Setting.Num maxBoxes = add(new Setting.Num("Most boxes shown", "The nearest ones are kept when there are more.", 150, 20, 600, 10, ""));

    private final Setting.Text extra = add(new Setting.Text("Extra blocks", "Any other blocks to mark, as id:colour separated by commas, e.g. beacon:cyan, ancient_debris:purple, *_spawner:#FF66CC. Colour names or #RRGGBB; * at the start or end of a name.", "", 600));

    private final Setting.Bool names = add(new Setting.Bool("Labels", "Name and distance on the nearest blocks.", true));

    private final Map<Long, int[][]> found = new HashMap<>();

    private final Map<Long, Long> scannedAt = new HashMap<>();

    private static final ConcurrentHashMap<Block, Integer> KIND = new ConcurrentHashMap<>();

    private int tick;

    private int shown;

    private String extraFrom = "\u0000";

    private final Map<String, Integer> extraList = new java.util.LinkedHashMap<>();

    private static final ConcurrentHashMap<Block, Integer> CUSTOM = new ConcurrentHashMap<>();

    private List<long[]> queue = new ArrayList<>();

    public BlockEsp() {

        super("blockesp", "Block ESP", "Finds ores, chests and spawners around you and marks them through walls.", Category.RENDER);

        light();

        for (int i = 0; i < kinds.length; i++) {

            boolean def = i >= 3 && i != 10;

            kinds[i] = add(new Setting.Bool(XRay.KINDS[i], "Mark " + XRay.KINDS[i].toLowerCase() + ".", def));

            kinds[i].onChange(this::reset);

        }

        kit = new com.zlorbie788.zeus.render.EspKit(this, 0, false);

        radius.onChange(this::reset);

        extra.onChange(this::reset);

        add(new Setting.Action("Pick blocks...", "Choose which ores and blocks to mark through walls, like X-Ray does.", this::openBlockPicker));

    }

    private void parseExtra() {

        String text = extra.get();

        if (text.equals(extraFrom)) return;

        extraFrom = text;

        extraList.clear();

        CUSTOM.clear();

        for (String part : text.split(",")) {

            String p = part.trim().toLowerCase(java.util.Locale.ROOT);

            if (p.isEmpty()) continue;

            int c = p.indexOf(':');

            String id = c < 0 ? p : p.substring(0, c).trim();

            if (id.startsWith("minecraft:")) id = id.substring(10);

            int color = 0;

            if (c >= 0) {

                String word = p.substring(c + 1).trim();

                if (word.startsWith("#")) {

                    try {

                        color = 0xFF000000 | Integer.parseInt(word.substring(1), 16);

                    } catch (NumberFormatException ignored) {

                        color = 0;

                    }

                } else {

                    for (int i = 1; i < com.zlorbie788.zeus.render.EspKit.COLOR_NAMES.length; i++) {

                        if (com.zlorbie788.zeus.render.EspKit.COLOR_NAMES[i].equalsIgnoreCase(word)) color = com.zlorbie788.zeus.render.EspKit.COLOR_VALUES[i];

                    }

                }

            }

            if (!id.isEmpty()) extraList.put(id, color == 0 ? 0xFF35E0D0 : color);

        }

    }

    private int customOf(Block b) {

        parseExtra();

        if (extraList.isEmpty()) return 0;

        Integer hit = CUSTOM.get(b);

        if (hit != null) return hit;

        int color = 0;

        try {

            String id = BuiltInRegistries.BLOCK.getKey(b).toString();

            id = id.substring(id.indexOf(':') + 1);

            Integer exact = extraList.get(id);

            if (exact != null) color = exact;

            for (Map.Entry<String, Integer> e : extraList.entrySet()) {

                if (color != 0) break;

                String key = e.getKey();

                boolean starts = key.endsWith("*");

                boolean ends = key.startsWith("*");

                if (starts && ends) {

                    if (key.length() > 2 && id.contains(key.substring(1, key.length() - 1))) color = e.getValue();

                } else if (starts) {

                    if (id.startsWith(key.substring(0, key.length() - 1))) color = e.getValue();

                } else if (ends) {

                    if (id.endsWith(key.substring(1))) color = e.getValue();

                }

            }

        } catch (RuntimeException ignored) {

            color = 0;

        }

        CUSTOM.put(b, color);

        return color;

    }

    private int kindFor(Block b) {

        int k = kindOf(b);

        if (k >= 0 && kinds[k].on()) return k;

        int c = customOf(b);

        return c != 0 ? 0x1000000 + (c & 0xFFFFFF) : -1;

    }

    public void mirrorKinds(int mask) {

        for (int i = 0; i < kinds.length; i++) kinds[i].set((mask & (1 << i)) != 0);

    }

    private void reset() {

        found.clear();

        scannedAt.clear();

    }

    @Override

    protected void onEnable() {

        reset();

    }

    @Override

    protected void onDisable() {

        reset();

    }

    private static int kindOf(Block b) {

        Integer k = KIND.get(b);

        if (k == null) {

            try {

                String id = BuiltInRegistries.BLOCK.getKey(b).toString();

                k = XRay.classify(id.substring(id.indexOf(':') + 1));

            } catch (RuntimeException e) {

                k = -1;

            }

            KIND.put(b, k);

        }

        return k;

    }

    private boolean wanted(Block b) {

        return kindFor(b) >= 0;

    }

    private static long key(int cx, int cz) {

        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);

    }

    @Override

    protected void onTick(Minecraft mc) {

        if (mc.level == null || mc.player == null) return;

        int pcx = mc.player.getBlockX() >> 4;

        int pcz = mc.player.getBlockZ() >> 4;

        int rc = (int) Math.ceil(radius.get() / 16.0);

        long now = System.currentTimeMillis();

        if (tick++ % 20 == 0) {

            queue = new ArrayList<>();

            for (int dx = -rc; dx <= rc; dx++) {

                for (int dz = -rc; dz <= rc; dz++) {

                    long k = key(pcx + dx, pcz + dz);

                    Long at = scannedAt.get(k);

                    if (at == null || now - at > 6000) queue.add(new long[] {dx * dx + dz * dz, pcx + dx, pcz + dz});

                }

            }

            queue.sort((a, b) -> Long.compare(a[0], b[0]));

            found.keySet().removeIf(k -> Math.abs((int) (k >> 32) - pcx) > rc + 2 || Math.abs((int) (long) k - pcz) > rc + 2);

            scannedAt.keySet().removeIf(k -> !found.containsKey(k) && now - scannedAt.get(k) > 60000);

        }

        long deadline = System.nanoTime() + 2_000_000L;

        while (!queue.isEmpty() && System.nanoTime() < deadline) {

            long[] c = queue.remove(0);

            scan(mc, (int) c[1], (int) c[2], now);

        }

    }

    private void scan(Minecraft mc, int cx, int cz, long now) {

        LevelChunk chunk = Env.chunk(mc, cx, cz);

        if (chunk == null) return;

        scannedAt.put(key(cx, cz), now);

        List<int[]> hits = new ArrayList<>();

        LevelChunkSection[] sections = chunk.getSections();

        int minY = chunk.getMinSectionY() << 4;

        for (int i = 0; i < sections.length && hits.size() < 200; i++) {

            LevelChunkSection sec = sections[i];

            if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(st -> wanted(st.getBlock()))) continue;

            int by = minY + i * 16;

            for (int y = 0; y < 16; y++) {

                for (int z = 0; z < 16; z++) {

                    for (int x = 0; x < 16; x++) {

                        Block b = sec.getBlockState(x, y, z).getBlock();

                        int k = kindFor(b);

                        if (k >= 0) hits.add(new int[] {(cx << 4) + x, by + y, (cz << 4) + z, k});

                    }

                }

            }

        }

        found.put(key(cx, cz), hits.toArray(new int[0][]));

    }

    @Override

    public String info() {

        return shown > 0 ? Integer.toString(shown) : null;

    }

    @Override

    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {

        shown = 0;

        if (mc.player == null || !Projector.prepare(mc, partial, w, h)) return;

        double ex = Projector.eyeX();

        double ey = Projector.eyeY();

        double ez = Projector.eyeZ();

        double r = radius.get();

        double r2 = r * r;

        List<double[]> near = new ArrayList<>();

        for (int[][] list : found.values()) {

            for (int[] hit : list) {

                double dx = hit[0] + 0.5 - ex;

                double dy = hit[1] + 0.5 - ey;

                double dz = hit[2] + 0.5 - ez;

                double d2 = dx * dx + dy * dy + dz * dz;

                if (d2 <= r2) near.add(new double[] {d2, hit[0], hit[1], hit[2], hit[3]});

            }

        }

        near.sort((a, b) -> Double.compare(a[0], b[0]));

        int cap = Math.min(near.size(), com.zlorbie788.zeus.ui.Perf.gameFast() ? Math.max(20, maxBoxes.i() * 2 / 3) : maxBoxes.i());

        float[] tmp = new float[2];

        int filled = 0;

        int lines = 0;

        for (int n = 0; n < cap; n++) {

            double[] hit = near.get(n);

            boolean custom = hit[4] >= 0x1000000;

            int color = custom ? 0xFF000000 | ((int) hit[4] & 0xFFFFFF) : COLORS[(int) hit[4]];

            double dist = Math.sqrt(hit[0]);

            float alpha = kit.alphaFor(dist, r);

            net.minecraft.world.phys.AABB box = com.zlorbie788.zeus.render.EspKit.shape(mc, (int) hit[1], (int) hit[2], (int) hit[3]);

            double cx = (box.minX + box.maxX) / 2;

            double cy = (box.minY + box.maxY) / 2;

            double cz = (box.minZ + box.maxZ) / 2;

            if (lines++ < 28) kit.tracer(g, w, h, cx, cy, cz, color, alpha);

            if (Projector.depth(cx, cy, cz) < -1.5) continue;

            if (!Projector.project(cx, cy, cz, tmp) || tmp[0] < -80 || tmp[0] > w + 80 || tmp[1] < -80 || tmp[1] > h + 80) continue;

            shown++;

            kit.box(g, box, color, alpha, filled++ < 36);

            if (names.on() && n < 8 && Projector.project(cx, box.maxY + 0.15, cz, tmp)) {

                kit.tag(g, custom ? mc.level.getBlockState(new net.minecraft.core.BlockPos((int) hit[1], (int) hit[2], (int) hit[3])).getBlock().getName().getString() : XRay.KINDS[(int) hit[4]], Math.round(dist) + "m", Math.round(tmp[0]), Math.round(tmp[1]) - 14, color, alpha, w, h);

            }

        }

    }

    private void openBlockPicker() {

        Minecraft mc = Minecraft.getInstance();

        if (mc == null || mc.screen == null) return;

        Set<String> current = new LinkedHashSet<>();

        for (String id : extraList.keySet()) current.add(id);

        mc.setScreen(new BlockPickerScreen(mc.screen, "Pick blocks for Block ESP", current, this::applyBlockPicks));

    }

    private void applyBlockPicks(Set<String> picked) {

        extra.set("");

        extraList.clear();

        CUSTOM.clear();

        for (String id : picked) {

            extra.set(extra.get() + (extra.get().isEmpty() ? "" : ", ") + id);

        }

        reset();

        Zeus.toast("Block ESP: " + picked.size() + " block" + (picked.size() == 1 ? "" : "s") + " selected", true);

    }

}
