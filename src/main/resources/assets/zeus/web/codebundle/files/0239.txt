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
import net.minecraft.client.renderer.RenderPipelines;

/**
 * UV mapped knife models.
 *
 * A knife is a list of quads with real uv rectangles into 128x128 texture sheets, which is
 * how the eight Counter-Strike 2 knives are modelled: asymmetric blades, curved claws,
 * saw toothed spines, vented handles and finger rings all come from the geometry outline,
 * while the surface - brushed grain, fuller shadow, bright edge bevel, knurling, wood,
 * paracord, brass - comes from the texture.
 *
 * Lighting is Minecraft's own: one fixed brightness per face direction, the same table the
 * item renderer bakes in.  There is no per triangle lighting.
 *
 * Skins: the blade sheets are greyscale and a finish swaps the sheet (Damascus, Fade,
 * Case hardened, Tiger, Doppler) and multiplies a tint, so a skin never touches the handle,
 * the brass or the grip materials.
 */
public final class KnifeUv {
    private static final String DIR = "knives/models/";
    private static final float D = 90f;
    private static final String[] PATTERNS = {"plain", "damascus", "fade", "case", "tiger", "doppler"};

    /**
     * Which blade sheet a finish uses.  Index order matches KnifeArt.SFINISHES, and the tint
     * comes from the same palette the old renderer used, so every skin keeps its identity.
     */
    private static final int[] FAMILY = {
            0,  // Stainless
            5,  // Blue steel
            2,  // Fade
            2,  // Slaughter
            1,  // Crimson web
            5,  // Doppler
            3,  // Case hardened
            1,  // Night
            3,  // Forest
            5,  // Gamma Doppler
            3,  // Gold
            1,  // Damascus
            0,  // Emerald
            1,  // Obsidian
            3,  // Copper
            2,  // Ruby
            4,  // Tiger
            2,  // Rainbow
    };

    static final class Quad {
        int tex;                 // resolved material index
        float[] p = new float[12];
        float u0, v0, u1, v1;
        float shade;
        float[] sx = new float[4], sy = new float[4];
        float depth;
    }

    static final class Model {
        String name;
        String knife;
        final List<Quad> quads = new ArrayList<>();
        final Map<Integer, Tex.Img> sheets = new HashMap<>();   // material -> steel sheet
        final List<Integer> steelMats = new ArrayList<>();
    }

    private static Map<String, Model> models;
    private static Map<String, Tex.Img> patterns;
    private static boolean tried;

    private KnifeUv() {
    }

    private static float faceShade(float nx, float ny, float nz) {
        if (Math.abs(nz) > 0.7f) return nz < 0f ? 1.00f : 0.72f;
        if (Math.abs(ny) > 0.7f) return ny > 0f ? 0.92f : 0.60f;
        return 0.84f;
    }

    public static boolean has(String name) {
        Model m = get(name);
        return m != null && !m.quads.isEmpty();
    }

    private static Model get(String name) {
        load();
        return models == null ? null : models.get(name);
    }

    private static synchronized void load() {
        if (tried) return;
        tried = true;
        models = new HashMap<>();
        patterns = new HashMap<>();
        for (String p : PATTERNS) {
            Tex.Img img = Tex.sprite("textures/knives/blade_" + p + ".png");
            if (img != null) patterns.put(p, img);
        }
        try (var in = KnifeUv.class.getResourceAsStream("/assets/zeus/" + DIR + "index.txt")) {
            if (in == null) return;
            String list = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : list.split("\n")) {
                String id = line.trim();
                if (id.isEmpty() || id.startsWith("#")) continue;
                Model m = parse(id);
                if (m == null) continue;
                models.put(id, m);
                if (m.knife != null && !m.knife.isEmpty()) models.put(m.knife, m);
            }
        } catch (Exception | Error e) {
            models = new HashMap<>();
        }
    }

    private static Model parse(String id) {
        try (var in = KnifeUv.class.getResourceAsStream("/assets/zeus/" + DIR + id + ".json")) {
            if (in == null) return null;
            JsonObject root;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            Model m = new Model();
            m.name = id;
            if (root.has("name") && root.get("name").isJsonPrimitive()) {
                m.knife = root.get("name").getAsString();
            }
            Map<String, Integer> matIndex = new HashMap<>();
            Map<Integer, String> matKey = new HashMap<>();
            JsonObject texs = root.getAsJsonObject("textures");
            if (texs != null) {
                for (Map.Entry<String, JsonElement> e : texs.entrySet()) {
                    String ref = e.getValue().getAsString();
                    String path = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
                    Tex.Img img = Tex.sprite("textures/" + path + ".png");
                    if (img == null) continue;
                    int idx = matIndex.size() + 1;
                    matIndex.put("#" + e.getKey(), idx);
                    matKey.put(idx, "#" + e.getKey());
                    m.sheets.put(idx, img);
                    if (e.getKey().equals("steel")) m.steelMats.add(idx);
                }
            }
            for (JsonElement qe : root.getAsJsonArray("quads")) {
                JsonObject o = qe.getAsJsonObject();
                String key = o.get("t").getAsString();
                Integer idx = matIndex.get(key);
                if (idx == null) continue;
                Quad q = new Quad();
                q.tex = idx;
                JsonArray v = o.getAsJsonArray("v");
                for (int i = 0; i < 12; i++) q.p[i] = v.get(i).getAsFloat();
                JsonArray uv = o.getAsJsonArray("uv");
                q.u0 = uv.get(0).getAsFloat();
                q.v0 = uv.get(1).getAsFloat();
                q.u1 = uv.get(2).getAsFloat();
                q.v1 = uv.get(3).getAsFloat();
                m.quads.add(q);
            }
            return m;
        } catch (Exception | Error e) {
            com.zlorbie788.zeus.core.Zeus.LOG.warn("Could not read knife model {}", id, e);
            return null;
        }
    }

    public static boolean draw(GuiGraphics g, String name, Rig.T k, float[] root,
                               KnifeArt.Look look, float unit, float alpha) {
        Model m = get(name);
        if (m == null) return false;
        float sc = KnifeArt.Q;
        int tint = tintFor(look);
        Tex.Img steel = steelSheet(look);
        int count = 0;
        Quad[] order = new Quad[m.quads.size()];
        for (Quad q : m.quads) {
            float depth = 0f;
            boolean ok = true;
            for (int i = 0; i < 4; i++) {
                float x = q.p[i * 3], y = q.p[i * 3 + 1], z = q.p[i * 3 + 2];
                if (root != null) {
                    float[] r = rotate(x, y, z, root);
                    x = r[0];
                    y = r[1];
                    z = r[2];
                }
                float[] w = k.point(x * sc, y * sc, z * sc);
                if (!Float.isFinite(w[0]) || !Float.isFinite(w[1]) || !Float.isFinite(w[2])) {
                    ok = false;
                    break;
                }
                float kk = unit * D / (D + w[2]);
                q.sx[i] = w[0] * kk;
                q.sy[i] = w[1] * kk;
                depth += w[2];
            }
            if (!ok) continue;
            q.depth = depth / 4f;
            // fixed Minecraft style face brightness from the quad normal
            float ax = q.p[0], ay = q.p[1], az = q.p[2];
            float bx = q.p[3], by = q.p[4], bz = q.p[5];
            float cx = q.p[6], cy = q.p[7], cz = q.p[8];
            float ux = bx - ax, uy = by - ay, uz = bz - az;
            float vx = cx - ax, vy = cy - ay, vz = cz - az;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            q.shade = len < 1e-6f ? 0.84f : faceShade(nx / len, ny / len, nz / len);
            order[count++] = q;
        }
        if (count == 0) return false;
        java.util.Arrays.sort(order, 0, count, (p, q) -> Float.compare(p.depth, q.depth));
        for (int i = 0; i < count; i++) {
            Quad q = order[i];
            float ex = q.sx[1] - q.sx[0], ey = q.sy[1] - q.sy[0];
            float fx = q.sx[3] - q.sx[0], fy = q.sy[3] - q.sy[0];
            if (Math.abs(ex * fy - ey * fx) < 1e-4f) continue;
            boolean blade = m.steelMats.contains(q.tex);
            Tex.Img img = blade && steel != null ? steel : m.sheets.get(q.tex);
            if (img == null) continue;
            int base = blade ? tint : 0xFFFFFFFF;
            int col = Draw.rgba(scale(base, q.shade), alpha);
            float tw = img.w / 16f, th = img.h / 16f;
            var pose = g.pose();
            pose.pushMatrix();
            pose.mul(new org.joml.Matrix3x2f(ex, ey, fx, fy, q.sx[0], q.sy[0]));
            g.blit(RenderPipelines.GUI_TEXTURED, img.id, 0, 0, q.u0 * tw, q.v0 * th, 1, 1,
                    Math.max(1, Math.round((q.u1 - q.u0) * tw)),
                    Math.max(1, Math.round((q.v1 - q.v0) * th)),
                    img.w, img.h, col);
            pose.popMatrix();
        }
        return count > 0;
    }

    /** The blade sheet for a finish: Damascus, Fade, Case hardened, Tiger or Doppler. */
    private static Tex.Img steelSheet(KnifeArt.Look look) {
        if (patterns == null || patterns.isEmpty()) return null;
        int f = Math.max(0, Math.min(FAMILY.length - 1, look.finish));
        return patterns.get(PATTERNS[FAMILY[f]]);
    }

    private static int tintFor(KnifeArt.Look look) {
        return Draw.mix(0xFFFFFFFF, look.blade[1], 0.45f);
    }

    private static int scale(int colour, float s) {
        int r = Math.max(0, Math.min(255, (int) (((colour >> 16) & 255) * s)));
        int g = Math.max(0, Math.min(255, (int) (((colour >> 8) & 255) * s)));
        int b = Math.max(0, Math.min(255, (int) ((colour & 255) * s)));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float[] rotate(float x, float y, float z, float[] r) {
        float d = (float) (Math.PI / 180.0);
        float cx = (float) Math.cos(r[0] * d), sx = (float) Math.sin(r[0] * d);
        float cy = (float) Math.cos(r[1] * d), sy = (float) Math.sin(r[1] * d);
        float cz = (float) Math.cos(r[2] * d), sz = (float) Math.sin(r[2] * d);
        float m0 = cz * cy, m1 = cz * sy * sx - sz * cx, m2 = cz * sy * cx + sz * sx;
        float m3 = sz * cy, m4 = sz * sy * sx + cz * cx, m5 = sz * sy * cx - cz * sx;
        float m6 = -sy, m7 = cy * sx, m8 = cy * cx;
        float px = x - r[6], py = y - r[7], pz = z - r[8];
        return new float[] {
                r[6] + r[3] + m0 * px + m1 * py + m2 * pz,
                r[7] + r[4] + m3 * px + m4 * py + m5 * pz,
                r[8] + r[5] + m6 * px + m7 * py + m8 * pz};
    }
}
