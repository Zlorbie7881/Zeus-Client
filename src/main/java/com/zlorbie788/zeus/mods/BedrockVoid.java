package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.EspKit;
import com.zlorbie788.zeus.render.Projector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Openings in the bedrock floor. A hole here is a column where the bottom layer is not bedrock,
 * which is either a way to look underneath the world or a way to fall out of it. The same check
 * runs on the Nether roof, where a missing block is a way up.
 */
public final class BedrockVoid extends Module {
    private static final int VIOLET = 0xFFA05BFF;

    private final Setting.Num radius = add(new Setting.Num("Range", "How far around you it looks. Only chunks the game has been sent can be searched.", 96, 32, 256, 16, " blocks"));
    private final Setting.Bool onlyVoid = add(new Setting.Bool("Only real openings", "On: only air or liquid counts, so you can look or fall through. Off: anything that is not bedrock counts, including blocks someone placed in the floor.", true));
    private final Setting.Bool netherRoof = add(new Setting.Bool("Nether roof", "Also looks at the top layer of the Nether, where a missing block is a way up.", true));
    private final Setting.Bool beam = add(new Setting.Bool("Beam", "A beam above each hole so you see it from far away.", true));
    private final Setting.Bool names = add(new Setting.Bool("Labels", "Name and distance on the nearest holes.", true));
    private final Setting.Bool alert = add(new Setting.Bool("Alert", "A message when a new hole shows up.", true));
    private final Setting.Num maxBoxes = add(new Setting.Num("Most boxes shown", "The nearest ones are kept when there are more.", 200, 20, 400, 10, ""));
    private final EspKit kit;

    private final Map<Long, long[]> hits = new HashMap<>();
    private final Map<Long, Long> scannedAt = new HashMap<>();
    private final Set<Long> seen = new HashSet<>();
    private List<long[]> queue = new ArrayList<>();
    private int tick;
    private int shown;
    private long lastToast;

    public BedrockVoid() {
        super("bedrockvoidesp", "Bedrock Void ESP", "Shows openings in the bedrock floor you can look or fall through, and holes in the Nether roof.", Category.SCANNER);
        light();
        alias("void holes", "bedrock holes", "void esp", "bedrock gaps");
        kit = new EspKit(this, 0, false);
        radius.onChange(this::reset);
        onlyVoid.onChange(this::reset);
        netherRoof.onChange(this::reset);
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
        seen.clear();
        tick = 0;
    }

    @Override
    protected void onDisable() {
        reset();
        seen.clear();
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
                    if (at == null || now - at > 4000) queue.add(new long[] {dx * dx + dz * dz, pcx + dx, pcz + dz});
                }
            }
            queue.sort((a, b) -> Long.compare(a[0], b[0]));
            hits.keySet().removeIf(k -> Math.abs((int) (k >> 32) - pcx) > rc + 1 || Math.abs((int) (long) k - pcz) > rc + 1);
            scannedAt.keySet().removeIf(k -> !hits.containsKey(k) && now - scannedAt.get(k) > 60000);
            if (seen.size() > 40000) seen.clear();
        }
        long deadline = System.nanoTime() + Brain.budget(1_400_000L);
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
        int floor = mc.level.getMinY();
        layer(chunk, floor, out);
        if (netherRoof.on() && mc.level.dimension() == Level.NETHER) layer(chunk, mc.level.getMaxY() - 1, out);
        long[] packed = new long[out.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = out.get(i);
        hits.put(key(cx, cz), packed);
        if (alert.on() && mc.player != null) {
            for (long pos : packed) {
                if (!seen.add(pos)) continue;
                if (now - lastToast < 3000) break;
                lastToast = now;
                double d = Math.sqrt(mc.player.distanceToSqr(BlockPos.getX(pos) + 0.5, BlockPos.getY(pos), BlockPos.getZ(pos) + 0.5));
                Zeus.toast("Bedrock opening " + Math.round(d) + " blocks away", true);
                break;
            }
        }
    }

    private void layer(LevelChunk chunk, int y, List<Long> out) {
        LevelChunkSection[] sections = chunk.getSections();
        int index = (y >> 4) - chunk.getMinSectionY();
        if (index < 0 || index >= sections.length) return;
        LevelChunkSection sec = sections[index];
        int minX = chunk.getPos().x << 4;
        int minZ = chunk.getPos().z << 4;
        if (sec == null) {
            if (onlyVoid.on()) return;
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) out.add(BlockPos.asLong(minX + x, y, minZ + z));
            return;
        }
        int ly = y & 15;
        if (sec.hasOnlyAir()) {
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) out.add(BlockPos.asLong(minX + x, y, minZ + z));
            return;
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                BlockState state = sec.getBlockState(x, ly, z);
                if (state.is(Blocks.BEDROCK)) continue;
                if (onlyVoid.on() && !state.isAir() && state.getFluidState().isEmpty()) continue;
                out.add(BlockPos.asLong(minX + x, y, minZ + z));
            }
        }
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
            kit.tracer(g, w, h, cx, cy, cz, VIOLET, alpha);
            if (beam.on()) {
                com.zlorbie788.zeus.render.Wire.line(g, cx, cy + 1, cz, cx, cy + 34, cz, 1.6f,
                        com.zlorbie788.zeus.ui.Draw.rgba(VIOLET, alpha * 0.7f), true);
            }
            if (Projector.depth(cx, cy, cz) < -1.5) continue;
            if (!Projector.project(cx, cy, cz, tmp) || tmp[0] < -80 || tmp[0] > w + 80 || tmp[1] < -80 || tmp[1] > h + 80) continue;
            shown++;
            kit.box(g, box, VIOLET, alpha, filled++ < 40);
            if (names.on() && n < 8 && Projector.project(cx, box.maxY + 0.15, cz, tmp)) {
                kit.tag(g, "Void hole", Math.round(dist) + "m", Math.round(tmp[0]), Math.round(tmp[1]) - 14, VIOLET, alpha, w, h);
            }
        }
    }
}
