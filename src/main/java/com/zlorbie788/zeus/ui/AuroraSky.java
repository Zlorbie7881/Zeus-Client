package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class AuroraSky {

    public interface View {

        boolean at(float sx, float sy, float[] azel);

        default boolean hasSkyline() {
            return false;
        }

        default float skyline(float az) {
            return -90f;
        }

        default float mask(float sx, float sy) {
            return 1f;
        }
    }

    public static final class Style {
        public int curtains = 4;
        public int scheme;
        public float height = 16f;
        public float bearing;
        public float spread = 62f;
        public float activity = 0.75f;
        public boolean surges = true;
        public int seed = 7;

        int signature() {
            int h = curtains * 31 + scheme;
            h = h * 31 + Float.floatToIntBits(height);
            h = h * 31 + Float.floatToIntBits(bearing);
            h = h * 31 + Float.floatToIntBits(spread);
            return h * 31 + seed;
        }
    }

    public static final String[] SCHEMES = {"Green and violet", "Classic green", "Polar pink", "Solar storm", "Ice", "Night sky"};

    private static final float[] STOPS = {0f, 0.20f, 0.45f, 0.72f, 1f};
    private static final float[][][] PALETTE = {
            {{0.98f, 0.38f, 0.72f}, {0.22f, 1.00f, 0.56f}, {0.16f, 0.96f, 0.62f}, {0.30f, 0.74f, 0.96f}, {0.64f, 0.32f, 1.00f}},
            {{0.70f, 1.00f, 0.55f}, {0.26f, 1.00f, 0.46f}, {0.20f, 0.95f, 0.38f}, {0.16f, 0.78f, 0.42f}, {0.50f, 0.26f, 0.70f}},
            {{1.00f, 0.36f, 0.62f}, {0.32f, 1.00f, 0.62f}, {0.22f, 0.88f, 0.74f}, {0.78f, 0.42f, 0.96f}, {1.00f, 0.32f, 0.70f}},
            {{1.00f, 0.30f, 0.42f}, {0.62f, 1.00f, 0.40f}, {0.92f, 0.56f, 0.30f}, {1.00f, 0.24f, 0.32f}, {0.72f, 0.12f, 0.38f}},
            {{0.66f, 0.92f, 1.00f}, {0.30f, 0.96f, 0.96f}, {0.26f, 0.78f, 1.00f}, {0.36f, 0.52f, 1.00f}, {0.62f, 0.42f, 1.00f}},
            {{0.62f, 1.00f, 0.50f}, {0.24f, 1.00f, 0.52f}, {0.14f, 0.92f, 0.76f}, {0.24f, 0.56f, 1.00f}, {0.66f, 0.30f, 0.96f}},
    };

    private static final int AZN = 720;
    private static final int MAX = 6;

    private int sig = Integer.MIN_VALUE;
    private int count;
    private final float[] az0 = new float[MAX];
    private final float[] baseEl = new float[MAX];
    private final float[] halfLen = new float[MAX];
    private final float[] thick0 = new float[MAX];
    private final float[] ph1 = new float[MAX];
    private final float[] ph2 = new float[MAX];
    private final float[] ph3 = new float[MAX];
    private final float[] tilt = new float[MAX];
    private final float[] weight = new float[MAX];
    private int scheme;
    private int seed = 7;

    private final float[][] tBase = new float[MAX][AZN];
    private final float[][] tThick = new float[MAX][AZN];
    private final float[][] tBright = new float[MAX][AZN];
    private final float[][] tRay = new float[MAX][AZN];
    private final float[][] tEdge = new float[MAX][AZN];
    private final int[][] stamp = new int[MAX][AZN];
    private int frameId = 1;

    private float time;
    private float act = 0.75f;
    private float breakup;
    private boolean surges = true;
    private float baseAct = 0.75f;

    private final float[] out = new float[4];
    private final float[] azel = new float[2];
    private float[] ys = new float[256];
    private float[] cr = new float[256];
    private float[] cg = new float[256];
    private float[] cb = new float[256];
    private float[] ca = new float[256];

    public AuroraSky() {
        for (int i = 0; i < MAX; i++) java.util.Arrays.fill(stamp[i], 0);
    }

    public float surge() {
        return breakup;
    }

    public void begin(float t, Style s) {
        if (s.signature() != sig) configure(s);
        time = t;
        surges = s.surges;
        baseAct = s.activity;
        frameId++;
        if (frameId == Integer.MAX_VALUE) {
            frameId = 1;
            for (int i = 0; i < MAX; i++) java.util.Arrays.fill(stamp[i], 0);
        }
        breakup = surges ? surgeAt(t) : 0f;
        float slow = 0.5f + 0.5f * (float) Math.sin(t * 0.045f + 1.3f);
        act = Math.max(0.12f, baseAct * (0.78f + 0.22f * slow) + 0.55f * breakup);
    }

    private static float surgeAt(float t) {
        final float period = 118f;
        int e = (int) Math.floor(t / period);
        float best = 0f;
        for (int k = e - 1; k <= e; k++) {
            float onset = k * period + period * (0.30f + 0.45f * Lightning.hash(k, 4242));
            float d = t - onset;
            if (d < 0f) continue;
            float v = d < 5f ? smooth01(d / 5f) : (float) Math.exp(-(d - 5f) / 24f);
            best = Math.max(best, v * (0.65f + 0.35f * Lightning.hash(k, 977)));
        }
        return best;
    }

    private void configure(Style s) {
        sig = s.signature();
        count = Math.max(1, Math.min(MAX, s.curtains));
        scheme = Math.max(0, Math.min(PALETTE.length - 1, s.scheme));
        seed = s.seed;
        for (int i = 0; i < count; i++) {
            float h1 = Lightning.hash(seed, i * 13 + 1);
            float h2 = Lightning.hash(seed, i * 13 + 2);
            float h3 = Lightning.hash(seed, i * 13 + 3);
            float h4 = Lightning.hash(seed, i * 13 + 4);
            float side = (i % 2 == 0 ? -1f : 1f) * (i == 0 ? 0f : 1f);
            az0[i] = s.bearing + side * s.spread * (0.12f + 0.22f * h1) + (h2 - 0.5f) * s.spread * 0.18f;
            baseEl[i] = s.height + i * (4.6f + 3.2f * h3) + (h4 - 0.5f) * 3f;
            halfLen[i] = Math.min(178f, s.spread * (0.80f + 0.50f * h2));
            thick0[i] = 11f + 13f * h1 + (i == 0 ? 3f : 0f);
            ph1[i] = h1 * 6.2832f;
            ph2[i] = h2 * 6.2832f;
            ph3[i] = h3 * 6.2832f;
            tilt[i] = (h4 - 0.5f) * 2f;
            weight[i] = (float) Math.pow(0.82, i) * (0.85f + 0.3f * h3);
            java.util.Arrays.fill(stamp[i], 0);
        }
    }

    private static float smooth01(float x) {
        x = x < 0f ? 0f : x > 1f ? 1f : x;
        return x * x * (3f - 2f * x);
    }

    private static float clamp01(float x) {
        return x < 0f ? 0f : x > 1f ? 1f : x;
    }

    private static float vn(float x, int sd) {
        int i = (int) Math.floor(x);
        float f = x - i;
        float u = f * f * (3f - 2f * f);
        float a = Lightning.hash(i, sd);
        float b = Lightning.hash(i + 1, sd);
        return a + (b - a) * u;
    }

    private static float wrap180(float d) {
        d %= 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private void fill(int c, int bin) {
        stamp[c][bin] = frameId;
        float az = bin * 0.5f + 0.25f;
        float d = wrap180(az - az0[c]);
        float len = halfLen[c];
        float as = Math.abs(d / len);
        if (as >= 1f) {
            tBright[c][bin] = 0f;
            tBase[c][bin] = baseEl[c];
            tThick[c][bin] = thick0[c];
            tRay[c][bin] = 0.5f;
            tEdge[c][bin] = 1f;
            return;
        }
        float t = time;
        float env = smooth01((1f - as) / 0.42f);
        float k = 0.054f;
        float a1 = d * k + ph1[c] + t * 0.021f;
        float a2 = d * k * 2.31f + ph2[c] - t * 0.037f;
        float a3 = d * k * 5.2f + ph3[c] + t * 0.061f;
        float amp = 3.4f + 2.4f * clamp01(act - 0.4f);
        float wob = 0.5f * (float) Math.sin(a1) + 0.3f * (float) Math.sin(a2) + 0.2f * (float) Math.sin(a3);
        float slope = amp * k * (0.5f * (float) Math.cos(a1) + 0.3f * 2.31f * (float) Math.cos(a2) + 0.2f * 5.2f * (float) Math.cos(a3));
        float base = baseEl[c] + tilt[c] * (d / len) * 5f + amp * wob;
        float edge = 1f + Math.min(2.2f, 3.0f * Math.abs(slope));
        float patch = 0.20f + 0.80f * smooth01(vn(d * 0.065f + t * 0.05f + ph1[c] * 3f, seed + c * 7) * 1.6f - 0.2f);
        float flame = 0.80f + 0.20f * (float) Math.sin(t * (1.0f + 0.9f * Lightning.hash(seed, c + 70)) * (1f + 0.8f * breakup) + d * 0.11f + ph2[c]);
        int sd = seed * 3 + c * 11;
        float rn = 0.50f * vn(d * 0.55f + t * 0.16f + ph3[c] * 5f, sd + 1)
                + 0.28f * vn(d * 1.3f - t * 0.28f, sd + 2)
                + 0.14f * vn(d * 2.7f + t * 0.46f, sd + 3)
                + 0.08f * vn(d * 5.5f - t * 0.75f, sd + 4);
        float ray = smooth01((rn - 0.22f) / 0.52f);
        float th = thick0[c] * (0.75f + 0.45f * vn(d * 0.05f - t * 0.03f, sd + 5)) * (0.88f + 0.30f * clamp01(act));
        tBase[c][bin] = base;
        tThick[c][bin] = th;
        tRay[c][bin] = 0.12f + 0.88f * ray;
        tEdge[c][bin] = edge;
        tBright[c][bin] = env * patch * flame * weight[c] * act;
    }

    private float eval(float az, float el, float gain) {
        float bf = az * 2f;
        bf %= AZN;
        if (bf < 0f) bf += AZN;
        int b0 = (int) bf;
        if (b0 >= AZN) b0 = AZN - 1;
        float fr = bf - b0;
        int b1 = b0 + 1 == AZN ? 0 : b0 + 1;
        float r = 0f;
        float g = 0f;
        float b = 0f;
        float lum = 0f;
        for (int c = 0; c < count; c++) {
            if (stamp[c][b0] != frameId) fill(c, b0);
            if (stamp[c][b1] != frameId) fill(c, b1);
            float br0 = tBright[c][b0];
            float br1 = tBright[c][b1];
            float br = br0 + (br1 - br0) * fr;
            if (br <= 0.004f) continue;
            float bs0 = tBase[c][b0];
            float base = bs0 + (tBase[c][b1] - bs0) * fr;
            float u = el - base;
            if (u < -1.6f) continue;
            float th = tThick[c][b0] + (tThick[c][b1] - tThick[c][b0]) * fr;
            if (u > th * 3.4f) continue;
            float ray = tRay[c][b0] + (tRay[c][b1] - tRay[c][b0]) * fr;
            float edge = tEdge[c][b0] + (tEdge[c][b1] - tEdge[c][b0]) * fr;
            float hh = th * (0.55f + 0.95f * ray);
            float up = u > 0f ? u : 0f;
            float lower = smooth01((u + 0.5f) / 1.7f);
            float vert = 0.82f * (float) Math.exp(-up / (hh * 0.36f)) + 0.18f * (float) Math.exp(-up / (hh * 1.15f));
            float rf = ray + (0.60f - ray) * Math.min(1f, up / (hh * 0.9f));
            float i = br * edge * lower * vert * (0.22f + 0.78f * rf * 1.25f);
            if (i <= 0.002f) continue;
            float v = (float) Math.pow(clamp01(u / (th * 0.85f)), 0.75);
            palette(v, i);
            r += out[0] * i;
            g += out[1] * i;
            b += out[2] * i;
            lum += i;
        }
        if (breakup > 0.04f && el > 50f) {
            float kz = smooth01((el - 50f) / 24f);
            float rr = 0.6f * vn(az * 0.22f + time * 0.15f, seed + 9) + 0.4f * vn(az * 0.9f - time * 0.4f, seed + 10);
            float i = breakup * kz * (0.22f + 0.78f * rr) * 0.85f;
            palette(0.4f, i);
            r += out[0] * i;
            g += out[1] * i;
            b += out[2] * i;
            lum += i;
        }
        if (lum <= 0.003f) return 0f;
        r /= lum;
        g /= lum;
        b /= lum;
        float e = lum * gain;
        float a = 1f - (float) Math.exp(-1.35f * e);
        float w = smooth01((e - 1.1f) / 2.2f) * 0.25f;
        out[0] = r + (1f - r) * w;
        out[1] = g + (1f - g) * w;
        out[2] = b + (1f - b) * w;
        return Math.min(a, 0.93f);
    }

    private void palette(float v, float strength) {
        float[][] p = PALETTE[scheme];
        int k = 0;
        while (k < 3 && v > STOPS[k + 1]) k++;
        float f = clamp01((v - STOPS[k]) / (STOPS[k + 1] - STOPS[k]));
        f = f * f * (3f - 2f * f);
        float r = p[k][0] + (p[k + 1][0] - p[k][0]) * f;
        float g = p[k][1] + (p[k + 1][1] - p[k][1]) * f;
        float b = p[k][2] + (p[k + 1][2] - p[k][2]) * f;
        if (breakup > 0.05f && (scheme == 0 || scheme == 1) && v > 0.55f) {
            float m = breakup * 0.6f * clamp01((v - 0.55f) / 0.45f);
            r += (1f - r) * m;
            g += (0.22f - g) * m;
            b += (0.36f - b) * m;
        }
        out[0] = r;
        out[1] = g;
        out[2] = b;
    }

    private void room(int n) {
        if (n <= ys.length) return;
        int m = Math.max(n, ys.length * 2);
        ys = java.util.Arrays.copyOf(ys, m);
        cr = java.util.Arrays.copyOf(cr, m);
        cg = java.util.Arrays.copyOf(cg, m);
        cb = java.util.Arrays.copyOf(cb, m);
        ca = java.util.Arrays.copyOf(ca, m);
    }

    public void render(GuiGraphics g, int x0, int y0, int x1, int y1, View view, float gain, int colStep, int rowStep) {
        if (gain <= 0.01f || x1 <= x0 || y1 <= y0) return;
        colStep = Math.max(2, colStep);
        rowStep = Math.max(3, rowStep);
        room((y1 - y0) / rowStep + 4);
        boolean land = view.hasSkyline();
        for (int x = x0; x < x1; x += colStep) {
            int xe = Math.min(x1, x + colStep);
            float sx = (x + xe) * 0.5f;
            float yEnd = y1;
            if (land) {
                float bottom = skyGap(view, sx, y1);
                if (bottom < 0f) {
                    float top = skyGap(view, sx, y0);
                    if (top < 0f) continue;
                    float lo = y0;
                    float hi = y1;
                    for (int it = 0; it < 8; it++) {
                        float mid = (lo + hi) * 0.5f;
                        if (skyGap(view, sx, mid) >= 0f) lo = mid;
                        else hi = mid;
                    }
                    yEnd = lo;
                }
            }
            int n = 0;
            boolean any = false;
            for (float y = y0; ; y += rowStep) {
                boolean last = y >= yEnd;
                float yy = last ? yEnd : y;
                float a = 0f;
                if (view.at(sx, yy, azel) && azel[1] > -2f) {
                    float m = view.mask(sx, yy);
                    if (m > 0.004f) a = eval(azel[0], azel[1], gain) * m;
                }
                ys[n] = yy;
                ca[n] = a;
                if (a > 0.004f) {
                    cr[n] = out[0];
                    cg[n] = out[1];
                    cb[n] = out[2];
                    any = true;
                }
                n++;
                if (last || n >= ys.length - 1) break;
            }
            if (!any) continue;
            for (int k = 0; k + 1 < n; k++) {
                float a0 = ca[k];
                float a1 = ca[k + 1];
                if (a0 <= 0.004f && a1 <= 0.004f) continue;
                int yt = Math.round(ys[k]);
                int yb = Math.round(ys[k + 1]);
                if (yb <= yt) continue;
                int top = a0 > 0.004f ? pack(cr[k], cg[k], cb[k], a0) : pack(cr[k + 1], cg[k + 1], cb[k + 1], 0f);
                int bot = a1 > 0.004f ? pack(cr[k + 1], cg[k + 1], cb[k + 1], a1) : pack(cr[k], cg[k], cb[k], 0f);
                g.fillGradient(x, yt, xe, yb, top, bot);
            }
        }
    }

    private float skyGap(View view, float sx, float sy) {
        if (!view.at(sx, sy, azel)) return -1f;
        return azel[1] - view.skyline(azel[0]);
    }

    private static int pack(float r, float g, float b, float a) {
        int ia = Math.round(clamp01(a) * 255f);
        int ir = Math.round(clamp01(r) * 255f);
        int ig = Math.round(clamp01(g) * 255f);
        int ib = Math.round(clamp01(b) * 255f);
        return ia << 24 | ir << 16 | ig << 8 | ib;
    }
}
