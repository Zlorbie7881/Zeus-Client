package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.EspKit;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Clues {
    private Clues() {}

    private static boolean dark(Minecraft mc) {
        String d = mc.level == null ? "" : mc.level.dimension().toString();
        return d.contains("nether") || d.contains("the_end");
    }

    static boolean underground(Minecraft mc, int x, int y, int z) {
        if (mc.level == null) return false;
        if (dark(mc)) return true;
        return !mc.level.canSeeSky(new BlockPos(x, y, z));
    }

    static Boolean flag(BlockState st, String name) {
        try {
            for (Property<?> p : st.getProperties()) {
                if (p.getName().equals(name)) {
                    Object v = st.getValue(p);
                    if (v instanceof Boolean b) return b;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    public static final class LightDetector extends Finders.BlockFinder {
        private final Setting.Bool deep = add(new Setting.Bool("Only underground", "Skip lights that have open sky above them (a lamp on a house, a torch at a farm).", true));

        public LightDetector() {
            super("lightdetector", "Light Detector", "Finds torches, lanterns and lamps underground: places where someone has been.",
                    new String[] {"Torches", "Lanterns", "Lamps and glowing blocks", "Campfires", "Lit candles"},
                    new int[] {0xFFFFD45E, 0xFFFFB347, 0xFFFFF2A8, 0xFFFF7A18, 0xFFFFE2C2},
                    new boolean[] {true, true, true, true, false},
                    new String[] {"Torches of every kind, wall torches included.", "Lanterns and soul lanterns.", "Glowstone, sea lanterns, shroomlight, froglight, jack o'lanterns, lit redstone lamps, end rods, beacons.", "Lit campfires and soul campfires.", "Candles that are lit."});
        }

        @Override
        protected boolean cares(String p) {
            return p.endsWith("torch") || p.endsWith("wall_torch") || p.endsWith("lantern") && !p.equals("sea_lantern") || p.equals("sea_lantern")
                    || p.equals("glowstone") || p.equals("shroomlight") || p.endsWith("froglight") || p.equals("jack_o_lantern") || p.equals("redstone_lamp")
                    || p.equals("end_rod") || p.equals("beacon") || p.endsWith("campfire") || p.endsWith("candle") || p.endsWith("candle_cake");
        }

        @Override
        protected int kind(BlockState st, String p) {
            if (p.endsWith("torch")) return p.contains("redstone") && Boolean.FALSE.equals(flag(st, "lit")) ? -1 : 0;
            if (p.endsWith("lantern") && !p.equals("sea_lantern") && !p.equals("jack_o_lantern")) return 1;
            if (p.endsWith("campfire")) return Boolean.FALSE.equals(flag(st, "lit")) ? -1 : 3;
            if (p.endsWith("candle") || p.endsWith("candle_cake")) return Boolean.TRUE.equals(flag(st, "lit")) ? 4 : -1;
            if (p.equals("redstone_lamp")) return Boolean.TRUE.equals(flag(st, "lit")) ? 2 : -1;
            return 2;
        }

        @Override
        protected boolean keep(LevelChunk chunk, int x, int y, int z, int kind) {
            return !deep.on() || underground(Minecraft.getInstance(), x, y, z);
        }
    }

    public static final class BaseClues extends Finders.BlockFinder {
        private final Setting.Bool entities = add(new Setting.Bool("Item frames, paintings, armor stands", "Also mark these, they only exist where somebody put them.", true));
        private final Setting.Bool deep = add(new Setting.Bool("Only underground", "Skip finds with open sky above them.", false));

        public BaseClues() {
            super("baseclues", "Base Clues", "Marks the small things bases leave behind: workstations, glass, signs, rails, blocks of valuables.",
                    new String[] {"Workstations", "Glass", "Signs and banners", "Iron doors and trapdoors", "Concrete and glazed terracotta", "Blocks of valuables", "Powered rails and ladders", "Pressure plates and tripwires"},
                    new int[] {0xFFB77BFF, 0xFF9FE8FF, 0xFFE8D28A, 0xFFC4CAD6, 0xFFFF7AB6, 0xFF4DFFC4, 0xFFFFA24D, 0xFFFF6B6B},
                    new boolean[] {true, false, true, true, true, true, true, false},
                    new String[] {"Anvils, enchanting tables, brewing stands, smithing tables, grindstones, lecterns and more.", "Glass and glass panes.", "Signs, hanging signs and banners.", "Iron doors and iron trapdoors.", "Concrete and glazed terracotta.", "Diamond, emerald, gold, netherite and lapis blocks, beacons.", "Powered, detector and activator rails, ladders, scaffolding.", "Pressure plates, tripwire hooks."});
        }

        @Override
        protected boolean cares(String p) {
            return kindOfPath(p) >= 0;
        }

        private static int kindOfPath(String p) {
            switch (p) {
                case "anvil", "chipped_anvil", "damaged_anvil", "enchanting_table", "brewing_stand", "smithing_table", "grindstone", "stonecutter",
                     "cartography_table", "fletching_table", "loom", "lectern", "crafting_table", "blast_furnace", "smoker", "composter":
                    return 0;
                case "glass", "glass_pane", "tinted_glass":
                    return 1;
                case "iron_door", "iron_trapdoor":
                    return 3;
                case "diamond_block", "emerald_block", "gold_block", "netherite_block", "lapis_block", "beacon", "conduit":
                    return 5;
                case "powered_rail", "detector_rail", "activator_rail", "ladder", "scaffolding":
                    return 6;
                case "tripwire_hook", "stone_pressure_plate", "polished_blackstone_pressure_plate", "heavy_weighted_pressure_plate", "light_weighted_pressure_plate":
                    return 7;
                default:
                    break;
            }
            if (p.endsWith("_stained_glass") || p.endsWith("_stained_glass_pane")) return 1;
            if (p.endsWith("_sign") || p.endsWith("_hanging_sign") || p.endsWith("_banner")) return 2;
            if (p.endsWith("_concrete") || p.endsWith("glazed_terracotta")) return 4;
            return -1;
        }

        @Override
        protected int kind(BlockState st, String p) {
            return kindOfPath(p);
        }

        @Override
        protected boolean keep(LevelChunk chunk, int x, int y, int z, int kind) {
            return !deep.on() || underground(Minecraft.getInstance(), x, y, z);
        }

        @Override
        protected void drawExtra(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (!entities.on() || mc.player == null) return;
            double r = scanRange();
            int filled = 0;
            for (Entity e : Env.entities(mc, r)) {
                if (!(e instanceof net.minecraft.world.entity.decoration.ItemFrame) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
                        && !(e instanceof net.minecraft.world.entity.decoration.painting.Painting)) continue;
                double dist = Math.sqrt(mc.player.distanceToSqr(e));
                float a = kit.alphaFor(dist, r);
                AABB bb = e.getBoundingBox();
                int color = e instanceof net.minecraft.world.entity.decoration.ArmorStand ? 0xFFFFC857 : 0xFF6AA6FF;
                if (Projector.depth((bb.minX + bb.maxX) / 2, (bb.minY + bb.maxY) / 2, (bb.minZ + bb.maxZ) / 2) < -1.5) continue;
                kit.tracer(g, w, h, (bb.minX + bb.maxX) / 2, (bb.minY + bb.maxY) / 2, (bb.minZ + bb.maxZ) / 2, color, a);
                kit.box(g, bb, color, a, filled++ < 12);
            }
        }
    }

    public static final class CaveEsp extends Module {
        private final Setting.Num range = add(new Setting.Num("Range", "How far around you the caves are marked.", 40, 16, 96, 8, " blocks"));
        private final Setting.Num open = add(new Setting.Num("How open", "How much of a cell has to be empty for it to count as cave.", 75, 50, 100, 5, " %"));
        private final Setting.Num smallest = add(new Setting.Num("Smallest cave", "Pockets with fewer cells than this are left out (one cell is 4 x 4 x 4 blocks).", 10, 3, 80, 1, " cells"));
        private final Setting.Num roof = add(new Setting.Num("Rock above", "A cave must have at least this much ground above it. Keeps hills, ravines and your own surface build out.", 6, 1, 40, 1, " blocks"));
        private final Setting.Choice look = add(new Setting.Choice("Look", "Boxes: the edge cells of every cave. Dots: a glow in each of them. Both together.", 0, "Boxes", "Dots", "Both"));
        private final Setting.Num boxes = add(new Setting.Num("Most boxes", "The nearest are kept when there are more.", 70, 10, 250, 10, "")).showIf(() -> this.look.index() != 1);
        private final Setting.Num dots = add(new Setting.Num("Most dots", "The nearest are kept when there are more.", 400, 50, 1500, 50, "")).showIf(() -> this.look.index() != 0);
        private final Setting.Num dotSize = add(new Setting.Num("Dot size", "How big the dots are.", 100, 50, 250, 10, " %")).showIf(() -> this.look.index() != 0);
        private final Setting.Bool labels = add(new Setting.Bool("Cave names", "A tag on the nearest big caves with their size.", true));
        private final Setting.Choice tint = add(new Setting.Choice("Colours", "By depth: teal near the top, violet deep. Or one colour.", 0, "By depth", "Teal", "Violet", "Orange", "Green"));
        private final EspKit kit;

        private final Map<Long, int[][]> cells = new HashMap<>();
        private final Map<Long, Long> scannedAt = new HashMap<>();
        private List<long[]> queue = new ArrayList<>();
        private int tick;
        private int shown;

        public CaveEsp() {
            super("caveesp", "CaveFinder", "Isolates and highlights hollow cave networks in loaded underground chunks.", Category.SCANNER);
            light();
            kit = new EspKit(this, 0, false);
            range.onChange(this::reset);
            open.onChange(this::reset);
            smallest.onChange(this::reset);
            roof.onChange(this::reset);
        }

        private void reset() {
            cells.clear();
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

        private static long key(int cx, int cz) {
            return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.level == null || mc.player == null) return;
            tick++;
            int pcx = mc.player.getBlockX() >> 4;
            int pcz = mc.player.getBlockZ() >> 4;
            int rc = (int) Math.ceil(range.get() / 16.0);
            long now = System.currentTimeMillis();
            if (tick % 20 == 1) {
                queue = new ArrayList<>();
                for (int dx = -rc; dx <= rc; dx++) {
                    for (int dz = -rc; dz <= rc; dz++) {
                        long k = key(pcx + dx, pcz + dz);
                        Long at = scannedAt.get(k);
                        if (at == null || now - at > 20000) queue.add(new long[] {dx * dx + dz * dz, pcx + dx, pcz + dz});
                    }
                }
                queue.sort((a, b) -> Long.compare(a[0], b[0]));
                cells.keySet().removeIf(k -> Math.abs((int) (k >> 32) - pcx) > rc + 2 || Math.abs((int) (long) k - pcz) > rc + 2);
                scannedAt.keySet().removeIf(k -> !cells.containsKey(k) && now - scannedAt.get(k) > 60000);
            }
            long deadline = System.nanoTime() + Brain.budget(1_800_000L);
            while (!queue.isEmpty() && System.nanoTime() < deadline) {
                long[] c = queue.remove(0);
                scan(mc, (int) c[1], (int) c[2], now);
            }
        }

        private void scan(Minecraft mc, int cx, int cz, long now) {
            LevelChunk chunk = Env.chunk(mc, cx, cz);
            if (chunk == null) return;
            scannedAt.put(key(cx, cz), now);
            LevelChunkSection[] sections = chunk.getSections();
            int minSecY = chunk.getMinSectionY() << 4;
            int layers = sections.length * 4;
            int need = (int) Math.ceil(64 * open.get() / 100.0);
            boolean overworld = Brain.ctx().hasSurface;
            boolean[][][] air = new boolean[layers][4][4];
            boolean[][][] hard = new boolean[layers][4][4];
            int[][][] amount = new int[layers][4][4];
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection sec = sections[i];
                if (sec == null) continue;
                boolean allAir = sec.hasOnlyAir();
                for (int cy = 0; cy < 4; cy++) {
                    int layer = i * 4 + cy;
                    for (int cz4 = 0; cz4 < 4; cz4++) {
                        for (int cx4 = 0; cx4 < 4; cx4++) {
                            if (allAir) {
                                air[layer][cz4][cx4] = true;
                                amount[layer][cz4][cx4] = 64;
                                continue;
                            }
                            int a = 0;
                            for (int y = 0; y < 4 && a >= 0; y++) {
                                for (int z = 0; z < 4; z++) {
                                    for (int x = 0; x < 4; x++) {
                                        if (sec.getBlockState(cx4 * 4 + x, cy * 4 + y, cz4 * 4 + z).isAir()) a++;
                                    }
                                }
                            }
                            amount[layer][cz4][cx4] = a;
                            air[layer][cz4][cx4] = a >= need;
                            hard[layer][cz4][cx4] = a <= 24;
                        }
                    }
                }
            }
            boolean[][][] cave = new boolean[layers][4][4];
            for (int cz4 = 0; cz4 < 4; cz4++) {
                for (int cx4 = 0; cx4 < 4; cx4++) {
                    int wx = (cx << 4) + cx4 * 4 + 2;
                    int wz = (cz << 4) + cz4 * 4 + 2;
                    int surf = overworld ? Brain.surface(mc, wx, wz) : Integer.MIN_VALUE;
                    boolean solidAbove = false;
                    for (int layer = layers - 1; layer >= 0; layer--) {
                        int wy = minSecY + layer * 4 + 2;
                        if (hard[layer][cz4][cx4]) solidAbove = true;
                        if (!air[layer][cz4][cx4]) continue;
                        boolean roofed = surf != Integer.MIN_VALUE ? wy + 2 < surf - roof.i() : solidAbove;
                        cave[layer][cz4][cx4] = roofed;
                    }
                }
            }
            int[][][] comp = new int[layers][4][4];
            List<int[]> sizes = new ArrayList<>();
            int next = 0;
            int minCells = overworld ? smallest.i() : smallest.i() * 4;
            for (int l = 0; l < layers; l++) {
                for (int z = 0; z < 4; z++) {
                    for (int x = 0; x < 4; x++) {
                        if (!cave[l][z][x] || comp[l][z][x] != 0) continue;
                        next++;
                        int count = 0;
                        java.util.ArrayDeque<int[]> todo = new java.util.ArrayDeque<>();
                        todo.add(new int[] {l, z, x});
                        comp[l][z][x] = next;
                        while (!todo.isEmpty()) {
                            int[] c = todo.poll();
                            count++;
                            int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                            for (int[] d : nb) {
                                int nl = c[0] + d[0], nz = c[1] + d[1], nx = c[2] + d[2];
                                if (nl < 0 || nl >= layers || nz < 0 || nz >= 4 || nx < 0 || nx >= 4) continue;
                                if (!cave[nl][nz][nx] || comp[nl][nz][nx] != 0) continue;
                                comp[nl][nz][nx] = next;
                                todo.add(new int[] {nl, nz, nx});
                            }
                        }
                        sizes.add(new int[] {next, count});
                    }
                }
            }
            int[] sizeOf = new int[next + 1];
            for (int[] s : sizes) sizeOf[s[0]] = s[1];
            List<int[]> out = new ArrayList<>();
            for (int l = 0; l < layers && out.size() < 500; l++) {
                for (int z = 0; z < 4; z++) {
                    for (int x = 0; x < 4; x++) {
                        int id = comp[l][z][x];
                        if (id == 0 || sizeOf[id] < minCells) continue;
                        boolean edge = l == 0 || l == layers - 1 || z == 0 || z == 3 || x == 0 || x == 3
                                || !cave[l + 1][z][x] || !cave[l - 1][z][x] || !cave[l][z + 1][x] || !cave[l][z - 1][x] || !cave[l][z][x + 1] || !cave[l][z][x - 1];
                        if (!edge) continue;
                        out.add(new int[] {(cx << 4) + x * 4 + 2, minSecY + l * 4 + 2, (cz << 4) + z * 4 + 2, amount[l][z][x] * 100 / 64, sizeOf[id]});
                    }
                }
            }
            cells.put(key(cx, cz), out.toArray(new int[0][]));
        }

        @Override
        public String info() {
            return shown > 0 ? Integer.toString(shown) : null;
        }

        private int colorFor(double y) {
            return switch (tint.index()) {
                case 1 -> 0xFF35E0D0;
                case 2 -> 0xFF9B6BFF;
                case 3 -> 0xFFFF9F43;
                case 4 -> 0xFF4DFF88;
                default -> Draw.mix(0xFF35E0D0, 0xFF9B6BFF, (float) Math.max(0, Math.min(1, (64 - y) / 128.0)));
            };
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            shown = 0;
            if (mc.player == null || !Projector.prepare(mc, partial, w, h)) return;
            double ex = Projector.eyeX();
            double ey = Projector.eyeY();
            double ez = Projector.eyeZ();
            double r = range.get();
            double r2 = r * r;
            List<double[]> near = new ArrayList<>();
            for (int[][] list : cells.values()) {
                for (int[] c : list) {
                    double dx = c[0] - ex;
                    double dy = c[1] - ey;
                    double dz = c[2] - ez;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= r2) near.add(new double[] {d2, c[0], c[1], c[2], c[3], c[4]});
                }
            }
            near.sort((a, b) -> Double.compare(a[0], b[0]));
            float[] t = new float[2];
            float pulse = kit.pulse();
            boolean showDots = look.index() != 0;
            boolean showBoxes = look.index() != 1;
            int boxCap = boxes.i();
            int dotCap = dots.i();
            int nb = 0;
            int nd = 0;
            List<double[]> tagged = new ArrayList<>();
            for (double[] c : near) {
                if (nb >= boxCap && nd >= dotCap) break;
                double dist = Math.sqrt(c[0]);
                if (Projector.depth(c[1], c[2], c[3]) < -2) continue;
                if (!Projector.project(c[1], c[2], c[3], t) || t[0] < -40 || t[0] > w + 40 || t[1] < -40 || t[1] > h + 40) continue;
                shown++;
                int color = EspKit.pick(kit.color, colorFor(c[2]));
                float a = kit.alphaFor(dist, r) * kit.opacity.f() / 100f;
                if (showBoxes && nb < boxCap) {
                    nb++;
                    kit.box(g, c[1] - 2, c[2] - 2, c[3] - 2, c[1] + 2, c[2] + 2, c[3] + 2, color, a * 0.9f, nb < 18);
                }
                if (showDots && nd < dotCap) {
                    nd++;
                    int sz = Math.max(2, Math.min(9, (int) Math.round(48 / Math.max(6, dist) * dotSize.f() / 100.0)));
                    int x = Math.round(t[0]);
                    int y = Math.round(t[1]);
                    if (kit.glow.on()) g.fill(x - sz - 1, y - sz - 1, x + sz + 2, y + sz + 2, Draw.rgba(color, 0.12f * a * pulse));
                    g.fill(x - sz / 2, y - sz / 2, x + sz / 2 + 1, y + sz / 2 + 1, Draw.rgba(color, 0.85f * a));
                }
                if (labels.on() && c[5] >= smallest.i() * 4 && tagged.size() < 3) {
                    boolean close = false;
                    for (double[] o : tagged) if (Math.abs(o[1] - c[1]) + Math.abs(o[3] - c[3]) < 24) close = true;
                    if (!close) tagged.add(c);
                }
            }
            for (double[] c : tagged) {
                if (!Projector.project(c[1], c[2] + 2.5, c[3], t)) continue;
                kit.tag(g, "Cave", Math.round(Math.sqrt(c[0])) + "m  " + Math.round(c[5] * 64 / 1000.0 * 10) / 10.0 + "k blocks", Math.round(t[0]), Math.round(t[1]) - 14, colorFor(c[2]), 1f, w, h);
            }
        }
    }
}
