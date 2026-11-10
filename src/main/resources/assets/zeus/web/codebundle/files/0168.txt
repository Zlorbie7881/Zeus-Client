package com.zlorbie788.zeus.render;

import com.zlorbie788.zeus.ui.Draw;
import com.zlorbie788.zeus.ui.Tex;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The flat chunk marking of Anubis (GPL-3, credited in NOTICE): a translucent tile on the Y=63 plane for every flagged chunk,
 * a thin edge only round the outside of a group of joined chunks with softly rounded corners, a tile that grows in from the
 * middle of its group and shrinks away again, and a tracer to the middle of each separate area.
 *
 * <p>This class only holds the shapes and draws them. Which chunks are flagged is decided elsewhere.
 */
public final class ChunkLook {
    private ChunkLook() {}

    /** Normal find: translucent red. */
    public static final int FILL = 0x60FF0404;
    /** A likely mined geode: translucent purple. */
    public static final int FILL_MINED = 0x60A64DFF;
    /** The plane the tiles lie on. */
    public static final double PLANE = 63.0;

    private static final float GROW_S = 0.30f;
    private static final float SHRINK_S = 0.25f;
    private static final double CORNER = 2.0;
    private static final int CORNER_STEPS = 4;
    private static final int MAX_CELLS_FILLED = 160;

    /** The edge colour of a fill: alpha x 1.5 (at most 255) and the colour a little darker. */
    public static int edgeColor(int fill) {
        int a = Math.min(255, Math.round(((fill >>> 24) & 255) * 1.5f));
        int r = Math.round(((fill >> 16) & 255) * 0.77f);
        int g = Math.round(((fill >> 8) & 255) * 0.77f);
        int b = Math.round((fill & 255) * 0.77f);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** One group of chunks with everything needed to draw it. */
    public static final class Area {
        public final long id;
        final int sig;
        final int fill;
        final List<long[]> cells;
        final double cx;
        final double cz;
        final List<double[][]> loops = new ArrayList<>();
        /** x, z of the middle of each separate (also diagonally joined) area. */
        public final List<double[]> centres = new ArrayList<>();
        long born;
        long died;
        public int stamp;

        Area(long id, List<long[]> cells, int fill, long now) {
            this.id = id;
            this.cells = cells;
            this.fill = fill;
            this.sig = signature(cells, fill);
            this.born = now;
            double sx = 0;
            double sz = 0;
            for (long[] c : cells) {
                sx += c[0] * 16 + 8;
                sz += c[1] * 16 + 8;
            }
            this.cx = cells.isEmpty() ? 0 : sx / cells.size();
            this.cz = cells.isEmpty() ? 0 : sz / cells.size();
            buildLoops();
            buildCentres();
        }

        public boolean dying() {
            return died != 0L;
        }

        public void die(long now) {
            if (died == 0L) died = now;
        }

        /** True once the shrinking is over and the area can be forgotten. */
        public boolean gone(long now) {
            return died != 0L && (now - died) / 1000f >= SHRINK_S;
        }

        /** 0 to 1: how far the tile has grown. */
        public float scale(long now) {
            if (died != 0L) {
                float t = Math.min(1f, Math.max(0f, (now - died) / 1000f / SHRINK_S));
                return 1f - t * t * t;
            }
            float t = Math.min(1f, Math.max(0f, (now - born) / 1000f / GROW_S));
            float u = 1f - t;
            return 1f - u * u * u;
        }

        private static long cellKey(long x, long z) {
            return (x << 32) ^ (z & 0xFFFFFFFFL);
        }

        private void buildCentres() {
            Set<Long> in = new HashSet<>();
            for (long[] c : cells) in.add(cellKey(c[0], c[1]));
            Set<Long> seen = new HashSet<>();
            for (long[] start : cells) {
                if (!seen.add(cellKey(start[0], start[1]))) continue;
                List<long[]> part = new ArrayList<>();
                ArrayDeque<long[]> queue = new ArrayDeque<>();
                queue.add(start);
                while (!queue.isEmpty()) {
                    long[] c = queue.poll();
                    part.add(c);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            long nx = c[0] + dx;
                            long nz = c[1] + dz;
                            long k = cellKey(nx, nz);
                            if (in.contains(k) && seen.add(k)) queue.add(new long[] {nx, nz});
                        }
                    }
                }
                double sx = 0;
                double sz = 0;
                for (long[] c : part) {
                    sx += c[0] * 16 + 8;
                    sz += c[1] * 16 + 8;
                }
                double mx = sx / part.size();
                double mz = sz / part.size();
                // the middle of an L or a ring can lie outside the area: move it into the nearest flagged chunk
                if (!in.contains(cellKey((long) Math.floor(mx / 16.0), (long) Math.floor(mz / 16.0)))) {
                    double best = Double.MAX_VALUE;
                    for (long[] c : part) {
                        double ccx = c[0] * 16 + 8;
                        double ccz = c[1] * 16 + 8;
                        double d = (ccx - mx) * (ccx - mx) + (ccz - mz) * (ccz - mz);
                        if (d < best) {
                            best = d;
                            mx = ccx;
                            mz = ccz;
                        }
                    }
                }
                centres.add(new double[] {mx, mz});
            }
        }

        /** The outside of the group, as closed lines; each corner is rounded off. */
        private void buildLoops() {
            Set<Long> in = new HashSet<>();
            for (long[] c : cells) in.add(cellKey(c[0], c[1]));
            // directed edges, clockwise seen from above (x east, z south), keyed by their start corner
            Map<Long, List<long[]>> out = new HashMap<>();
            for (long[] c : cells) {
                long x0 = c[0] * 16;
                long z0 = c[1] * 16;
                if (!in.contains(cellKey(c[0], c[1] - 1))) put(out, x0, z0, x0 + 16, z0);
                if (!in.contains(cellKey(c[0] + 1, c[1]))) put(out, x0 + 16, z0, x0 + 16, z0 + 16);
                if (!in.contains(cellKey(c[0], c[1] + 1))) put(out, x0 + 16, z0 + 16, x0, z0 + 16);
                if (!in.contains(cellKey(c[0] - 1, c[1]))) put(out, x0, z0 + 16, x0, z0);
            }
            while (!out.isEmpty()) {
                Long first = out.keySet().iterator().next();
                List<long[]> corners = new ArrayList<>();
                long[] e = take(out, first);
                long sx = e[0];
                long sz = e[1];
                corners.add(new long[] {e[0], e[1]});
                long px = e[2];
                long pz = e[3];
                int guard = 0;
                while ((px != sx || pz != sz) && guard++ < 100000) {
                    corners.add(new long[] {px, pz});
                    long[] n = take(out, pointKey(px, pz));
                    if (n == null) break;
                    px = n[2];
                    pz = n[3];
                }
                loops.add(round(simplify(corners)));
            }
        }

        private static long pointKey(long x, long z) {
            return (x << 32) ^ (z & 0xFFFFFFFFL);
        }

        private static void put(Map<Long, List<long[]>> m, long x1, long z1, long x2, long z2) {
            m.computeIfAbsent(pointKey(x1, z1), k -> new ArrayList<>()).add(new long[] {x1, z1, x2, z2});
        }

        private static long[] take(Map<Long, List<long[]>> m, long key) {
            List<long[]> l = m.get(key);
            if (l == null || l.isEmpty()) return null;
            long[] e = l.remove(l.size() - 1);
            if (l.isEmpty()) m.remove(key);
            return e;
        }

        /** Drops corners that lie on a straight line. */
        private static List<long[]> simplify(List<long[]> pts) {
            List<long[]> out = new ArrayList<>();
            int n = pts.size();
            for (int i = 0; i < n; i++) {
                long[] a = pts.get((i + n - 1) % n);
                long[] b = pts.get(i);
                long[] c = pts.get((i + 1) % n);
                long cross = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0]);
                if (cross != 0) out.add(b);
            }
            return out;
        }

        private static double[][] round(List<long[]> pts) {
            int n = pts.size();
            List<double[]> out = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                long[] a = pts.get((i + n - 1) % n);
                long[] b = pts.get(i);
                long[] c = pts.get((i + 1) % n);
                double l1 = Math.hypot(b[0] - a[0], b[1] - a[1]);
                double l2 = Math.hypot(c[0] - b[0], c[1] - b[1]);
                double r = Math.min(CORNER, Math.min(l1, l2) / 2.0);
                double sx = b[0] - (b[0] - a[0]) / l1 * r;
                double sz = b[1] - (b[1] - a[1]) / l1 * r;
                double ex = b[0] + (c[0] - b[0]) / l2 * r;
                double ez = b[1] + (c[1] - b[1]) / l2 * r;
                for (int s = 0; s <= CORNER_STEPS; s++) {
                    double t = s / (double) CORNER_STEPS;
                    double u = 1.0 - t;
                    out.add(new double[] {u * u * sx + 2 * u * t * b[0] + t * t * ex, u * u * sz + 2 * u * t * b[1] + t * t * ez});
                }
            }
            return out.toArray(new double[0][]);
        }
    }

    private static int signature(List<long[]> cells, int fill) {
        int h = fill * 31 + cells.size();
        for (long[] c : cells) h = h * 31 + (int) (c[0] * 7919 + c[1]);
        return h;
    }

    /** The Area for these chunks: the old one if nothing changed, a fresh one (growing in) if it is new or changed shape. */
    public static Area area(Map<Long, Area> store, List<long[]> cells, int fill, long now) {
        if (cells.isEmpty()) return null;
        long id = Long.MAX_VALUE;
        for (long[] c : cells) {
            long k = (c[0] << 32) ^ (c[1] & 0xFFFFFFFFL);
            if (k < id) id = k;
        }
        Area old = store.get(id);
        if (old != null && old.sig == signature(cells, fill) && !old.dying()) return old;
        Area a = new Area(id, new ArrayList<>(cells), fill, now);
        // a group that only changed a little keeps its place instead of growing in from nothing again
        if (old != null && !old.dying()) a.born = old.born;
        store.put(id, a);
        return a;
    }

    private static final double[] POLY = new double[24];
    private static final float[] SCR = new float[16];

    /** Draws the tiles and the edge of an area at the given grow state (0 to 1). */
    public static void draw(GuiGraphics g, Area a, float e, double y) {
        if (e <= 0.003f) return;
        int gs = Tex.guiScale();
        int fill = Draw.rgba(a.fill, e);
        int edge = Draw.rgba(edgeColor(a.fill), e);
        float sw = Projector.width();
        float sh = Projector.height();
        double cx = a.cx;
        double cz = a.cz;
        int filled = 0;
        double eyeD = Projector.depth(cx, y, cz);
        for (long[] c : a.cells) {
            if (filled >= MAX_CELLS_FILLED) break;
            double x0 = c[0] * 16;
            double z0 = c[1] * 16;
            // the four corners, shrunk towards the middle of the group while it grows or shrinks
            double[] px = {x0, x0 + 16, x0 + 16, x0};
            double[] pz = {z0, z0, z0 + 16, z0 + 16};
            int n = 0;
            for (int i = 0; i < 4; i++) {
                POLY[n * 3] = cx + (px[i] - cx) * e;
                POLY[n * 3 + 1] = y;
                POLY[n * 3 + 2] = cz + (pz[i] - cz) * e;
                n++;
            }
            n = clipNear(n);
            if (n < 3) continue;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            boolean ok = true;
            float[] tmp = new float[2];
            for (int i = 0; i < n; i++) {
                if (!Projector.project(POLY[i * 3], POLY[i * 3 + 1], POLY[i * 3 + 2], tmp)) {
                    ok = false;
                    break;
                }
                SCR[i * 2] = tmp[0];
                SCR[i * 2 + 1] = tmp[1];
                minX = Math.min(minX, tmp[0]);
                maxX = Math.max(maxX, tmp[0]);
                minY = Math.min(minY, tmp[1]);
                maxY = Math.max(maxY, tmp[1]);
            }
            if (!ok || maxX < 0 || maxY < 0 || minX > sw || minY > sh) continue;
            // a very long thin sliver (the plane seen edge-on) adds nothing
            if (maxY - minY < 0.3f && maxX - minX < 0.3f) continue;
            for (int i = 1; i < n - 1; i++) {
                Stroke.tri(g, SCR[0], SCR[1], SCR[i * 2], SCR[i * 2 + 1], SCR[(i + 1) * 2], SCR[(i + 1) * 2 + 1], fill);
            }
            filled++;
        }
        float[] seg = new float[4];
        float thick = 1.5f / gs;
        for (double[][] loop : a.loops) {
            int m = loop.length;
            for (int i = 0; i < m; i++) {
                double[] p = loop[i];
                double[] q = loop[(i + 1) % m];
                if (Projector.projectLine(cx + (p[0] - cx) * e, y, cz + (p[1] - cz) * e, cx + (q[0] - cx) * e, y, cz + (q[1] - cz) * e, seg)) {
                    Stroke.hard(g, seg[0], seg[1], seg[2], seg[3], thick, edge);
                }
            }
        }
    }

    /** Cuts the polygon in POLY (n points) against the near plane. Returns the new count. */
    private static int clipNear(int n) {
        double near = 0.1;
        double[] out = new double[24];
        int m = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double ax = POLY[i * 3], ay = POLY[i * 3 + 1], az = POLY[i * 3 + 2];
            double bx = POLY[j * 3], by = POLY[j * 3 + 1], bz = POLY[j * 3 + 2];
            double da = Projector.depth(ax, ay, az);
            double db = Projector.depth(bx, by, bz);
            boolean ina = da >= near;
            boolean inb = db >= near;
            if (ina) {
                out[m * 3] = ax;
                out[m * 3 + 1] = ay;
                out[m * 3 + 2] = az;
                m++;
            }
            if (ina != inb && m < 7) {
                double t = (near - da) / (db - da);
                out[m * 3] = ax + (bx - ax) * t;
                out[m * 3 + 1] = ay + (by - ay) * t;
                out[m * 3 + 2] = az + (bz - az) * t;
                m++;
            }
        }
        System.arraycopy(out, 0, POLY, 0, m * 3);
        return m;
    }
}
