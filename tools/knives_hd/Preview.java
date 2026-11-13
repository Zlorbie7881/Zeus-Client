// Offline preview of the HD knives: compile with the mod classes (KnifeHD, Rig) and gson on the classpath,
// run "java Preview lineup out.png" from the repo root. It rasterises the exact quads KnifeHD builds.
import com.zlorbie788.zeus.ui.KnifeHD;
import com.zlorbie788.zeus.ui.Rig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Offline rasteriser for KnifeHD's real output: same quads, same colours, the GUI shader's
 * rules (texture * vertex colour, discard at alpha 0), TRANSLUCENT blending for the solid
 * groups and premultiplied blending for the light groups, affine uv per triangle, bilinear
 * filtering - which is what the game's GUI_TEXTURED pipelines do.
 */
public class Preview {
    static final String RES = "src/main/resources/assets/zeus/";
    static final Map<String, float[][]> SHEETS = new HashMap<>();
    static final Map<String, int[]> SIZE = new HashMap<>();

    static float[][] sheet(String path) throws Exception {
        if (SHEETS.containsKey(path)) return SHEETS.get(path);
        BufferedImage im = ImageIO.read(new File(RES + path));
        int w = im.getWidth(), h = im.getHeight();
        float[][] px = new float[4][w * h];
        int[] argb = im.getRGB(0, 0, w, h, null, 0, w);
        // ImageIO un-premultiplies nothing for PNG: values are straight, like NativeImage
        BufferedImage raw = im;
        java.awt.image.Raster r = raw.getRaster();
        int[] p = new int[4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                r.getPixel(x, y, p);
                int i = y * w + x;
                px[0][i] = p[0] / 255f;
                px[1][i] = p[1] / 255f;
                px[2][i] = p[2] / 255f;
                px[3][i] = (r.getNumBands() > 3 ? p[3] : 255) / 255f;
            }
        }
        SHEETS.put(path, px);
        SIZE.put(path, new int[] {w, h});
        return px;
    }

    final int W, H;
    final float[] R, G, B;

    Preview(int w, int h, float[] top, float[] bottom) {
        W = w;
        H = h;
        R = new float[w * h];
        G = new float[w * h];
        B = new float[w * h];
        for (int y = 0; y < h; y++) {
            float t = y / (float) (h - 1);
            for (int x = 0; x < w; x++) {
                float cx = (x - w * 0.5f) / w, cy = (y - h * 0.45f) / h;
                float vig = 1f - 0.55f * (cx * cx + cy * cy);
                int i = y * w + x;
                R[i] = (top[0] * (1 - t) + bottom[0] * t) * vig;
                G[i] = (top[1] * (1 - t) + bottom[1] * t) * vig;
                B[i] = (top[2] * (1 - t) + bottom[2] * t) * vig;
            }
        }
    }

    void draw(KnifeHD.Frame f, float ox, float oy) throws Exception {
        String path = KnifeHD.sheet(f.model, f.level);
        float[][] tex = sheet(path);
        int[] sz = SIZE.get(path);
        for (KnifeHD.Quad q : f.quads) {
            tri(q, 0, 1, 2, ox, oy, tex, sz);
            tri(q, 0, 2, 3, ox, oy, tex, sz);
        }
    }

    void tri(KnifeHD.Quad q, int a, int b, int c, float ox, float oy, float[][] tex, int[] sz) {
        float x0 = q.x[a] + ox, y0 = q.y[a] + oy, x1 = q.x[b] + ox, y1 = q.y[b] + oy, x2 = q.x[c] + ox, y2 = q.y[c] + oy;
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(area) < 1e-6f) return;
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(W - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(H - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        int tw = sz[0], th = sz[1];
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area;
                float w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area;
                float w2 = 1f - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float u = w0 * q.u[a] + w1 * q.u[b] + w2 * q.u[c];
                float v = w0 * q.v[a] + w1 * q.v[b] + w2 * q.v[c];
                float[] vc = new float[4];
                for (int k = 0; k < 4; k++) {
                    int sh = k == 3 ? 24 : 16 - 8 * k;
                    vc[k] = (w0 * ((q.c[a] >> sh) & 255) + w1 * ((q.c[b] >> sh) & 255) + w2 * ((q.c[c] >> sh) & 255)) / 255f;
                }
                // bilinear, clamp to edge
                float fx = u * tw - 0.5f, fy = v * th - 0.5f;
                int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
                float ax = fx - ix, ay = fy - iy;
                float[] s = new float[4];
                for (int ch = 0; ch < 4; ch++) {
                    float t00 = at(tex[ch], ix, iy, tw, th), t10 = at(tex[ch], ix + 1, iy, tw, th);
                    float t01 = at(tex[ch], ix, iy + 1, tw, th), t11 = at(tex[ch], ix + 1, iy + 1, tw, th);
                    s[ch] = (t00 * (1 - ax) + t10 * ax) * (1 - ay) + (t01 * (1 - ax) + t11 * ax) * ay;
                }
                float sr = s[0] * vc[0], sg = s[1] * vc[1], sb = s[2] * vc[2], sa = s[3] * vc[3];
                // 8 bit texture and vertex colour: the shader sees quantised alpha
                if (sa < 0.5f / 255f) continue;
                int i = y * W + x;
                if (q.light) {
                    R[i] = sr + R[i] * (1 - sa);
                    G[i] = sg + G[i] * (1 - sa);
                    B[i] = sb + B[i] * (1 - sa);
                } else {
                    R[i] = sr * sa + R[i] * (1 - sa);
                    G[i] = sg * sa + G[i] * (1 - sa);
                    B[i] = sb * sa + B[i] * (1 - sa);
                }
            }
        }
    }

    static float at(float[] ch, int x, int y, int w, int h) {
        x = Math.max(0, Math.min(w - 1, x));
        y = Math.max(0, Math.min(h - 1, y));
        return ch[y * w + x];
    }

    void save(String file) throws Exception {
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < W * H; i++) {
            int r = Math.min(255, Math.max(0, Math.round(R[i] * 255))), g = Math.min(255, Math.max(0, Math.round(G[i] * 255))), b = Math.min(255, Math.max(0, Math.round(B[i] * 255)));
            out.setRGB(i % W, i / W, (r << 16) | (g << 8) | b);
        }
        ImageIO.write(out, "png", new File(file));
    }

    // ------------------------------------------------------------------ scenes
    public static void main(String[] args) throws Exception {
        String scene = args.length > 0 ? args[0] : "lineup";
        if (scene.equals("lineup")) lineup(args.length > 1 ? args[1] : "knife_lineup.png", 1f);
        else if (scene.equals("spin")) spin(Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]));
        else if (scene.equals("poses")) poses(Integer.parseInt(args[1]), args[2]);
        else if (scene.equals("gallery")) gallery(args[1], Float.parseFloat(args[2]));
    }

    static Rig.T upright(float yaw, float pitch, float roll) {
        // model +y is the blade; turn it so the tip points up the screen
        return Rig.T.rz((float) Math.PI + roll).rotY(yaw).rotX(pitch);
    }

    static void lineup(String file, float scale) throws Exception {
        int n = KnifeHD.count();
        Preview p = new Preview(1408, 768, new float[] {0.33f, 0.34f, 0.36f}, new float[] {0.20f, 0.205f, 0.215f});
        KnifeHD.Frame f = new KnifeHD.Frame();
        float[] yaw = {0.30f, -0.25f, 0.20f, -0.15f, 0.25f, -0.20f, 0.15f, -0.30f};
        for (int i = 0; i < n; i++) {
            Rig.T k = upright((float) Math.PI + yaw[i % yaw.length], 0.05f, (i % 2 == 0 ? 0.03f : -0.03f)).move(0f, -16f, 0f);
            KnifeHD.build(f, KnifeHD.model(i), k, 16.5f * scale, 1f, 1f, true);
            p.draw(f, 115 + i * 168, 360);
        }
        p.save(file);
    }

    static void spin(int knife, String dir, int frames) throws Exception {
        new File(dir).mkdirs();
        KnifeHD.Frame f = new KnifeHD.Frame();
        for (int fr = 0; fr < frames; fr++) {
            float t = fr / (float) frames;
            Preview p = new Preview(960, 540, new float[] {0.30f, 0.31f, 0.34f}, new float[] {0.15f, 0.155f, 0.17f});
            float yaw = (float) (t * Math.PI * 2);
            Rig.T k = Rig.T.rz(-1.0f + 0.25f * (float) Math.sin(t * 6.283)).rotY(yaw).rotX(0.35f).move(0f, -14f, 0f);
            KnifeHD.build(f, KnifeHD.model(knife), k, 14f, 1f, 1f, true);
            p.draw(f, 480, 300);
            p.save(String.format("%s/f%03d.png", dir, fr));
        }
    }

    /** The same fit as KnifeHDGui.showcase, in tiles like the gallery's. */
    static void gallery(String file, float time) throws Exception {
        int tw = 150, th = 92, big = 1;
        Preview p = new Preview(4 * tw + 40, 2 * th + 30 + 260, new float[] {0.10f, 0.11f, 0.15f}, new float[] {0.07f, 0.075f, 0.10f});
        KnifeHD.Frame f = new KnifeHD.Frame();
        for (int i = 0; i < 9; i++) {
            int knife = i < 8 ? i : 3;
            int bx, by, bw, bh;
            if (i < 8) { bx = 10 + (i % 4) * (tw + 5); by = 10 + (i / 4) * (th + 5); bw = tw; bh = th; }
            else { bx = 20; by = 2 * th + 30; bw = 4 * tw; bh = 240; }
            float sway = (float) Math.sin(time * (i == 8 ? 0.9 : 0.7) + knife * 0.8);
            Rig.T k = Rig.T.rz((float) Math.PI - 1.10f).rotY(-0.35f + 0.32f * sway).rotX(0.12f + 0.06f * sway);
            KnifeHD.build(f, KnifeHD.model(knife), k, 1f, 1f, 1f, false);
            float x0 = 1e9f, y0 = 1e9f, x1 = -1e9f, y1 = -1e9f;
            for (KnifeHD.Quad q : f.quads) for (int c = 0; c < 4; c++) { x0 = Math.min(x0, q.x[c]); y0 = Math.min(y0, q.y[c]); x1 = Math.max(x1, q.x[c]); y1 = Math.max(y1, q.y[c]); }
            float ew = (x1 - x0) * 0.80f, eh = (y1 - y0) * 0.80f;
            float unit = Math.min(bw / ew, bh / eh) * 0.92f;
            // tile frame
            for (int y = by; y < by + bh; y++) for (int x = bx; x < bx + bw; x++) { int j = y * p.W + x; if (y == by || y == by + bh - 1 || x == bx || x == bx + bw - 1) { p.R[j] = 0.3f; p.G[j] = 0.35f; p.B[j] = 0.5f; } }
            KnifeHD.build(f, KnifeHD.model(knife), k, unit, 2f, 1f, true);
            p.draw(f, bx + bw / 2f - (x0 + x1) / 2f * unit, by + bh / 2f - (y0 + y1) / 2f * unit);
        }
        p.save(file);
    }

    static void poses(int knife, String file) throws Exception {
        Preview p = new Preview(1600, 560, new float[] {0.30f, 0.31f, 0.34f}, new float[] {0.15f, 0.155f, 0.17f});
        KnifeHD.Frame f = new KnifeHD.Frame();
        float[][] ps = {{0f, 0f, -0.9f}, {0.7f, 0.2f, -0.9f}, {1.35f, 0.25f, -0.8f}, {1.57f, 0f, -1.2f}, {2.6f, -0.3f, -1.0f}, {3.14f, 0.1f, -0.9f}};
        for (int i = 0; i < ps.length; i++) {
            Rig.T k = Rig.T.rz(ps[i][2]).rotY(ps[i][0]).rotX(ps[i][1]).move(0f, -14f, 0f);
            KnifeHD.build(f, KnifeHD.model(knife), k, 11f, 1f, 1f, true);
            p.draw(f, 140 + i * 265, 300);
        }
        p.save(file);
    }
}
