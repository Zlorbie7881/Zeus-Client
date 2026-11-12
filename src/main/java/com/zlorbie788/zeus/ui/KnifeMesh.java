package com.zlorbie788.zeus.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

public final class KnifeMesh {
    private static final float D = 90f;
    private static final float LX = -0.40f, LY = -0.55f, LZ = -0.73f;
    private static final int MAX_TRIS = 520;

    private static final String TRI = "textures/gui/anim/tri.png";
    private static boolean broken;
    private static Map<String, Model> models;
    private static boolean tried;

    static final class Mesh {
        String mat;
        float[] v;
        int[] f;
    }

    static final class Part {
        String name;
        float[] pivot;
        final List<Mesh> meshes = new ArrayList<>();
    }

    static final class Model {
        String name;
        boolean butterfly;
        final List<Part> parts = new ArrayList<>();
        final Map<String, Clip> clips = new HashMap<>();
    }

    static final class Clip {
        float d;
        float[] pivot = {0f, 0f, 0f};
        float[][] rot = new float[0][];
        float[][] pos = new float[0][];
        final Map<String, float[][][]> parts = new HashMap<>();
    }

    private static float[][] rows(JsonElement e) {
        if (e == null || !e.isJsonArray()) return new float[0][];
        JsonArray a = e.getAsJsonArray();
        float[][] out = new float[a.size()][4];
        for (int i = 0; i < out.length; i++) {
            JsonArray r = a.get(i).getAsJsonArray();
            for (int j = 0; j < 4; j++) out[i][j] = r.get(j).getAsFloat();
        }
        return out;
    }

    private KnifeMesh() {
    }

    private static synchronized void load() {
        if (tried) return;
        tried = true;
        models = new HashMap<>();
        java.io.InputStream in = KnifeMesh.class.getResourceAsStream("/assets/zeus/knives/knives_lo.json");
        if (in == null) {
            com.zlorbie788.zeus.core.Zeus.LOG.warn("The 3D knife models are missing from the jar; the pixel knives are used");
            return;
        }
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement ke : root.getAsJsonArray("knives")) {
                JsonObject k = ke.getAsJsonObject();
                Model m = new Model();
                m.name = k.get("name").getAsString();
                m.butterfly = "butterfly".equals(k.get("kind").getAsString());
                for (JsonElement pe : k.getAsJsonArray("parts")) {
                    JsonObject po = pe.getAsJsonObject();
                    Part p = new Part();
                    p.name = po.get("n").getAsString();
                    JsonArray pv = po.getAsJsonArray("p");
                    p.pivot = new float[] {pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat()};
                    for (JsonElement me : po.getAsJsonArray("meshes")) {
                        JsonObject mo = me.getAsJsonObject();
                        Mesh mesh = new Mesh();
                        mesh.mat = mo.get("m").getAsString();
                        JsonArray v = mo.getAsJsonArray("v");
                        mesh.v = new float[v.size()];
                        for (int i = 0; i < mesh.v.length; i++) mesh.v[i] = v.get(i).getAsFloat();
                        JsonArray f = mo.getAsJsonArray("f");
                        mesh.f = new int[f.size()];
                        for (int i = 0; i < mesh.f.length; i++) mesh.f[i] = f.get(i).getAsInt();
                        p.meshes.add(mesh);
                    }
                    m.parts.add(p);
                }
                JsonObject an = k.has("anims") && k.get("anims").isJsonObject() ? k.getAsJsonObject("anims") : null;
                if (an != null) {
                    for (Map.Entry<String, JsonElement> en : an.entrySet()) {
                        JsonObject co = en.getValue().getAsJsonObject();
                        Clip c = new Clip();
                        c.d = co.get("d").getAsFloat();
                        JsonObject ro = co.getAsJsonObject("root");
                        c.rot = rows(ro.get("rot"));
                        c.pos = rows(ro.get("pos"));
                        if (ro.has("pivot") && ro.get("pivot").isJsonArray()) {
                            JsonArray pv = ro.getAsJsonArray("pivot");
                            c.pivot = new float[] {pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat()};
                        }
                        JsonObject po = co.getAsJsonObject("parts");
                        if (po != null) {
                            for (Map.Entry<String, JsonElement> pe : po.entrySet()) {
                                JsonObject o = pe.getValue().getAsJsonObject();
                                c.parts.put(pe.getKey(), new float[][][] {rows(o.get("rot")), rows(o.get("pos"))});
                            }
                        }
                        m.clips.put(en.getKey(), c);
                    }
                }
                models.put(m.name, m);
            }
        } catch (Exception | Error e) {
            models = new HashMap<>();
        }
    }

    public static boolean has(String name) {
        load();
        return !broken && models.containsKey(name) && Tex.sprite(TRI) != null;
    }

    public static boolean hasClip(String name, String clip) {
        load();
        Model m = models == null ? null : models.get(name);
        return !broken && m != null && m.clips.containsKey(clip);
    }

    private static void sample3(float[][] rows, float t, float[] out) {
        out[0] = out[1] = out[2] = 0f;
        int n = rows.length;
        if (n == 0) return;
        if (t <= rows[0][0]) {
            out[0] = rows[0][1];
            out[1] = rows[0][2];
            out[2] = rows[0][3];
            return;
        }
        if (t >= rows[n - 1][0]) {
            out[0] = rows[n - 1][1];
            out[1] = rows[n - 1][2];
            out[2] = rows[n - 1][3];
            return;
        }
        int i = 0;
        while (rows[i + 1][0] < t) i++;
        float t0 = rows[i][0], t1 = rows[i + 1][0], h = t1 - t0, u = (t - t0) / h, u2 = u * u, u3 = u2 * u;
        for (int c = 1; c <= 3; c++) {
            float m0 = tangent(rows, i, c, n) * h, m1 = tangent(rows, i + 1, c, n) * h;
            float v0 = rows[i][c], v1 = rows[i + 1][c];
            out[c - 1] = (2 * u3 - 3 * u2 + 1) * v0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * v1 + (u3 - u2) * m1;
        }
    }

    private static float tangent(float[][] rows, int j, int c, int n) {
        if (j <= 0 || j >= n - 1) return 0f;
        return (rows[j + 1][c] - rows[j - 1][c]) / (rows[j + 1][0] - rows[j - 1][0]);
    }

    private static final class Xf {
        final float[] r = new float[9];
        final float[] piv = new float[3];
        final float[] mv = new float[3];

        void set(float[] pivot, float[] rotDeg, float[] move) {
            float d = (float) (Math.PI / 180.0);
            float cx = (float) Math.cos(rotDeg[0] * d), sx = (float) Math.sin(rotDeg[0] * d);
            float cy = (float) Math.cos(rotDeg[1] * d), sy = (float) Math.sin(rotDeg[1] * d);
            float cz = (float) Math.cos(rotDeg[2] * d), sz = (float) Math.sin(rotDeg[2] * d);
            r[0] = cz * cy;
            r[1] = cz * sy * sx - sz * cx;
            r[2] = cz * sy * cx + sz * sx;
            r[3] = sz * cy;
            r[4] = sz * sy * sx + cz * cx;
            r[5] = sz * sy * cx - cz * sx;
            r[6] = -sy;
            r[7] = cy * sx;
            r[8] = cy * cx;
            System.arraycopy(pivot, 0, piv, 0, 3);
            System.arraycopy(move, 0, mv, 0, 3);
        }

        void apply(float[] p) {
            float x = p[0] - piv[0], y = p[1] - piv[1], z = p[2] - piv[2];
            p[0] = piv[0] + mv[0] + r[0] * x + r[1] * y + r[2] * z;
            p[1] = piv[1] + mv[1] + r[3] * x + r[4] * y + r[5] * z;
            p[2] = piv[2] + mv[2] + r[6] * x + r[7] * y + r[8] * z;
        }
    }

    private static final float[] S_ROT = new float[3], S_POS = new float[3], P3 = new float[3];
    private static final Xf ROOT = new Xf(), PART = new Xf();

    private static final float[] SX = new float[MAX_TRIS * 3];
    private static final float[] SY = new float[MAX_TRIS * 3];
    private static final float[] DEPTH = new float[MAX_TRIS];
    private static final int[] COL = new int[MAX_TRIS];
    private static float[] wv = new float[3 * 4096];

    public static boolean draw(GuiGraphics g, String name, Rig.T k, float a, float b, float c, KnifeArt.Look look, float unit, float alpha, boolean cheap) {
        return draw(g, name, k, a, b, c, look, unit, alpha, cheap, null, 0f);
    }

    public static boolean drawClip(GuiGraphics g, String name, String clip, float t, Rig.T k, KnifeArt.Look look, float unit, float alpha, boolean cheap) {
        return draw(g, name, k, 0f, 0f, 0f, look, unit, alpha, cheap, clip, t);
    }

    private static boolean draw(GuiGraphics g, String name, Rig.T k, float a, float b, float c, KnifeArt.Look look, float unit, float alpha, boolean cheap, String clip, float clipT) {
        try {
            return drawMesh(g, name, k, a, b, c, look, unit, alpha, cheap, clip, clipT);
        } catch (RuntimeException | Error e) {
            broken = true;
            com.zlorbie788.zeus.core.Zeus.LOG.warn("3D knife failed, using the pixel knives", e);
            return false;
        }
    }

    private static boolean drawMesh(GuiGraphics g, String name, Rig.T k, float a, float b, float c, KnifeArt.Look look, float unit, float alpha, boolean cheap, String clipName, float clipT) {
        load();
        Model m = models.get(name);
        if (m == null) return false;
        Clip clip = clipName == null ? null : m.clips.get(clipName);
        float sc = m.butterfly ? 1.10f : KnifeArt.Q;
        if (clip != null) {
            sample3(clip.rot, clipT, S_ROT);
            sample3(clip.pos, clipT, S_POS);
            ROOT.set(clip.pivot, S_ROT, S_POS);
        }
        int n = 0;
        for (Part p : m.parts) {
            Rig.T t = clip != null ? k : partFrame(m, p, k, a, b, c, sc);
            boolean part = false;
            if (clip != null) {
                float[][][] pc = clip.parts.get(p.name);
                if (pc != null) {
                    sample3(pc[0], clipT, S_ROT);
                    sample3(pc[1], clipT, S_POS);
                    PART.set(p.pivot, S_ROT, S_POS);
                    part = true;
                }
            }
            for (Mesh mesh : p.meshes) {
                int nv = mesh.v.length / 3;
                if (wv.length < nv * 3) wv = new float[nv * 3];
                for (int i = 0; i < nv; i++) {
                    float x = mesh.v[i * 3], y = mesh.v[i * 3 + 1], z = mesh.v[i * 3 + 2];
                    if (clip != null) {
                        P3[0] = x;
                        P3[1] = y;
                        P3[2] = z;
                        if (part) PART.apply(P3);
                        ROOT.apply(P3);
                        x = P3[0];
                        y = P3[1];
                        z = P3[2];
                    }
                    if (m.butterfly) {
                        y = -y;
                        z = -z;
                    }
                    float[] w = t.point(x * sc, y * sc, z * sc);
                    wv[i * 3] = w[0];
                    wv[i * 3 + 1] = w[1];
                    wv[i * 3 + 2] = w[2];
                }
                for (int i = 0; i + 2 < mesh.f.length && n < MAX_TRIS; i += 3) {
                    int ia = mesh.f[i] * 3, ib = mesh.f[i + 1] * 3, ic = mesh.f[i + 2] * 3;
                    float ax = wv[ia], ay = wv[ia + 1], az = wv[ia + 2];
                    float bx = wv[ib], by = wv[ib + 1], bz = wv[ib + 2];
                    float cx = wv[ic], cy = wv[ic + 1], cz = wv[ic + 2];
                    float ux = bx - ax, uy = by - ay, uz = bz - az;
                    float vx = cx - ax, vy = cy - ay, vz = cz - az;
                    float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (!(len > 1e-6f) || !Float.isFinite(len)) continue;
                    nx /= len;
                    ny /= len;
                    nz /= len;
                    if (!Float.isFinite(ax) || !Float.isFinite(ay) || !Float.isFinite(az)) continue;
                    if (!Float.isFinite(bx) || !Float.isFinite(by) || !Float.isFinite(bz)) continue;
                    if (!Float.isFinite(cx) || !Float.isFinite(cy) || !Float.isFinite(cz)) continue;
                    float mx = (ax + bx + cx) / 3f, my = (ay + by + cy) / 3f, mz = (az + bz + cz) / 3f;
                    if (nx * mx + ny * my + nz * (mz + D) >= 0f) continue;
                    float lam = Math.max(0f, nx * LX + ny * LY + nz * LZ);
                    float ka = unit * D / (D + az), kb = unit * D / (D + bz), kc = unit * D / (D + cz);
                    SX[n * 3] = ax * ka;
                    SY[n * 3] = ay * ka;
                    SX[n * 3 + 1] = bx * kb;
                    SY[n * 3 + 1] = by * kb;
                    SX[n * 3 + 2] = cx * kc;
                    SY[n * 3 + 2] = cy * kc;
                    DEPTH[n] = mz;
                    COL[n] = shade(mesh.mat, look, lam, nx, ny, alpha);
                    n++;
                }
            }
        }
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (p, q) -> Float.compare(DEPTH[q], DEPTH[p]));
        float grow = cheap ? 0.3f : 0.45f;
        int painted = 0;
        for (int o = 0; o < n; o++) {
            int i = idx[o];
            float x0 = SX[i * 3], y0 = SY[i * 3], x1 = SX[i * 3 + 1], y1 = SY[i * 3 + 1], x2 = SX[i * 3 + 2], y2 = SY[i * 3 + 2];
            float gx = (x0 + x1 + x2) / 3f, gy = (y0 + y1 + y2) / 3f;
            if (tri(g, grow(x0, gx, grow), grow(y0, gy, grow), grow(x1, gx, grow), grow(y1, gy, grow), grow(x2, gx, grow), grow(y2, gy, grow), COL[i])) painted++;
        }
        return painted > 0;
    }

    private static boolean tri(GuiGraphics g, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        Tex.Img t = Tex.sprite(TRI);
        if (t == null) return false;
        float ux = (bx - ax) / 126f, uy = (by - ay) / 126f, vx = (cx - ax) / 126f, vy = (cy - ay) / 126f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.mul(new org.joml.Matrix3x2f(ux, uy, vx, vy, ax - ux - vx, ay - uy - vy));
        t.drawPart(g, 0, 0, 128, 128, 0, 0, 128, 128, color);
        pose.popMatrix();
        return true;
    }

    private static float grow(float v, float centre, float by) {
        float d = v - centre;
        float l = Math.abs(d);
        return l < 1e-4f ? v : v + d * Math.min(0.12f, by / l);
    }

    private static Rig.T partFrame(Model m, Part p, Rig.T k, float a, float b, float c, float sc) {
        if (!m.butterfly) return k;
        float ang;
        switch (p.name) {
            case "handleB" -> ang = a;
            case "handleA" -> ang = b;
            default -> ang = c - (float) Math.PI;
        }
        float px = p.pivot[0] * sc, py = -p.pivot[1] * sc, pz = -p.pivot[2] * sc;
        return k.move(px, py, pz).rotZ(ang).move(-px, -py, -pz);
    }

    private static int shade(String mat, KnifeArt.Look look, float lam, float nx, float ny, float alpha) {
        int light, mid, dark;
        if ("handle".equals(mat)) {
            light = look.handle[0];
            mid = look.handle[1];
            dark = look.handle[2];
        } else {
            light = look.blade[0];
            mid = look.blade[1];
            dark = look.blade[2];
            if ("pin".equals(mat) || "guard".equals(mat)) {
                light = Draw.mix(look.blade[0], 0xFFFFFFFF, 0.2f);
                mid = look.blade[1];
                dark = look.blade[2];
            }
        }
        float t = Math.min(1f, 0.10f + lam * 1.05f);
        int col = t < 0.5f ? Draw.mix(dark, mid, t * 2f) : Draw.mix(mid, light, (t - 0.5f) * 2f);
        if (!"handle".equals(mat)) {
            float glint = Math.max(0f, 1f - Math.abs(0.5f + nx * 0.9f - ny * 0.6f - 0.62f) * 5f);
            col = Draw.mix(col, 0xFFFFFFFF, 0.28f * glint * lam);
        }
        col |= 0xFF000000;
        return Draw.rgba(col, alpha);
    }
}
