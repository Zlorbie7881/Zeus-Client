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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * The HD tactical knives: high resolution sheet art drawn as a lit, layered solid.
 *
 * <p>Every knife is a strip of sheet art in {@code knives/hd/tactical.json}: the parts run
 * along u (grip, guard, blade) and the sheet holds seven cells, one material layer each -
 * FACE, RIM50, RIM13, SIDE, GLOSS, EDGE and PROFILE.  The alpha of the FACE cell is the
 * knife's outline, so saw teeth, the fuller slot, the lanyard ring and the grip grooves are
 * exactly the artwork instead of extra geometry.
 *
 * <p>The quad builder here mirrors {@link KnifeUv}'s projection and Minecraft's fixed face
 * brightness, so the HD knives sit in the same hand, at the same scale, with the same tricks.
 */
public final class KnifeHd {
    private KnifeHd() {
    }

    public static final String JSON = "/assets/zeus/knives/hd/tactical.json";
    public static final int CELLS = 7;
    public static final int FACE = 0, RIM50 = 1, RIM13 = 2, SIDE = 3, GLOSS = 4, EDGE = 5, PROFILE = 6;

    /** Sheet units to rig units, and where the pommel end sits, the same as the straight knives. */
    private static final float SC = 1.2f;
    private static final float U0 = 0.3f;
    private static final float D = 90f;

    public static final class Part {
        public String name;
        public float u0, u1, t, r;
    }

    public static final class Model {
        public String id;
        public String name;
        public int glow;
        public float len, half, zmax = 1.4f;
        public float tipU, tipV;
        public float[] centre = new float[0];
        public final List<Part> parts = new ArrayList<>();
    }

    private static List<Model> models;
    private static final Map<String, Integer> BY_NAME = new HashMap<>();
    private static final Map<String, Tex.Img> SHEETS = new HashMap<>();
    private static boolean tried;

    public static synchronized List<Model> models() {
        if (tried) return models;
        tried = true;
        models = new ArrayList<>();
        try (InputStream in = KnifeHd.class.getResourceAsStream(JSON)) {
            if (in == null) return models;
            JsonObject root;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            float len = root.get("len").getAsFloat();
            float half = root.get("half").getAsFloat();
            float zmax = root.has("zmax") ? root.get("zmax").getAsFloat() : 1.4f;
            for (JsonElement ke : root.getAsJsonArray("knives")) {
                JsonObject k = ke.getAsJsonObject();
                Model m = new Model();
                m.id = k.get("id").getAsString();
                m.name = k.get("name").getAsString();
                String glow = k.get("glow").getAsString().replace("#", "");
                m.glow = 0xFF000000 | Integer.parseInt(glow, 16);
                m.len = len;
                m.half = half;
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
                BY_NAME.put(m.name, models.size());
                BY_NAME.put(m.id, models.size());
                models.add(m);
            }
        } catch (Exception | Error e) {
            models = new ArrayList<>();
            com.zlorbie788.zeus.core.Zeus.LOG.warn("Could not read the HD knife table", e);
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

    /** HD index for a knife name, or -1. */
    public static int indexOf(String name) {
        if (name == null) return -1;
        models();
        Integer i = BY_NAME.get(name);
        return i == null ? -1 : i;
    }

    public static boolean has(String name) {
        return indexOf(name) >= 0;
    }

    public static String sheet(Model m, int level) {
        return "textures/knives/hd/" + m.id + "_" + Math.max(0, Math.min(2, level)) + ".png";
    }

    // ------------------------------------------------------------------ model space
    private static float mx(float v) {
        return v * SC;
    }

    private static float my(float u) {
        return (u - U0) * SC;
    }

    /** Where the grip is held, in rig units. */
    public static float grip(Model m) {
        if (m == null) return my(6f);
        for (Part p : m.parts) {
            if ("handle".equals(p.name)) return my(p.u0 + (p.u1 - p.u0) * 0.42f);
        }
        return my(6f);
    }

    private static float centre(Model m, float u) {
        int n = m.centre.length;
        if (n < 2) return 0f;
        float x = Math.max(0f, Math.min(n - 1.001f, u / m.len * (n - 1)));
        int i = (int) x;
        float t = x - i;
        return m.centre[i] * (1 - t) + m.centre[i + 1] * t;
    }

    // ------------------------------------------------------------------ draw
    private static final float[] W = new float[3];

    private static void world(Rig.T k, float u, float v, float z, float[] out) {
        float lx = mx(v), ly = my(u), lz = z * SC;
        float[] r = k.r;
        out[0] = r[0] * lx + r[1] * ly + r[2] * lz + k.x;
        out[1] = r[3] * lx + r[4] * ly + r[5] * lz + k.y;
        out[2] = r[6] * lx + r[7] * ly + r[8] * lz + k.z;
    }

    private static float proj(float z, float unit) {
        return unit * D / (D + z);
    }

    /** One textured quad ready to blit, in screen space. */
    private static final class Quad {
        float[] sx = new float[4];
        float[] sy = new float[4];
        float[] u = new float[4];
        float[] v = new float[4];
        int tint;
        float depth;
    }

    private static Quad q(List<Quad> pool, int[] used) {
        Quad quad;
        if (used[0] < pool.size()) {
            quad = pool.get(used[0]);
        } else {
            quad = new Quad();
            pool.add(quad);
        }
        used[0]++;
        return quad;
    }

    /**
     * Draws a knife by name at the rig transform, the unit scale and the opacity.
     *
     * @return false when the knife could not be drawn, so the caller can fall back
     */
    public static boolean draw(GuiGraphics g, String name, Rig.T k, KnifeArt.Look look, float unit, float alpha) {
        int idx = indexOf(name);
        if (idx < 0) return false;
        Model m = model(idx);
        if (m == null || alpha <= 0.004f) return false;
        boolean fast = Perf.gameFast();
        List<Quad> pool = new ArrayList<>();
        int[] used = {0};
        try {
            // how long the knife is on screen picks the sheet level, since there are no mipmaps
            world(k, 0f, 0f, 0f, W);
            float ax = W[0] * proj(W[2], unit), ay = W[1] * proj(W[2], unit);
            world(k, m.len, 0f, 0f, W);
            float bx = W[0] * proj(W[2], unit), by = W[1] * proj(W[2], unit);
            float lenPx = (float) Math.hypot(bx - ax, by - ay) * 2f;
            int level = lenPx > 620f ? 0 : lenPx > 300f ? 1 : 2;
            Tex.Img sheet = img(m, level);
            if (sheet == null) sheet = img(m, 1);
            if (sheet == null) return false;

            float[] r = k.r;
            float nx = r[2], ny = r[5], nz = r[8];

            int count = 0;
            float step = fast ? 1.8f : 0.85f;
            int nv = fast ? 2 : 4;
            int shadeDefault = 232;
            for (Part p : m.parts) {
                // which face of the slab is towards the camera (camera at 0, 0, -D)
                world(k, (p.u0 + p.u1) * 0.5f, 0f, 0f, W);
                float toCam = nx * (0f - W[0]) + ny * (0f - W[1]) + nz * (-D - W[2]);
                float side = toCam >= 0f ? 1f : -1f;
                float fx = nx * side, fy = ny * side, fz = nz * side;
                int shade = Math.round(255f * clamp(0.56f + 0.44f * Math.abs(fz)));
                int nqu = Math.max(1, (int) Math.ceil((p.u1 - p.u0) / step));
                for (int j = 0; j < nv; j++) {
                    float va = -m.half + 2f * m.half * j / nv;
                    float vb = -m.half + 2f * m.half * (j + 1) / nv;
                    for (int i = 0; i < nqu; i++) {
                        float ua = p.u0 + (p.u1 - p.u0) * i / nqu;
                        float ub = p.u0 + (p.u1 - p.u0) * (i + 1) / nqu;
                        Quad quad = q(pool, used);
                        corner(quad, 0, m, k, ua, va, p.t * side, unit);
                        corner(quad, 1, m, k, ua, vb, p.t * side, unit);
                        corner(quad, 2, m, k, ub, vb, p.t * side, unit);
                        corner(quad, 3, m, k, ub, va, p.t * side, unit);
                        float tu0 = ua / m.len, tu1 = ub / m.len;
                        float tv0 = (FACE + (j / (float) nv)) / CELLS;
                        float tv1 = (FACE + ((j + 1) / (float) nv)) / CELLS;
                        quad.u[0] = tu0;
                        quad.u[1] = tu0;
                        quad.u[2] = tu1;
                        quad.u[3] = tu1;
                        quad.v[0] = tv0;
                        quad.v[1] = tv1;
                        quad.v[2] = tv1;
                        quad.v[3] = tv0;
                        quad.tint = scale(0xFFFFFFFF, shade / 255f);
                        world(k, (ua + ub) * 0.5f, (va + vb) * 0.5f, p.t * side, W);
                        quad.depth = W[2];
                        count++;
                    }
                }
                // the side wall, so an edge-on knife is still solid
                int ns = Math.max(1, (int) Math.ceil((p.u1 - p.u0) / 1.6f));
                for (int i = 0; i < ns; i++) {
                    float ua = p.u0 + (p.u1 - p.u0) * i / ns;
                    float ub = p.u0 + (p.u1 - p.u0) * (i + 1) / ns;
                    Quad quad = q(pool, used);
                    wall(quad, 0, m, k, ua, -p.t, unit);
                    wall(quad, 1, m, k, ub, -p.t, unit);
                    wall(quad, 2, m, k, ub, p.t, unit);
                    wall(quad, 3, m, k, ua, p.t, unit);
                    float tu0 = ua / m.len, tu1 = ub / m.len;
                    float tv = (SIDE + 0.5f) / CELLS;
                    for (int c = 0; c < 4; c++) {
                        quad.u[c] = c == 0 || c == 3 ? tu0 : tu1;
                        quad.v[c] = tv;
                    }
                    quad.tint = scale(0xFFFFFFFF, 0.72f);
                    world(k, (ua + ub) * 0.5f, 0f, 0f, W);
                    quad.depth = W[2];
                    count++;
                }
            }
            if (count == 0) return false;

            Quad[] order = new Quad[count];
            for (int i = 0; i < count; i++) order[i] = pool.get(i);
            java.util.Arrays.sort(order, (a, b) -> Float.compare(a.depth, b.depth));

            float tw = sheet.w;
            float th = sheet.h;
            var pose = g.pose();
            int drawn = 0;
            for (Quad quad : order) {
                float ex = quad.sx[1] - quad.sx[0], ey = quad.sy[1] - quad.sy[0];
                float gx = quad.sx[3] - quad.sx[0], gy = quad.sy[3] - quad.sy[0];
                if (Math.abs(ex * gy - ey * gx) < 1e-4f) continue;
                int col = Draw.rgba(quad.tint, alpha);
                pose.pushMatrix();
                pose.mul(new org.joml.Matrix3x2f(ex, ey, gx, gy, quad.sx[0], quad.sy[0]));
                g.blit(RenderPipelines.GUI_TEXTURED, sheet.id, 0, 0,
                        quad.u[0] * tw, quad.v[0] * th,
                        1, 1,
                        Math.max(1, Math.round((quad.u[2] - quad.u[0]) * tw)),
                        Math.max(1, Math.round((quad.v[2] - quad.v[0]) * th)),
                        sheet.w, sheet.h, col);
                pose.popMatrix();
                drawn++;
            }
            return drawn > 0;
        } catch (RuntimeException | Error e) {
            com.zlorbie788.zeus.core.Zeus.LOG.warn("HD knife {} could not be drawn", name, e);
            return false;
        }
    }

    private static void corner(Quad q, int i, Model m, Rig.T k, float u, float v, float z, float unit) {
        world(k, u, v + centre(m, u), z, W);
        float s = proj(W[2], unit);
        q.sx[i] = W[0] * s;
        q.sy[i] = W[1] * s;
    }

    private static void wall(Quad q, int i, Model m, Rig.T k, float u, float z, float unit) {
        world(k, u, centre(m, u), z, W);
        float s = proj(W[2], unit);
        q.sx[i] = W[0] * s;
        q.sy[i] = W[1] * s;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int scale(int colour, float s) {
        int r = Math.max(0, Math.min(255, (int) (((colour >> 16) & 255) * s)));
        int gr = Math.max(0, Math.min(255, (int) (((colour >> 8) & 255) * s)));
        int b = Math.max(0, Math.min(255, (int) ((colour & 255) * s)));
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private static Tex.Img img(Model m, int level) {
        String path = sheet(m, level);
        if (SHEETS.containsKey(path)) return SHEETS.get(path);
        Tex.Img img = Tex.sprite(path);
        SHEETS.put(path, img);
        return img;
    }
}
