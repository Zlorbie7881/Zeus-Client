package com.zlorbie788.zeus.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The HD tactical knives: high resolution sheets drawn as a lit, layered 3D solid.
 *
 * <p>Each knife is split along its length into parts (pommel or ring, handle, guard, blade).
 * A part is a slab: a stack of textured layers between -t and +t.  The outermost layer is the
 * lit face of the knife; just below it two shoulder layers, inset by the part's rounding
 * radius, turn the edge of a handle round instead of square; the core layers carry the colour
 * of the side wall, so a knife seen edge on is solid.  All layers are cut out by the alpha of
 * the sheet, so the outline - saw teeth, finger grooves, lanyard holes, rings - is exactly the
 * artwork at full resolution.
 *
 * <p>Lighting is per vertex on a grid, from the same key light as the hand, plus two light
 * passes drawn additively: a gloss pass for the flats and a glint pass for the bevels, whose
 * strength follows a Blinn-Phong highlight of a light near the camera.  As the knife turns, the
 * highlight runs along the blade and the edge flashes.
 *
 * <p>This class only builds the quads ({@link #build}); {@link KnifeHDGui} submits them to the
 * game, and a plain rasteriser can draw them for previews, so the geometry is the same in both.
 */
public final class KnifeHD {
    private KnifeHD() {
    }

    public static final String JSON = "/assets/zeus/knives/hd/tactical.json";
    public static final int CELLS = 7;
    public static final int FACE = 0, RIM50 = 1, RIM13 = 2, SIDE = 3, GLOSS = 4, EDGE = 5, PROFILE = 6;

    /** Model scale, the same as the other straight knives, so the hand and tricks fit. */
    public static final float SC = 1.2f;
    /** Knife units from the sheet's pommel end to the model origin. */
    public static final float U0 = 0.3f;

    private static final float D = 90f;
    // key light, the same direction the hand and the old knives use (towards the light)
    private static final float LX = -0.40f, LY = -0.55f, LZ = -0.73f;
    // a soft fill from the lower right, so the shadow side is never dead black
    private static final float FX = 0.55f, FY = 0.35f, FZ = -0.76f;
    // the light the highlights come from: up and left of the camera, close to the knife
    private static final float SPX = -38f, SPY = -62f, SPZ = -70f;

    public static final class Part {
        public String name;
        public float u0, u1, t, r;
    }

    public static final class Model {
        /** The sharpest detail level shipped for this knife (the newer knives start at 1). */
        public int minLevel;
        public String id;
        public String name;
        public int glow;
        public float len, half, zmax = 1.4f;
        public float tipU, tipV;
        /** Centre of material across the knife, sampled evenly along u from 0 to len. */
        public float[] centre = new float[0];
        public final List<Part> parts = new ArrayList<>();
    }

    /** One textured quad in screen space. uv are 0..1 over the whole sheet. */
    public static final class Quad {
        public final float[] x = new float[4];
        public final float[] y = new float[4];
        public final float[] u = new float[4];
        public final float[] v = new float[4];
        public final int[] c = new int[4];
        /** true: drawn as light (premultiplied, alpha 2/255 texels). */
        public boolean light;
        public int group;
    }

    /** The output of one build: quads in paint order, grouped so groups can change pipeline. */
    public static final class Frame {
        public final List<Quad> quads = new ArrayList<>();
        public int level;
        public Model model;
        private int used;

        Quad next() {
            Quad q;
            if (used < pool.size()) {
                q = pool.get(used);
            } else {
                q = new Quad();
                pool.add(q);
            }
            used++;
            quads.add(q);
            return q;
        }

        void reset() {
            quads.clear();
            used = 0;
        }

        private final List<Quad> pool = new ArrayList<>();
    }

    private static List<Model> models;
    private static boolean tried;

    public static synchronized List<Model> models() {
        if (tried) return models;
        tried = true;
        models = new ArrayList<>();
        try (InputStream in = KnifeHD.class.getResourceAsStream(JSON)) {
            if (in == null) return models;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                float len = root.get("len").getAsFloat();
                float half = root.get("half").getAsFloat();
                float zmax = root.has("zmax") ? root.get("zmax").getAsFloat() : 1.4f;
                for (JsonElement ke : root.getAsJsonArray("knives")) {
                    JsonObject k = ke.getAsJsonObject();
                    Model m = new Model();
                    m.id = k.get("id").getAsString();
                    m.minLevel = k.has("min_level") ? k.get("min_level").getAsInt() : 0;
                    m.name = k.get("name").getAsString();
                    String g = k.get("glow").getAsString().replace("#", "");
                    m.glow = 0xFF000000 | Integer.parseInt(g, 16);
                    m.len = len;
                    m.half = k.has("half") ? k.get("half").getAsFloat() : half;  // the karambit uses a taller sheet
                    m.zmax = zmax;
                    JsonArray tip = k.getAsJsonArray("tip");
                    m.tipU = tip.get(0).getAsFloat();
                    m.tipV = tip.get(1).getAsFloat();
                    if (k.has("centre")) {
                        JsonArray c = k.getAsJsonArray("centre");
                        m.centre = new float[c.size()];
                        for (int i = 0; i < m.centre.length; i++) m.centre[i] = c.get(i).getAsFloat();
                    }
                    for (JsonElement pe : k.getAsJsonArray("parts")) {
                        JsonObject po = pe.getAsJsonObject();
                        Part p = new Part();
                        p.name = po.get("name").getAsString();
                        p.u0 = po.get("u0").getAsFloat();
                        p.u1 = po.get("u1").getAsFloat();
                        p.t = po.get("t").getAsFloat();
                        p.r = po.get("r").getAsFloat();
                        m.parts.add(p);
                    }
                    models.add(m);
                }
            }
        } catch (Exception | Error e) {
            models = new ArrayList<>();
        }
        return models;
    }

    public static Model model(int i) {
        List<Model> ms = models();
        return i >= 0 && i < ms.size() ? ms.get(i) : null;
    }

    public static int count() {
        return models().size();
    }

    /** Sheet path for a knife at a detail level (0 full, 1 half, 2 quarter). */
    public static String sheet(Model m, int level) {
        return "textures/knives/hd/" + m.id + "_" + level + ".png";
    }

    // ------------------------------------------------------------------ model space
    /** Model point of a sheet position (u along, v across, z thickness), before the rig. */
    private static float mx(float v) {
        return v * SC;
    }

    private static float my(float u) {
        return (u - U0) * SC;
    }

    /** Where the tip of a knife is, in rig space. */
    public static float[] tip(Model m, Rig.T k) {
        return k.point(mx(m.tipV), my(m.tipU), 0f);
    }

    /** A point on the blade's centre line: t = 0 at the guard, 1 at the tip (rig space). */
    public static float[] along(Model m, Rig.T k, float t) {
        if (m == null) return k.point(0f, 0f, 0f);
        float g = m.tipU - 14f;
        for (Part p : m.parts) {
            if ("blade".equals(p.name)) g = p.u0;
        }
        float u = g + (m.tipU - g) * t;
        return k.point(mx(m.tipV * t), my(u), 0f);
    }

    /** How far up the knife the hand holds it, in rig units. */
    public static float grip(Model m) {
        for (Part p : m.parts) {
            if ("handle".equals(p.name)) return my(p.u0 + (p.u1 - p.u0) * 0.42f);
        }
        return my(6f);
    }

    // ------------------------------------------------------------------ build
    private static final float[] W = new float[3];

    private static void world(Rig.T k, float u, float v, float z, float[] out) {
        float lx = mx(v), ly = my(u), lz = z * SC;
        float[] r = k.r;
        out[0] = r[0] * lx + r[1] * ly + r[2] * lz + k.x;
        out[1] = r[3] * lx + r[4] * ly + r[5] * lz + k.y;
        out[2] = r[6] * lx + r[7] * ly + r[8] * lz + k.z;
    }

    /**
     * Build the quads of a knife.
     *
     * @param unit   screen units per rig unit (negative mirrors, as for the left hand)
     * @param pixels real pixels per screen unit (the GUI scale), used to pick the detail level
     *               and the number of core slices
     * @param alpha  overall opacity
     * @param light  false skips the light passes (motion blur ghosts)
     */
    public static Frame build(Frame f, Model m, Rig.T k, float unit, float pixels, float alpha, boolean light) {
        f.reset();
        f.model = m;
        if (m == null || alpha <= 0.003f) return f;

        // face normal (+z of the model) and the blade's edge direction (+x) in rig space
        float[] r = k.r;
        float nx = r[2], ny = r[5], nz = r[8];
        float ex = r[0], ey = r[3], ez = r[6];
        float ax = r[1], ay = r[4], az = r[7];

        // detail level from the on screen length
        world(k, 0f, 0f, 0f, W);
        float sx0 = W[0] * proj(W[2], unit), sy0 = W[1] * proj(W[2], unit);
        world(k, m.len, 0f, 0f, W);
        float sx1 = W[0] * proj(W[2], unit), sy1 = W[1] * proj(W[2], unit);
        float lenPx = (float) Math.hypot(sx1 - sx0, sy1 - sy0) * Math.max(1f, pixels);
        // the sheet has no mipmaps, so pick the level whose texels best match the screen pixels
        f.level = Math.max(m.minLevel, lenPx > 600f ? 0 : lenPx > 300f ? 1 : 2);

        // paint parts far to near
        int np = m.parts.size();
        Integer[] order = new Integer[np];
        float[] depth = new float[np];
        for (int i = 0; i < np; i++) {
            Part p = m.parts.get(i);
            world(k, (p.u0 + p.u1) * 0.5f, 0f, 0f, W);
            depth[i] = W[2];
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(depth[b], depth[a]));

        int group = 0;
        for (int oi = 0; oi < np; oi++) {
            Part p = m.parts.get(order[oi]);
            world(k, (p.u0 + p.u1) * 0.5f, 0f, 0f, W);
            float cx = W[0], cy = W[1], cz = W[2];
            // which side of the slab faces the camera (camera at 0, 0, -D)
            float toCam = nx * (0f - cx) + ny * (0f - cy) + nz * (-D - cz);
            float side = toCam >= 0f ? 1f : -1f;
            // the visible face's normal, towards the camera
            float fx = nx * side, fy = ny * side, fz = nz * side;

            // how thick the slab is on screen decides how many core slices make it solid
            world(k, (p.u0 + p.u1) * 0.5f, 0f, p.t, W);
            float tx = W[0] * proj(W[2], unit), ty = W[1] * proj(W[2], unit);
            world(k, (p.u0 + p.u1) * 0.5f, 0f, -p.t, W);
            float bx = W[0] * proj(W[2], unit), by = W[1] * proj(W[2], unit);
            float thickPx = (float) Math.hypot(tx - bx, ty - by) * Math.max(1f, pixels);

            float core = Math.max(0f, p.t - p.r);
            int slices = Math.max(1, Math.min(14, (int) Math.ceil(thickPx * (core / Math.max(1e-3f, p.t)) / 1.6f)));
            if (!light) slices = Math.min(slices, 3);

            // lighting of the visible face
            float key = Math.max(0f, fx * LX + fy * LY + fz * LZ);
            float fill = Math.max(0f, fx * FX + fy * FY + fz * FZ);
            float faceLum = clamp(0.40f + 0.58f * key + 0.16f * fill);
            // the side wall faces sideways: light it by how edge-on the slab is
            float edgeOn = 1f - Math.abs(fz);
            float sideLum = clamp(0.46f + 0.30f * edgeOn + 0.18f * key);

            // core slices from the far side to the near side
            for (int s = 0; s < slices; s++) {
                float t = slices == 1 ? 0f : -core + 2f * core * s / (slices - 1);
                float z = t * side;
                layer(f, m, k, p, SIDE, z, unit, sideLum, sideLum, alpha, group, false, fx, fy, fz, ex, ey, ez);
            }
            // the side wall: a strip through the thickness, so an edge-on knife is still solid
            float sx = ex * (toCamX(cx) ) + ey * (toCamY(cy)) + ez * (-D - cz) >= 0f ? 1f : -1f;
            float sKey = Math.max(0f, sx * (ex * LX + ey * LY + ez * LZ));
            float sFill = Math.max(0f, sx * (ex * FX + ey * FY + ez * FZ));
            strip(f, m, k, p, unit, clamp(0.50f + 0.45f * sKey + 0.12f * sFill), alpha, group);
            if (p.r > 0.02f) {
                layer(f, m, k, p, RIM13, (p.t - 0.5f * p.r) * side, unit, faceLum * 0.92f, faceLum * 0.92f, alpha, group, false, fx, fy, fz, ex, ey, ez);
                layer(f, m, k, p, RIM50, (p.t - 0.134f * p.r) * side, unit, faceLum * 0.97f, faceLum * 0.97f, alpha, group, false, fx, fy, fz, ex, ey, ez);
            }
            layer(f, m, k, p, FACE, p.t * side, unit, faceLum, -1f, alpha, group, false, fx, fy, fz, ex, ey, ez);
            if (light) {
                group++;
                layer(f, m, k, p, GLOSS, p.t * side, unit, 0f, -2f, alpha, group, true, fx, fy, fz, ex, ey, ez);
                layer(f, m, k, p, EDGE, p.t * side, unit, 0f, -3f, alpha, group, true, fx, fy, fz, ex, ey, ez);
                group++;
            }
        }
        return f;
    }

    private static float toCamX(float x) {
        return -x;
    }

    private static float toCamY(float y) {
        return -y;
    }

    /** Centre of material across the knife at u. */
    private static float centre(Model m, float u) {
        int n = m.centre.length;
        if (n < 2) return 0f;
        float x = Math.max(0f, Math.min(n - 1.001f, u / m.len * (n - 1)));
        int i = (int) x;
        float t = x - i;
        return m.centre[i] * (1 - t) + m.centre[i + 1] * t;
    }

    /** The profile strip of a part: the plane through the centre line and the thickness axis. */
    private static void strip(Frame f, Model m, Rig.T k, Part p, float unit, float lum, float alpha, int group) {
        float len = p.u1 - p.u0;
        int nu = Math.max(1, (int) Math.ceil(len / 0.5f));
        float cellV = 1f / CELLS;
        float tv0 = PROFILE * cellV, tv1 = (PROFILE + 1) * cellV;
        int a = Math.round(clamp(alpha) * 255f);
        int lv = Math.round(clamp(lum) * 255f);
        int c = (a << 24) | (lv << 16) | (lv << 8) | lv;
        // only the rows this part's thickness can reach, inset half a texel
        float zt = Math.min(m.zmax, p.t + 0.05f);
        float va = tv0 + (tv1 - tv0) * (0.5f - 0.5f * zt / m.zmax);
        float vb = tv0 + (tv1 - tv0) * (0.5f + 0.5f * zt / m.zmax);
        for (int i = 0; i < nu; i++) {
            float ua = p.u0 + len * i / nu, ub = p.u0 + len * (i + 1) / nu;
            Quad q = f.next();
            q.light = false;
            q.group = group;
            corner(q, 0, m, k, ua, -zt, unit, ua / m.len, va, c);
            corner(q, 1, m, k, ua, zt, unit, ua / m.len, vb, c);
            corner(q, 2, m, k, ub, zt, unit, ub / m.len, vb, c);
            corner(q, 3, m, k, ub, -zt, unit, ub / m.len, va, c);
        }
    }

    private static void corner(Quad q, int i, Model m, Rig.T k, float u, float z, float unit, float tu, float tv, int c) {
        world(k, u, centre(m, u), z, W);
        float s = proj(W[2], unit);
        set(q, i, W[0] * s, W[1] * s, tu, tv, c);
    }

    private static float proj(float z, float unit) {
        return unit * D / (D + z);
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * One layer of a part, as a grid of quads over the part's length.
     *
     * @param mode >= 0: flat brightness; -1: per vertex face light; -2: gloss highlight;
     *             -3: bevel glint
     */
    private static void layer(Frame f, Model m, Rig.T k, Part p, int cell, float z, float unit,
                              float lum, float mode, float alpha, int group, boolean light,
                              float fx, float fy, float fz, float ex, float ey, float ez) {
        float len = p.u1 - p.u0;
        boolean fine = mode < 0f;
        int nu = Math.max(1, (int) Math.ceil(len / (fine ? 0.7f : 1.6f)));
        int nv = fine ? 6 : 3;
        float v0 = -m.half + 0.03f, v1 = m.half - 0.03f;
        float cellV = 1f / CELLS;
        // bevel normal: the face tipped 38 degrees towards the cutting edge
        float bc = 0.788f, bs = 0.616f;
        float bnx = fx * bc + ex * bs, bny = fy * bc + ey * bs, bnz = fz * bc + ez * bs;

        int a = Math.round(clamp(alpha) * 255f);
        float[] px = new float[(nu + 1) * (nv + 1)];
        float[] py = new float[px.length];
        int[] col = new int[px.length];
        for (int j = 0; j <= nv; j++) {
            float v = v0 + (v1 - v0) * j / nv;
            for (int i = 0; i <= nu; i++) {
                float u = p.u0 + len * i / nu;
                world(k, u, v, z, W);
                float s = proj(W[2], unit);
                int idx = j * (nu + 1) + i;
                px[idx] = W[0] * s;
                py[idx] = W[1] * s;
                float b;
                if (mode >= 0f) {
                    b = lum;
                } else if (mode == -1f) {
                    // a gentle falloff along the knife towards the key light
                    float vx = -W[0], vy = -W[1], vz = -D - W[2];
                    float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    float rim = 1f - Math.abs((fx * vx + fy * vy + fz * vz) / vl);
                    b = clamp(lum + 0.10f * rim * rim);
                } else {
                    float nX = mode == -3f ? bnx : fx, nY = mode == -3f ? bny : fy, nZ = mode == -3f ? bnz : fz;
                    b = highlight(W[0], W[1], W[2], nX, nY, nZ, mode == -3f);
                }
                if (light) {
                    int lv = Math.round(clamp(b * alpha) * 255f);
                    col[idx] = (a << 24) | (lv << 16) | (lv << 8) | lv;
                } else {
                    int lv = Math.round(clamp(b) * 255f);
                    col[idx] = (a << 24) | (lv << 16) | (lv << 8) | lv;
                }
            }
        }
        // a light layer with nothing to add is skipped
        if (light) {
            boolean any = false;
            for (int c : col) {
                if ((c & 0xFF) > 2) {
                    any = true;
                    break;
                }
            }
            if (!any) return;
        }
        float tv0 = cell * cellV, tv1 = (cell + 1) * cellV;
        for (int j = 0; j < nv; j++) {
            for (int i = 0; i < nu; i++) {
                Quad q = f.next();
                q.light = light;
                q.group = group;
                int i00 = j * (nu + 1) + i, i10 = i00 + 1, i01 = i00 + nu + 1, i11 = i01 + 1;
                float ua = (p.u0 + len * i / nu) / m.len, ub = (p.u0 + len * (i + 1) / nu) / m.len;
                float va = tv0 + (tv1 - tv0) * j / nv, vb = tv0 + (tv1 - tv0) * (j + 1) / nv;
                set(q, 0, px[i00], py[i00], ua, va, col[i00]);
                set(q, 1, px[i01], py[i01], ua, vb, col[i01]);
                set(q, 2, px[i11], py[i11], ub, vb, col[i11]);
                set(q, 3, px[i10], py[i10], ub, va, col[i10]);
            }
        }
    }

    private static void set(Quad q, int i, float x, float y, float u, float v, int c) {
        q.x[i] = x;
        q.y[i] = y;
        q.u[i] = u;
        q.v[i] = v;
        q.c[i] = c;
    }

    /**
     * Studio lighting for the light passes: a large softbox up and to the left, a tall strip
     * light on the right and a sharp point highlight, all read along the mirror reflection of
     * the view.  Every vertex sees the studio from a slightly different angle, so as the knife
     * turns the reflections slide along the blade instead of the whole face blinking.
     */
    private static float highlight(float x, float y, float z, float nx, float ny, float nz, boolean bevel) {
        float vx = -x, vy = -y, vz = -D - z;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= vl;
        vy /= vl;
        vz /= vl;
        float ndv = nx * vx + ny * vy + nz * vz;
        if (ndv <= 0f) return 0f;
        // mirror reflection of the view direction
        float rx = 2f * ndv * nx - vx, ry = 2f * ndv * ny - vy, rz = 2f * ndv * nz - vz;
        // softbox: a wide soft lobe towards the upper left, in front of the knife
        float sb = rx * -0.48f + ry * -0.58f + rz * -0.66f;
        float soft = (float) Math.pow(Math.max(0f, sb), bevel ? 22.0 : 16.0);
        // strip light: a narrow tall band on the right; brightness falls off across its width
        float across = rx - 0.62f;
        float strip = (float) Math.exp(-(across * across) / (bevel ? 0.006f : 0.010f)) * Math.max(0f, -rz);
        // a hard point highlight from the key light
        float lx = SPX - x, ly = SPY - y, lz = SPZ - z;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        float rdl = Math.max(0f, (rx * lx + ry * ly + rz * lz) / ll);
        float point = (float) Math.pow(rdl, bevel ? 160.0 : 110.0);
        float fres = (float) Math.pow(1f - ndv, 5.0);
        if (bevel) return 1.15f * soft + 0.75f * strip + 1.4f * point + 0.30f * fres;
        return 0.80f * soft + 0.55f * strip + 1.1f * point + 0.05f + 0.22f * fres;
    }
}
