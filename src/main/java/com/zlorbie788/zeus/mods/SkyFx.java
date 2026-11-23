package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.Projector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A painterly sky wash: a multi-stop gradient, a sun and moon with bloom, a lens flare, a nebula
 * built from domain-warped noise and a coloured star field. It sits above the projected horizon
 * and under the rest of the sky, so it works with or without the Atmosphere module.
 *
 * <p>The whole sky is drawn as layers of soft quads, the way the rest of Zeus's sky is drawn. A
 * real GLSL pass would have to replace vanilla's sky renderer, which is not something this module
 * pretends to do; what it changes is the colour and light of the sky, not the geometry shader.
 */
public final class SkyFx extends Module {
    private final Setting.Num strength = add(new Setting.Num("Strength", "How strongly the sky is tinted and lit.", 60, 10, 100, 5, "%"));
    private final Setting.Bool sunMoon = add(new Setting.Bool("Sun and moon", "Draw the sun and the moon with a soft bloom.", true));
    private final Setting.Bool flare = add(new Setting.Bool("Lens flare", "A few soft ghosts along the line from the sun through the middle of the screen.", true));
    private final Setting.Bool nebula = add(new Setting.Bool("Nebula", "A drifting cloud of colour in the night sky.", true));
    private final Setting.Bool stars = add(new Setting.Bool("Coloured stars", "A star field with warm and cold stars that twinkle.", true));
    private final Setting.Choice detail = add(new Setting.Choice("Detail", "How many layers are drawn. Lower is faster.", 1, "Low", "Medium", "High"));
    private final Setting.Bool clearOnly = add(new Setting.Bool("Hide in rain", "Fade the sky out while it rains or storms.", true));

    private static final int[] BANDS = {16, 26, 38};
    private static final int[] NEB_COLS = {12, 18, 26};
    private static final int[] NEB_ROWS = {7, 10, 14};
    private static final int[] STAR_COUNT = {70, 150, 260};

    private float fade;
    private float clock;
    private long last;

    public SkyFx() {
        super("skyfx", "Sky FX", "A shader-style sky: gradient, sun and moon bloom, lens flare, nebula and coloured stars.", Category.RENDER);
        alias("sky shader", "shader sky", "sky gradient", "sky colour");
    }

    @Override
    protected void onDisable() {
        fade = 0f;
        last = 0L;
    }

    private static int rgba(float r, float g, float b, float a) {
        return (Math.round(Mth.clamp(a, 0f, 1f) * 255f) << 24)
                | (Math.round(Mth.clamp(r, 0f, 1f) * 255f) << 16)
                | (Math.round(Mth.clamp(g, 0f, 1f) * 255f) << 8)
                | Math.round(Mth.clamp(b, 0f, 1f) * 255f);
    }

    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static float noise(float x, float y) {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        float a = hash(ix, iy);
        float b = hash(ix + 1, iy);
        float c = hash(ix, iy + 1);
        float d = hash(ix + 1, iy + 1);
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static float fbm(float x, float y, int octaves) {
        float sum = 0f;
        float amp = 0.5f;
        float norm = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x, y);
            norm += amp;
            x = x * 2.03f + 7.3f;
            y = y * 2.03f + 2.9f;
            amp *= 0.5f;
        }
        return norm <= 0f ? 0f : sum / norm;
    }

    @Override
    protected void onHudWorld(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        long now = System.nanoTime();
        float dt = last == 0L ? 0f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) return;
        if (!Projector.prepare(mc, partial, w, h)) return;

        float rain = 0f;
        try {
            rain = Math.max(mc.level.getRainLevel(partial), mc.level.getThunderLevel(partial));
        } catch (RuntimeException | LinkageError ignored) {
        }
        float target = clearOnly.on() ? 1f - Math.min(1f, rain) : 1f;
        if (target < 0.01f) {
            fade = Math.max(0f, fade - dt);
        } else {
            fade += (target - fade) * (1f - (float) Math.exp(-dt * 1.6f));
        }
        if (fade <= 0.01f) return;

        clock += dt;
        int q = detail.index();
        if (com.zlorbie788.zeus.ui.Perf.level() >= 2) q = 0;
        else if (com.zlorbie788.zeus.ui.Perf.level() == 1) q = Math.max(0, q - 1);

        float dayTime = (mc.level.getDayTime() % 24000L) / 24000f;
        float angle = dayTime * (float) (Math.PI * 2.0);
        float elevation = Mth.sin(angle);
        float day = Mth.clamp((elevation + 0.18f) / 0.36f, 0f, 1f);
        float dusk = 1f - Math.min(1f, Math.abs(elevation) / 0.30f);

        // the horizon is where a level ray from the eye lands on the screen
        double yaw = Math.toRadians(mc.player.getViewYRot(partial));
        double ex = Projector.eyeX();
        double ey = Projector.eyeY();
        double ez = Projector.eyeZ();
        float[] horizon = new float[2];
        if (!Projector.project(ex - Math.sin(yaw) * 64.0, ey, ez + Math.cos(yaw) * 64.0, horizon)) return;
        float hy = Math.min(h, horizon[1]);
        if (hy <= 0f) return;

        float alpha = fade * strength.f() / 100f;

        // ---- gradient: warm near the horizon, deep at the top ----
        float zr = Mth.lerp(day, 0.024f, 0.227f);
        float zg = Mth.lerp(day, 0.039f, 0.451f);
        float zb = Mth.lerp(day, 0.110f, 0.831f);
        float hr = Mth.lerp(day, 0.110f, 0.686f);
        float hg = Mth.lerp(day, 0.141f, 0.812f);
        float hb = Mth.lerp(day, 0.290f, 1.000f);
        hr = Mth.lerp(dusk * 0.65f, hr, 1.0f);
        hg = Mth.lerp(dusk * 0.65f, hg, 0.545f);
        hb = Mth.lerp(dusk * 0.65f, hb, 0.376f);

        int bands = BANDS[q];
        float span = Math.max(1f, hy);
        for (int i = 0; i < bands; i++) {
            float t0 = i / (float) bands;
            float t1 = (i + 1) / (float) bands;
            float y0 = hy - span * (1f - t0);
            float y1 = hy - span * (1f - t1);
            float k = (t0 + t1) * 0.5f;
            int col = rgba(Mth.lerp(k, hr, zr), Mth.lerp(k, hg, zg), Mth.lerp(k, hb, zb), alpha * (0.55f + 0.45f * (1f - k)));
            g.fill(0, Math.round(y0), w, Math.round(y1) + 1, col);
        }

        // ---- nebula, only worth drawing at night ----
        if (nebula.on() && day < 0.65f) {
            int cols = NEB_COLS[q];
            int rows = NEB_ROWS[q];
            float cw = w / (float) cols;
            float ch = span / (float) rows;
            for (int ry = 0; ry < rows; ry++) {
                for (int rx = 0; rx < cols; rx++) {
                    float u = rx / (float) cols;
                    float v = ry / (float) rows;
                    float px = u * 4.2f + clock * 0.012f;
                    float py = v * 2.4f + clock * 0.006f;
                    float warp = fbm(px * 1.9f, py * 1.9f, 2) * 0.9f;
                    float f = fbm(px + warp, py - warp * 0.7f, q == 0 ? 2 : 3);
                    float a = Mth.clamp((f - 0.52f) / 0.34f, 0f, 1f);
                    if (a <= 0.02f) continue;
                    a *= (1f - day) * alpha * 0.55f;
                    float warm = fbm(px * 0.7f + 9.1f, py * 0.7f, 2);
                    float cr = Mth.lerp(warm, 0.24f, 0.62f);
                    float cg = Mth.lerp(warm, 0.30f, 0.20f);
                    float cb = Mth.lerp(warm, 0.78f, 0.66f);
                    int y0 = Math.round(hy - span + ry * ch);
                    g.fill(Math.round(rx * cw), y0, Math.round((rx + 1) * cw) + 1, Math.round(y0 + ch) + 1, rgba(cr, cg, cb, a));
                }
            }
        }

        // ---- coloured stars ----
        if (stars.on()) {
            int count = STAR_COUNT[q];
            float night = 1f - day;
            for (int i = 0; i < count && night > 0.05f; i++) {
                float sx = hash(i, 17) * w;
                float sy = hash(i, 91) * span;
                float tw = 0.6f + 0.4f * Mth.sin(clock * (1.1f + hash(i, 5) * 1.7f) + i);
                float a = night * alpha * (0.35f + 0.65f * hash(i, 41)) * tw;
                if (a <= 0.03f) continue;
                float temp = hash(i, 73);
                int col = rgba(
                        Mth.lerp(temp, 0.78f, 1.0f),
                        Mth.lerp(temp, 0.86f, 0.92f),
                        Mth.lerp(temp, 1.0f, 0.74f),
                        a * 0.9f);
                int size = hash(i, 13) > 0.88f ? 2 : 1;
                int yy = Math.round(hy - span + sy);
                g.fill(Math.round(sx), yy, Math.round(sx) + size, yy + size, col);
            }
        }

        // ---- sun and moon ----
        if (sunMoon.on()) {
            float[] p = new float[2];
            look(mc, partial, angle, 120.0, p);
            if (p != null) {
                boolean up = elevation > -0.05f;
                disc(g, p[0], p[1], up ? 7f : 5f, up ? 1.0f : 1.0f, up ? 0.97f : 0.86f, up ? 0.82f : 0.92f,
                        alpha * (up ? 0.95f : 0.75f), q);
                if (flare.on() && up && day > 0.25f) {
                    float cx = w * 0.5f;
                    float cy = h * 0.5f;
                    for (int i = 1; i <= 3; i++) {
                        float t = i / 4f;
                        float fx = p[0] + (cx - p[0]) * t * 1.6f;
                        float fy = p[1] + (cy - p[1]) * t * 1.6f;
                        disc(g, fx, fy, 3.2f - i * 0.5f, 0.75f, 0.85f, 1.0f, alpha * (0.20f - i * 0.04f), q);
                    }
                }
            }
        }
    }

    /** Projects a point far along the sun direction, or returns null when it is behind the camera. */
    private static void look(Minecraft mc, float partial, float angle, double distance, float[] out) {
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        double px = Projector.eyeX() + dx * distance;
        double py = Projector.eyeY() + dy * distance * 0.85;
        double pz = Projector.eyeZ();
        if (Projector.depth(px, py, pz) < -1.5) return;
        if (Projector.project(px, py, pz, out)) return;
        out[0] = Float.NaN;
        out[1] = Float.NaN;
    }

    /** A soft glow: a few nested squares with falling alpha, which reads as a bloomed disc. */
    private void disc(GuiGraphics g, float cx, float cy, float radius, float r, float gr, float b, float alpha, int q) {
        if (Float.isNaN(cx) || Float.isNaN(cy)) return;
        int layers = q == 0 ? 3 : q == 1 ? 5 : 7;
        for (int i = layers; i >= 1; i--) {
            float k = i / (float) layers;
            float rad = radius * (1f + k * 2.6f);
            float a = alpha * (1f - k * 0.85f) * 0.32f;
            int x0 = Math.round(cx - rad);
            int y0 = Math.round(cy - rad);
            g.fill(x0, y0, Math.round(cx + rad), Math.round(cy + rad), rgba(r, gr, b, a));
        }
        int core = Math.round(radius * 0.55f);
        g.fill(Math.round(cx) - core, Math.round(cy) - core, Math.round(cx) + core, Math.round(cy) + core, rgba(r, gr, b, alpha));
    }

    @Override
    public String info() {
        return fade > 0.02f ? Math.round(fade * 100f) + "%" : null;
    }
}
