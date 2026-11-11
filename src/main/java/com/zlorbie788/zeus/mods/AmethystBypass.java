package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.EspKit;
import com.zlorbie788.zeus.render.Projector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AmethystBypass extends Module {
    private static final String BYPASS = "Bypass";
    private static final String LIGHT = "Light";
    private static final String GLOW = "ANBS+ Scan";

    private static final int PINK = 0xFFC882FF;
    private static final long SCAN_NANOS = 1_600_000L;
    private static final int MAX_GLOW_MARKS = 200;

    private final Setting.Num minCells = add(new Setting.Num("Min cells",
            "How many dark cells a geode must show before the chunk is marked by the ANBS+ scan.",
            12, 1, 100, 1, ""));
    private final Setting.Num radius = add(new Setting.Num("Range",
            "How far around you it looks. Only chunks the game has been sent can be searched.",
            64, 32, 256, 16, " blocks"));
    private final Setting.Num maxBoxes = add(new Setting.Num("Most boxes shown",
            "The nearest ones are kept when there are more.", 200, 20, 400, 10, ""));
    private final Setting.Bool names = add(new Setting.Bool("Labels",
            "Name and distance on the nearest finds.", true));
    private final EspKit kit;

    private final Map<Long, long[]> hits = new HashMap<>();
    private final Map<Long, Long> scannedAt = new HashMap<>();
    private final GlowScan glow = new GlowScan();
    private List<long[]> queue = new ArrayList<>();
    private int tick;
    private int shown;

    public AmethystBypass() {
        super("amethystesp", "Amethyst ESP",
                "Marks amethyst geodes even where the server hid the blocks, using the block list and the light it sent.",
                Category.SCANNER);
        light();
        alias("amethyst", "amethyst bypass", "geode", "geode finder");
        kit = new EspKit(this, 0, false);
        radius.onChange(this::reset);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private void reset() {
        hits.clear();
        scannedAt.clear();
        queue = new ArrayList<>();
    }

    @Override
    protected void onEnable() {
        reset();
        tick = 0;
    }

    @Override
    protected void onDisable() {
        reset();
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
                    if (at == null || now - at > 3000) queue.add(new long[] {dx * dx + dz * dz, pcx + dx, pcz + dz});
                }
            }
            queue.sort((a, b) -> Long.compare(a[0], b[0]));
            hits.keySet().removeIf(k -> Math.abs((int) (k >> 32) - pcx) > rc + 1 || Math.abs((int) (long) k - pcz) > rc + 1);
            scannedAt.keySet().removeIf(k -> !hits.containsKey(k) && now - scannedAt.get(k) > 60000);
        }
        long deadline = System.nanoTime() + Brain.budget(SCAN_NANOS);
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            long[] c = queue.remove(0);
            scan(mc, (int) c[1], (int) c[2], now);
        }
    }

    private void scan(Minecraft mc, int cx, int cz, long now) {
        LevelChunk chunk = Env.chunk(mc, cx, cz);
        if (chunk == null) return;
        scannedAt.put(key(cx, cz), now);
        List<Long> out = new ArrayList<>();
        BlockPos marker = bypassMarker(chunk);
        if (marker != null) out.add(marker.asLong());
        lightScan(mc.level, chunk, out);
        int cells = glow.scan(mc.level, chunk, out);
        if (cells < minCells.i()) out.clear();
        else if (out.size() > MAX_GLOW_MARKS) out.subList(MAX_GLOW_MARKS, out.size()).clear();
        long[] packed = new long[out.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = out.get(i);
        hits.put(key(cx, cz), packed);
    }

    private static BlockPos bypassMarker(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            if (sec == null || !sec.getStates().maybeHas(AmethystBypass::isBud)) continue;
            int baseY = (minSectionY + i) * 16;
            BlockPos marker = pickMarker(chunk, sec, baseY);
            return marker != null ? marker : new BlockPos(chunk.getPos().x << 4, baseY + 8, chunk.getPos().z << 4);
        }
        return null;
    }

    private static BlockPos pickMarker(LevelChunk chunk, LevelChunkSection sec, int baseY) {
        ChunkPos cp = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int y = 0; y < 16; y++) {
            int wy = baseY + y;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!sec.getBlockState(x, y, z).isAir()) continue;
                    int wx = (cp.x << 4) + x;
                    int wz = (cp.z << 4) + z;
                    boolean onGeode = false;
                    for (Direction d : Direction.values()) {
                        int nx = wx + d.getStepX();
                        int nz = wz + d.getStepZ();
                        if ((nx >> 4) != cp.x || (nz >> 4) != cp.z) continue;
                        cursor.set(nx, wy + d.getStepY(), nz);
                        Block b = chunk.getBlockState(cursor).getBlock();
                        if (b == Blocks.AMETHYST_BLOCK || b == Blocks.BUDDING_AMETHYST) {
                            onGeode = true;
                            break;
                        }
                    }
                    if (!onGeode) continue;
                    int score = scatter(wx, wy, wz);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new BlockPos(wx, wy, wz);
                    }
                }
            }
        }
        return best;
    }

    static int scatter(int x, int y, int z) {
        long h = (long) x * -7046029254386353131L ^ (long) y * -4417276706812531889L ^ (long) z * 1609587929392839161L;
        h ^= h >>> 29;
        h *= -4658895280553007687L;
        h ^= h >>> 32;
        return Math.floorMod(h, 100);
    }

    private static void lightScan(ClientLevel level, LevelChunk chunk, List<Long> out) {
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        LightCache light = new LightCache(level, chunk.getPos().x, chunk.getPos().z, minSectionY, sections.length);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().x << 4;
        int minZ = chunk.getPos().z << 4;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            if (sec == null || sec.hasOnlyAir() || light.quiet(i) || !sec.getStates().maybeHas(AmethystBypass::isLining)) continue;
            int baseY = (minSectionY + i) * 16;
            for (int y = 0; y < 16; y++) {
                int wy = baseY + y;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = sec.getBlockState(x, y, z);
                        if (state.getLightEmission() > 0) continue;
                        int wx = minX + x;
                        int wz = minZ + z;
                        int value = light.get(wx, wy, wz);
                        int brightest = light.brightestAround(wx, wy, wz);
                        if (value == 0) {
                            if ((brightest != 4 && brightest != 3) || !isOpen(state)) continue;
                            if (!touchesLining(level, cursor, wx, wy, wz) || !litOpening(level, light, cursor, wx, wy, wz, brightest)) continue;
                        } else {
                            if (!isGrowthLight(value) || brightest >= value) continue;
                            if (!touchesLining(level, cursor, wx, wy, wz)
                                    || value == 4 && besideDarkOpening(level, light, cursor, wx, wy, wz)) continue;
                        }
                        out.add(BlockPos.asLong(wx, wy, wz));
                    }
                }
            }
        }
    }

    private static final class LightCache {
        private final LayerLightEventListener listener;
        private final int chunkX;
        private final int chunkZ;
        private final int minSectionY;
        private final int sectionCount;
        private final DataLayer[] layers;
        private final boolean[] fetched;

        LightCache(ClientLevel level, int chunkX, int chunkZ, int minSectionY, int sectionCount) {
            this.listener = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minSectionY = minSectionY - 1;
            this.sectionCount = sectionCount + 2;
            this.layers = new DataLayer[9 * this.sectionCount];
            this.fetched = new boolean[this.layers.length];
        }

        private DataLayer layer(int dx, int dz, int section) {
            int slot = ((dx + 1) * 3 + dz + 1) * this.sectionCount + section;
            if (!this.fetched[slot]) {
                this.fetched[slot] = true;
                DataLayer data = this.listener.getDataLayerData(SectionPos.of(this.chunkX + dx, this.minSectionY + section, this.chunkZ + dz));
                this.layers[slot] = data != null && !data.isEmpty() ? data : null;
            }
            return this.layers[slot];
        }

        int get(int x, int y, int z) {
            int dx = (x >> 4) - this.chunkX;
            int dz = (z >> 4) - this.chunkZ;
            int section = (y >> 4) - this.minSectionY;
            if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1 && section >= 0 && section < this.sectionCount) {
                DataLayer data = layer(dx, dz, section);
                return data == null ? 0 : data.get(x & 15, y & 15, z & 15);
            }
            return 0;
        }

        int brightestAround(int x, int y, int z) {
            int best = get(x + 1, y, z);
            best = Math.max(best, get(x - 1, y, z));
            best = Math.max(best, get(x, y + 1, z));
            best = Math.max(best, get(x, y - 1, z));
            best = Math.max(best, get(x, y, z + 1));
            return Math.max(best, get(x, y, z - 1));
        }

        boolean quiet(int sectionIndex) {
            int section = sectionIndex + 1;
            return layer(0, 0, section) == null && layer(0, 0, section - 1) == null && layer(0, 0, section + 1) == null
                    && layer(-1, 0, section) == null && layer(1, 0, section) == null
                    && layer(0, -1, section) == null && layer(0, 1, section) == null;
        }
    }

    private static boolean litOpening(ClientLevel level, LightCache light, BlockPos.MutableBlockPos p, int x, int y, int z, int brightest) {
        for (Direction d : Direction.values()) {
            p.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
            if (light.get(p.getX(), p.getY(), p.getZ()) == brightest && isOpen(level.getBlockState(p))) return true;
        }
        return false;
    }

    private static boolean besideDarkOpening(ClientLevel level, LightCache light, BlockPos.MutableBlockPos p, int x, int y, int z) {
        for (Direction d : Direction.values()) {
            p.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
            if (light.get(p.getX(), p.getY(), p.getZ()) == 0) {
                BlockState state = level.getBlockState(p);
                if (isOpen(state) && !state.is(Blocks.VOID_AIR)) return true;
            }
        }
        return false;
    }

    private static boolean touchesLining(ClientLevel level, BlockPos.MutableBlockPos p, int x, int y, int z) {
        for (Direction d : Direction.values()) {
            p.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
            if (isLining(level.getBlockState(p))) return true;
        }
        return false;
    }

    private static final class GlowScan {
        private static final int MIN_Y = -58;
        private static final int MAX_Y = 30;
        private static final int LOW_SECTION = -4;
        private static final int HIGH_SECTION = 1;
        private static final int FIRST_CACHED = -5;
        private static final int CACHED = 8;
        private final DataLayer[] layers = new DataLayer[72];
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int chunkX;
        private int chunkZ;

        int scan(ClientLevel level, LevelChunk chunk, List<Long> out) {
            this.chunkX = chunk.getPos().x;
            this.chunkZ = chunk.getPos().z;
            if (!cache(level)) return 0;
            LevelChunkSection[] sections = chunk.getSections();
            int minX = this.chunkX << 4;
            int minZ = this.chunkZ << 4;
            int count = 0;
            for (int sy = LOW_SECTION; sy <= HIGH_SECTION; sy++) {
                int index = sy - chunk.getMinSectionY();
                if (index < 0 || index >= sections.length) continue;
                LevelChunkSection sec = sections[index];
                if (sec == null || !nearLight(sy)) continue;
                int from = Math.max(MIN_Y, sy << 4);
                int to = Math.min(MAX_Y, (sy << 4) + 15);
                for (int y = from; y <= to; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (!open(sec.getBlockState(x, y & 15, z))) continue;
                            int wx = minX + x;
                            int wz = minZ + z;
                            if (lightAt(wx, y, wz) == 0 && edge(level, wx, y, wz)) {
                                count++;
                                out.add(BlockPos.asLong(wx, y, wz));
                            }
                        }
                    }
                }
            }
            return count;
        }

        private boolean cache(ClientLevel level) {
            LayerLightEventListener light = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
            boolean any = false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int s = 0; s < CACHED; s++) {
                        DataLayer layer = light.getDataLayerData(SectionPos.of(this.chunkX + dx, FIRST_CACHED + s, this.chunkZ + dz));
                        if (layer != null && layer.isEmpty()) layer = null;
                        this.layers[slot(dx, dz, s)] = layer;
                        any |= layer != null;
                    }
                }
            }
            return any;
        }

        private boolean nearLight(int sy) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int s = sy - 1; s <= sy + 1; s++) {
                        if (this.layers[slot(dx, dz, s - FIRST_CACHED)] != null) return true;
                    }
                }
            }
            return false;
        }

        private boolean edge(ClientLevel level, int x, int y, int z) {
            boolean litOpening = false;
            int brightest = 0;
            for (Direction d : Direction.values()) {
                int nx = x + d.getStepX();
                int ny = y + d.getStepY();
                int nz = z + d.getStepZ();
                int value = lightAt(nx, ny, nz);
                if (value > 4) return false;
                brightest = Math.max(brightest, value);
                if (value == 4 && !litOpening && open(level.getBlockState(this.cursor.set(nx, ny, nz)))) litOpening = true;
            }
            return brightest == 4 && litOpening;
        }

        private int lightAt(int x, int y, int z) {            int s = (y >> 4) - FIRST_CACHED;
            int dx = (x >> 4) - this.chunkX;
            int dz = (z >> 4) - this.chunkZ;
            if (s < 0 || s >= CACHED || dx < -1 || dx > 1 || dz < -1 || dz > 1) return 0;
            DataLayer layer = this.layers[slot(dx, dz, s)];
            return layer == null ? 0 : layer.get(x & 15, y & 15, z & 15);
        }

        private static int slot(int dx, int dz, int s) {
            return ((dx + 1) * 3 + dz + 1) * CACHED + s;
        }

        private static boolean open(BlockState state) {
            return state.isAir() || state.is(Blocks.AMETHYST_CLUSTER);
        }
    }

    private static boolean isLining(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST);
    }

    private static boolean isBud(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.SMALL_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.LARGE_AMETHYST_BUD;
    }

    private static boolean isOpen(BlockState state) {
        return state.isAir() || state.is(Blocks.AMETHYST_CLUSTER) || state.is(Blocks.WATER);
    }

    private static boolean isGrowthLight(int light) {
        return light == 1 || light == 2 || light == 4 || light == 5;
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
        for (long[] list : hits.values()) {
            for (long packed : list) {
                int px = BlockPos.getX(packed);
                int py = BlockPos.getY(packed);
                int pz = BlockPos.getZ(packed);
                double dx = px + 0.5 - ex;
                double dy = py + 0.5 - ey;
                double dz = pz + 0.5 - ez;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 <= r2) near.add(new double[] {d2, px, py, pz});
            }
        }
        near.sort((a, b) -> Double.compare(a[0], b[0]));
        int cap = Math.min(near.size(), maxBoxes.i());
        float[] tmp = new float[2];
        int filled = 0;
        for (int n = 0; n < cap; n++) {
            double[] hit = near.get(n);
            int x = (int) hit[1];
            int y = (int) hit[2];
            int z = (int) hit[3];
            double dist = Math.sqrt(hit[0]);
            float alpha = kit.alphaFor(dist, r);
            AABB box = EspKit.shape(mc, x, y, z);
            double cx = (box.minX + box.maxX) / 2;
            double cy = (box.minY + box.maxY) / 2;
            double cz = (box.minZ + box.maxZ) / 2;
            kit.tracer(g, w, h, cx, cy, cz, PINK, alpha);
            if (Projector.depth(cx, cy, cz) < -1.5) continue;
            if (!Projector.project(cx, cy, cz, tmp) || tmp[0] < -80 || tmp[0] > w + 80 || tmp[1] < -80 || tmp[1] > h + 80) continue;
            shown++;
            kit.box(g, box, PINK, alpha, filled++ < 36);
            if (names.on() && n < 8 && Projector.project(cx, box.maxY + 0.15, cz, tmp)) {
                kit.tag(g, "Amethyst", Math.round(dist) + "m", Math.round(tmp[0]), Math.round(tmp[1]) - 14, PINK, alpha, w, h);
            }
        }
    }
}
