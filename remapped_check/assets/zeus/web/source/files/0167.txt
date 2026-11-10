package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The opening animation. A storm gathers, then three bolts of lightning come down out of the clouds one after another and
 * trace the strokes of a Z. Each bolt behaves like the real thing: a thin, dim stepped leader feels its way down from the
 * cloud (with forks hanging off it), the moment it reaches the ground the whole channel flares white (the return stroke),
 * flickers with two quick re-strikes and fades into a glow, while the sky and the ground light up and thunder follows.
 * After the last strike the three channels settle into a clean, electric Z, the name appears under it and the Z flies up
 * into the corner of the panel. Everything is drawn from the clock alone, so it can be shown or skipped at any moment.
 */
public final class Intro {
    private Intro() {}

    /** Times in seconds at normal speed. STRIKEn is the moment bolt n reaches the ground. */
    public static final float LEADER = 0.17f;
    public static final float STRIKE1 = 0.65f;
    public static final float STRIKE2 = 1.25f;
    public static final float STRIKE3 = 1.85f;
    public static final float FORMED = 2.40f;
    public static final float NAME = 2.65f;
    public static final float LEAVE = 3.45f;
    public static final float LENGTH = 4.10f;

    // the Z, in unit coordinates around the centre: A top left, B top right, C bottom left, D bottom right
    private static final float[][] Z = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {-0.5f, 0.5f}, {0.5f, 0.5f}};
    private static final float SLANT = 0.10f;
    private static final float[] LAND = {STRIKE1, STRIKE2, STRIKE3};

    private static float hash(int a, int b) {
        return Lightning.hash(a, b);
    }

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static float clamp01(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    private static float[] pt(float cx, float cy, float size, float[] u) {
        return new float[] {cx + (u[0] - u[1] * SLANT) * size, cy + u[1] * size};
    }

    private static float decay(float since, float len) {
        if (since < 0 || since > len) return 0f;
        float k = 1f - since / len;
        return k * k;
    }

    private static float tri(float x, float len) {
        if (x < 0f || x > len) return 0f;
        return 1f - x / len;
    }

    /** How bright a bolt's channel is {@code s} seconds after it reached the ground: a flash, two re-strikes, then a glow. */
    static float returnStroke(float s) {
        if (s < 0f) return 0f;
        float v = s < 0.07f ? 1f : 0f;
        v = Math.max(v, tri(s - 0.10f, 0.05f) * 0.85f);
        v = Math.max(v, tri(s - 0.19f, 0.07f) * 0.65f);
        float glow = Math.max(0f, 1f - s / 1.0f);
        return Math.max(v, glow * glow * 0.32f);
    }

    /** The white flash over the screen. */
    public static float flash(float t) {
        float f = 0f;
        for (int i = 0; i < 3; i++) {
            float s = t - LAND[i];
            f = Math.max(f, (s < 0.07f && s >= 0f ? 1f : 0f) * 0.50f);
            f = Math.max(f, tri(s - 0.10f, 0.05f) * 0.32f);
            f = Math.max(f, decay(s, 0.30f) * 0.22f);
        }
        // far-off sheet lightning inside the clouds before the first strike
        f = Math.max(f, tri(t - 0.12f, 0.10f) * 0.10f);
        f = Math.max(f, tri(t - 0.33f, 0.08f) * 0.14f);
        f = Math.max(f, decay(t - FORMED, 0.45f) * 0.85f);
        f = Math.max(f, decay(t - LEAVE, 0.30f) * 0.35f);
        return f;
    }

    public static float shake(float t) {
        float s = 0f;
        for (float land : LAND) s = Math.max(s, decay(t - land, 0.22f) * 0.8f);
        s = Math.max(s, decay(t - FORMED, 0.35f) * 1.5f);
        return s;
    }

    /** The dark storm backdrop: clouds that light up from inside and falling rain. */
    public static void backdrop(GuiGraphics g, int w, int h, float t, int accent, float fade) {
        if (fade <= 0.02f) return;
        float charge = clamp01(t / FORMED);
        float lit = Math.min(1f, flash(t) * 1.5f);
        g.fillGradient(0, 0, w, h, Draw.rgba(0xFF020308, 0.97f * fade), Draw.rgba(0xFF05070F, 0.97f * fade));
        // the cloud deck: lit from inside by every flash
        int cloud = Draw.mix(0xFF1B2440, 0xFF9FB6FF, lit);
        g.fillGradient(0, 0, w, Math.round(h * 0.42f), Draw.rgba(cloud, (0.35f + 0.5f * lit) * fade), 0x00000000);
        g.fillGradient(0, 0, w, Math.round(h * 0.16f), Draw.rgba(Draw.mix(accent, 0xFFFFFFFF, 0.3f), (0.10f + 0.25f * lit) * fade), 0x00000000);
        // the glow of the charging Z in the middle
        Lightning.bloom(g, w / 2f, h * 0.46f, Math.min(w, h) * 0.45f, accent, (0.25f + 0.75f * charge) * fade * (0.6f + 0.4f * lit));
        // rain: slanted streaks, in two layers for depth
        for (int i = 0; i < 44; i++) {
            float layer = i < 22 ? 1f : 0.55f;
            float x = hash(i, 11) * w * 1.2f - w * 0.1f;
            float sp = (0.7f + hash(i, 12)) * layer;
            float y = ((hash(i, 13) * h * 2f + t * 520f * sp) % (h * 1.3f)) - h * 0.15f;
            int len = Math.round((6 + hash(i, 14) * 9) * layer);
            float a = (0.07f + 0.09f * hash(i, 15)) * layer * (1f + 2.2f * lit) * fade;
            Draw.line(g, x, y, x - len * 0.25f, y + len, 1f, Draw.rgba(0xFFBFD8FF, Math.min(0.5f, a)));
        }
        // a dark vignette keeps the eye on the middle
        g.fillGradient(0, Math.round(h * 0.78f), w, h, 0x00000000, Draw.rgba(0xFF000000, 0.55f * fade));
    }

    private static void ring(GuiGraphics g, float cx, float cy, float r, float thick, int color) {
        int n = Math.max(28, Math.min(64, (int) (r / 3)));
        float px = cx + r;
        float py = cy;
        for (int i = 1; i <= n; i++) {
            float a = i / (float) n * 6.2832f;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r * 0.62f;
            Draw.line(g, px, py, x, y, thick, color);
            px = x;
            py = y;
        }
    }

    /**
     * The Z, the bolts and the name. {@code leave} moves the finished Z to a small size at (tx, ty); with 0 it is
     * centred and large.
     */
    public static void front(GuiGraphics g, int w, int h, float t, int accent, int second, float leave, float tx, float ty, float tsize) {
        float big = Math.min(w, h) * 0.36f;
        float cx0 = w / 2f;
        float cy0 = h / 2f - big * 0.12f;
        float e = ease(leave);
        float size = big + (tsize - big) * e;
        float cx = cx0 + (tx - cx0) * e;
        float cy = cy0 + (ty - cy0) * e;
        float[][] p = new float[4][];
        for (int i = 0; i < 4; i++) p[i] = pt(cx, cy, size, Z[i]);

        int frame = (int) (t * 30f);
        float[][] from = {p[0], p[1], p[2]};
        float[][] to = {p[1], p[2], p[3]};
        float[] dur = {0.34f, 0.46f, 0.34f};
        float wUnit = Math.max(0.6f, size / 42f);

        if (leave < 1f && t < FORMED + 0.2f) {
            for (int s = 0; s < 3; s++) {
                float since = t - LAND[s];
                float untilLand = -since;
                if (untilLand > LEADER + 0.02f) continue;
                float sx = from[s][0] + (hash(s, 3) - 0.5f) * size * 0.9f;
                int seed = s * 53 + 11;
                float stroke = returnStroke(since);
                if (untilLand > 0f) {
                    // the stepped leader: a thin dim channel growing down from the clouds, with a bright tip
                    float pr = clamp01(1f - untilLand / LEADER);
                    Lightning.bolt(g, sx, -8, from[s][0], from[s][1], pr, seed, 0.55f, accent, second, wUnit * 0.55f, 2, 1f, 0.35f, 3);
                    float headY = -8 + (from[s][1] + 8) * pr;
                    float headX = sx + (from[s][0] - sx) * pr;
                    Lightning.bloom(g, headX, headY, size * 0.18f, 0xFFFFFFFF, 0.75f);
                } else {
                    // the return stroke: the whole channel flares, thicker and whiter when it is brightest
                    float widthScale = 0.9f + 1.0f * stroke;
                    float flick = 0.45f + 0.55f * stroke;
                    // a flicker in the fine shape, but the main channel keeps its place
                    Lightning.bolt(g, sx, -8, from[s][0], from[s][1], 1f, seed, Math.min(1f, flick), accent, second, wUnit * 1.2f * widthScale, 2, 1f, 0.35f, 4);
                    if (since < 0.9f) {
                        float hit = decay(since, 0.9f);
                        Lightning.bloom(g, from[s][0], from[s][1], size * (0.35f + 0.5f * stroke), 0xFFFFFFFF, 0.95f * hit);
                        Lightning.streak(g, from[s][0], from[s][1], size * (0.5f + 0.9f * stroke), accent, hit * stroke);
                        Lightning.sparks(g, from[s][0], from[s][1], 12, size * 0.8f, since, s * 7 + 1, accent, hit);
                    }
                }
                // the stroke it lights, running along the Z like a crawler
                float cs = since - 0.02f;
                if (cs > 0f) {
                    float prog = clamp01(cs / dur[s]);
                    float settle = clamp01((cs - dur[s]) / 0.60f);
                    float wander = 1f - 0.87f * ease(settle);
                    int cseed = s * 97 + (settle < 1f ? frame : frame / 5);
                    float alive = 0.70f + 0.30f * stroke;
                    Lightning.bolt(g, from[s][0], from[s][1], to[s][0], to[s][1], prog, cseed, alive, accent, second, wUnit * 1.15f, settle < 0.7f ? 1 : 0, wander, 0f, 3);
                    if (prog < 1f && prog > 0f) {
                        float hx = from[s][0] + (to[s][0] - from[s][0]) * prog;
                        float hy = from[s][1] + (to[s][1] - from[s][1]) * prog;
                        Lightning.bloom(g, hx, hy, size * 0.28f, 0xFFFFFFFF, 0.85f);
                    }
                }
            }
        }

        // after the Z has formed it is the living, packed Z (the same one that sits in the corner of the panel)
        if (t > FORMED - 0.05f) {
            float live = clamp01((t - FORMED + 0.05f) / 0.3f);
            // your own Z picture fades in over the strokes, with its glow, shine and arcs
            float pic = ease(clamp01((t - FORMED - 0.05f) / 0.55f));
            Logo.packed(g, cx, cy, size, t, accent, second, live * (1f - 0.8f * pic));
            if (pic > 0f && !Logo.live("intro", g, cx, cy, size * 1.2f, t, accent, second, pic * live)) Logo.packed(g, cx, cy, size, t, accent, second, live);
            if (leave == 0f) {
                float since = t - FORMED;
                if (since < 1.0f) {
                    float k = since / 1.0f;
                    ring(g, cx, cy, size * (0.2f + 1.5f * ease(k)), Math.max(1f, wUnit * 1.3f * (1f - k)), Draw.rgba(Draw.mix(accent, 0xFFFFFFFF, 0.5f), 0.7f * (1f - k)));
                    ring(g, cx, cy, size * (0.1f + 1.1f * ease(k)), 1f, Draw.rgba(0xFFFFFFFF, 0.5f * (1f - k)));
                    Lightning.streak(g, cx, cy, size * (0.8f + 1.4f * k), accent, (1f - k) * (1f - k));
                    // bolts that jump out from the finished Z in every direction
                    if (since < 0.35f) {
                        for (int i = 0; i < 4; i++) {
                            float ang = (i + hash(frame / 2, i)) * 1.5708f + 0.4f;
                            float len = size * (0.55f + 0.5f * hash(frame / 2, i + 9));
                            float ox = p[i][0];
                            float oy = p[i][1];
                            Lightning.bolt(g, ox, oy, ox + (float) Math.cos(ang) * len, oy + (float) Math.sin(ang) * len, 1f, frame / 2 * 5 + i, 1f - since / 0.35f, accent, second, wUnit * 0.8f, 1, 1f, 0.1f, 3);
                        }
                    }
                }
            }
        }

        // the name
        if (leave < 0.6f && t > NAME) {
            float fade = (1f - clamp01(leave / 0.6f));
            String word = "ZEUS";
            float sc = Math.max(2f, Math.round(size / 45f));
            int total = 0;
            int gap = Math.round(5 * sc);
            int[] ws = new int[word.length()];
            for (int i = 0; i < word.length(); i++) {
                ws[i] = Math.round(Draw.widthBold(String.valueOf(word.charAt(i))) * sc);
                total += ws[i] + (i < word.length() - 1 ? gap : 0);
            }
            float x = cx - total / 2f;
            float y = cy + size * 0.78f;
            for (int i = 0; i < word.length(); i++) {
                float a = clamp01((t - NAME - i * 0.07f) / 0.25f) * fade;
                if (a <= 0f) {
                    x += ws[i] + gap;
                    continue;
                }
                int col = Draw.mix(accent, second, i / 3f);
                col = Draw.mix(col, 0xFFFFFFFF, 0.55f);
                // each letter lights up with a little flash of white
                float flash = clamp01(1f - (t - NAME - i * 0.07f - 0.1f) / 0.3f);
                col = Draw.mix(col, 0xFFFFFFFF, flash * 0.8f);
                g.pose().pushMatrix();
                g.pose().translate(x, y + (1f - a) * 6f);
                g.pose().scale(sc, sc);
                Draw.bold(g, String.valueOf(word.charAt(i)), 0, 0, Draw.rgba(col, a));
                g.pose().popMatrix();
                x += ws[i] + gap;
            }
            float ta = clamp01((t - NAME - 0.4f) / 0.4f) * fade;
            if (ta > 0f) {
                String tag = "T H U N D E R   C L I E N T";
                Draw.textCenter(g, tag, Math.round(cx), Math.round(y + 9 * sc + 6), Draw.rgba(0xFF8FA3C8, ta));
                int half = Math.round(total * 0.6f * ease((t - NAME) / 0.8f));
                g.fill(Math.round(cx) - half, Math.round(y + 9 * sc + 2), Math.round(cx) + half, Math.round(y + 9 * sc + 3), Draw.rgba(accent, 0.6f * ta));
            }
        }
    }
}
