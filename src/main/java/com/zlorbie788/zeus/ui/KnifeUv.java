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
 * UV mapped knife models: standard Minecraft item model JSON plus real 64/128px textures.
 *
 * This is the replacement for the procedural triangle knives.  A knife is a handful of
 * thin blocks whose faces carry a uv rect into a texture, and all of the shape detail -
 * silhouette, grind line, fuller, serrations, grain, grip ribs, screws - lives in that
 * texture, so the knife stays crisp at any size and costs a few dozen textured quads
 * instead of hundreds of tinted triangles.
 *
 * Lighting is Minecraft's own: each face direction has a fixed brightness (the same table
 * the item renderer bakes in), there is no per triangle lambert, and the option flags
 * model GuiLight.FRONT behaviour where the face towards the camera is full brightness.
 *
 * Finishes are a tint layer on the steel faces only.  Faces that reference the "#steel"
 * texture are multiplied by the finish colour, so a Damascus or Tiger texture keeps its
 * pattern and only changes hue; faces that reference "#trim" (brass, rubber, cord) are
 * never tinted, so fittings stay brass on every finish.
 */
public final class KnifeUv {
    private static final String DIR = "knives/models/";
    private static final float D = 90f;

    /** Minecraft item light: fixed per face, no per triangle shading. */
    private static float faceShade(String dir) {
        return switch (dir) {
            case "north" -> 1.00f;   // straight at the camera
            case "south" -> 0.72f;
            case "east", "west" -> 0.84f;
            case "up" -> 0.92f;
            default -> 0.60f;        // down
        };
    }

    static final class Face {
        final float[] p = new float[12];       // four corners, model space
        float u0, v0, u1, v1;
        float shade;
        boolean tinted;
        Tex.Img img;
        float[] sx = new float[4], sy = new float[4];
        float depth;
    }

    static final class Model {
        String name;
        String knife;
        final List<Face> faces = new ArrayList<>();
    }

    private static Map<String, Model> models;
    private static boolean tried;

    private KnifeUv() {
    }

    public static boolean has(String name) {
        Model m = get(name);
        return m != null && !m.faces.isEmpty();
    }

    private static Model get(String name) {
        load();
        return models == null ? null : models.get(name);
    }

    private static synchronized void load() {
        if (tried) return;
        tried = true;
        models = new HashMap<>();
        try (var in = KnifeUv.class.getResourceAsStream("/assets/zeus/knives/models/index.txt")) {
            if (in == null) return;
            String list = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : list.split("\n")) {
                String id = line.trim();
                if (id.isEmpty() || id.startsWith("#")) continue;
                Model m = parse(id);
                if (m != null) {
                    models.put(id, m);
                    if (m.knife != null && !m.knife.isEmpty()) models.put(m.knife, m);
                }
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

            Map<String, Object[]> tex = new HashMap<>();
            JsonObject texObj = root.getAsJsonObject("textures");
            if (texObj != null) {
                for (Map.Entry<String, JsonElement> e : texObj.entrySet()) {
                    String ref = e.getValue().getAsString();
                    if (ref.startsWith("#")) continue;
                    String path = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
                    Tex.Img img = Tex.sprite("textures/" + path + ".png");
                    if (img == null) return null;
                    boolean tinted = e.getKey().equals("steel") || path.endsWith("_steel");
                    tex.put("#" + e.getKey(), new Object[] {img, tinted});
                }
            }

            for (JsonElement ee : root.getAsJsonArray("elements")) {
                JsonObject e = ee.getAsJsonObject();
                if (e.has("rotation") && !e.get("rotation").isJsonNull()) {
                    // Element rotations are only used for the swept guard wings; treat a
                    // non zero angle as unsupported so a model never renders wrong.
                    JsonObject rot = e.getAsJsonObject("rotation");
                    if (Math.abs(rot.get("angle").getAsFloat()) > 0.001f) continue;
                }
                JsonArray f = e.getAsJsonArray("from");
                JsonArray t = e.getAsJsonArray("to");
                float x0 = f.get(0).getAsFloat(), y0 = f.get(1).getAsFloat(), z0 = f.get(2).getAsFloat();
                float x1 = t.get(0).getAsFloat(), y1 = t.get(1).getAsFloat(), z1 = t.get(2).getAsFloat();
                JsonObject faces = e.getAsJsonObject("faces");
                for (Map.Entry<String, JsonElement> fe : faces.entrySet()) {
                    String dir = fe.getKey();
                    JsonObject fo = fe.getValue().getAsJsonObject();
                    String ref = fo.has("texture") ? fo.get("texture").getAsString() : "#steel";
                    Object[] tinfo = tex.get(ref);
                    if (tinfo == null) continue;
                    Face fc = new Face();
                    fc.img = (Tex.Img) tinfo[0];
                    fc.tinted = (Boolean) tinfo[1];
                    fc.shade = faceShade(dir);
                    JsonArray uv = fo.getAsJsonArray("uv");
                    fc.u0 = uv.get(0).getAsFloat();
                    fc.v0 = uv.get(1).getAsFloat();
                    fc.u1 = uv.get(2).getAsFloat();
                    fc.v1 = uv.get(3).getAsFloat();
                    corners(dir, x0, x1, y0, y1, z0, z1, fc.p);
                    m.faces.add(fc);
                }
            }
            return m;
        } catch (Exception | Error e) {
            com.zlorbie788.zeus.core.Zeus.LOG.warn("Could not read knife model {}", id, e);
            return null;
        }
    }

    /** Corners in model space, wound so uv(0,0) lands on the top left of the face. */
    private static void corners(String dir, float x0, float x1, float y0, float y1,
                                float z0, float z1, float[] out) {
        float[][] c;
        switch (dir) {
            case "north" -> c = new float[][] {{x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}};
            case "south" -> c = new float[][] {{x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}};
            case "east" -> c = new float[][] {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case "west" -> c = new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case "up" -> c = new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}};
            default -> c = new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
        }
        for (int i = 0; i < 4; i++) {
            out[i * 3] = c[i][0];
            out[i * 3 + 1] = c[i][1];
            out[i * 3 + 2] = c[i][2];
        }
    }

    public static boolean draw(GuiGraphics g, String name, Rig.T k, float[] root,
                               KnifeArt.Look look, float unit, float alpha) {
        Model m = get(name);
        if (m == null) return false;
        float sc = KnifeArt.Q;
        int tint = tintFor(look);
        int count = 0;
        Face[] order = new Face[m.faces.size()];
        for (Face f : m.faces) {
            float depth = 0f;
            boolean ok = true;
            for (int i = 0; i < 4; i++) {
                float x = f.p[i * 3], y = f.p[i * 3 + 1], z = f.p[i * 3 + 2];
                if (root != null) {
                    float[] q = rotate(x, y, z, root);
                    x = q[0];
                    y = q[1];
                    z = q[2];
                }
                float[] w = k.point(x * sc, y * sc, z * sc);
                if (!Float.isFinite(w[0]) || !Float.isFinite(w[1]) || !Float.isFinite(w[2])) {
                    ok = false;
                    break;
                }
                float kk = unit * D / (D + w[2]);
                f.sx[i] = w[0] * kk;
                f.sy[i] = w[1] * kk;
                depth += w[2];
            }
            if (!ok) continue;
            f.depth = depth / 4f;
            order[count++] = f;
        }
        if (count == 0) return false;
        java.util.Arrays.sort(order, 0, count, (p, q) -> Float.compare(p.depth, q.depth));
        for (int i = 0; i < count; i++) {
            Face f = order[i];
            float ex = f.sx[1] - f.sx[0], ey = f.sy[1] - f.sy[0];
            float fx = f.sx[3] - f.sx[0], fy = f.sy[3] - f.sy[0];
            if (Math.abs(ex * fy - ey * fx) < 1e-4f) continue;
            int base = f.tinted ? tint : 0xFFFFFFFF;
            int col = Draw.rgba(scale(base, f.shade), alpha);
            float tw = f.img.w / 16f, th = f.img.h / 16f;
            var pose = g.pose();
            pose.pushMatrix();
            pose.mul(new org.joml.Matrix3x2f(ex, ey, fx, fy, f.sx[0], f.sy[0]));
            g.blit(RenderPipelines.GUI_TEXTURED, f.img.id, 0, 0, f.u0 * tw, f.v0 * th, 1, 1,
                    Math.max(1, Math.round((f.u1 - f.u0) * tw)),
                    Math.max(1, Math.round((f.v1 - f.v0) * th)),
                    f.img.w, f.img.h, col);
            pose.popMatrix();
        }
        return count > 0;
    }

    /** Finish tint: the steel texture is authored neutral, the finish supplies the hue. */
    private static int tintFor(KnifeArt.Look look) {
        // The steel texture carries the detail, the finish supplies the colour: a mostly
        // saturated tint keeps every etched line while turning the blade into the finish.
        return Draw.mix(0xFFFFFFFF, look.blade[1], 0.75f);
    }

    private static int scale(int colour, float s) {
        int r = (int) (((colour >> 16) & 255) * s);
        int g = (int) (((colour >> 8) & 255) * s);
        int b = (int) ((colour & 255) * s);
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Rotation about the clip pivot, matching KnifeMesh's transform order. */
    private static float[] rotate(float x, float y, float z, float[] r) {
        float[] out = new float[3];
        float d = (float) (Math.PI / 180.0);
        float cx = (float) Math.cos(r[0] * d), sx = (float) Math.sin(r[0] * d);
        float cy = (float) Math.cos(r[1] * d), sy = (float) Math.sin(r[1] * d);
        float cz = (float) Math.cos(r[2] * d), sz = (float) Math.sin(r[2] * d);
        float m0 = cz * cy, m1 = cz * sy * sx - sz * cx, m2 = cz * sy * cx + sz * sx;
        float m3 = sz * cy, m4 = sz * sy * sx + cz * cx, m5 = sz * sy * cx - cz * sx;
        float m6 = -sy, m7 = cy * sx, m8 = cy * cx;
        float px = x - r[6], py = y - r[7], pz = z - r[8];
        out[0] = r[6] + r[3] + m0 * px + m1 * py + m2 * pz;
        out[1] = r[7] + r[4] + m3 * px + m4 * py + m5 * pz;
        out[2] = r[8] + r[5] + m6 * px + m7 * py + m8 * pz;
        return out;
    }
}
