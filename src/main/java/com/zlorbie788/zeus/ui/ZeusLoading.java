package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ZeusLoading {
    private ZeusLoading() {}

    private static final float LW = 368f;
    private static final float LH = 332f;
    private static final float MCX = 178f;
    private static final float MCY = 131f;
    private static final float MR = 123f;

    private static boolean probed;
    private static Field smoothField;
    private static Field trackerField;
    private static Field reasonField;
    private static Method progressMethod;
    private static boolean warned;

    private static long born;
    private static long last;
    private static float shown;

    private static long fullAt;
    private static long lastFull;

    private static float[] tick() {
        long now = System.nanoTime();
        if (last == 0 || now - last > 1_500_000_000L) {
            born = now;
            shown = 0f;
            fullAt = 0;
        }
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        return new float[] {(now - born) / 1e9f, dt};
    }

    public static boolean level(Screen screen, GuiGraphics g) {
        try {
            if (!UiSettings.LOADING.on()) return false;
            probe();
            if (portal(screen)) return false;
            float[] tt = tick();
            boolean single = Minecraft.getInstance().getSingleplayerServer() != null;
            if (single) {
                float target = progress(screen);
                float step = 1f - (float) Math.exp(-tt[1] * 6f);
                shown = Math.max(shown, shown + (target - shown) * step);
                if (target >= 0.999f) shown = Math.max(shown, Math.min(1f, shown + tt[1] * 1.5f));
                ring(g, screen.width, screen.height, Math.min(1f, shown), tt[0], 1f);
                if (shown >= 0.999f) lastFull = System.nanoTime();
            } else {
                join(g, screen.width, screen.height, tt[0]);
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            if (!warned) Zeus.LOG.warn("Zeus loading screen could not be drawn; using the game's own", e);
            warned = true;
            return false;
        }
    }

    public static boolean connect(Screen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        try {
            if (!UiSettings.LOADING.on()) return false;
            float[] tt = tick();
            if (joinFor != screen) {
                joinFor = screen;
                joinShown = 0f;
            }
            joinShown = Math.min(0.92f, joinShown + tt[1] * 0.55f);
            ring(g, screen.width, screen.height, joinShown, tt[0], 1f);
            for (GuiEventListener c : screen.children()) {
                if (c instanceof Renderable r) r.render(g, mouseX, mouseY, partialTick);
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            if (!warned) Zeus.LOG.warn("Zeus connecting screen could not be drawn; using the game's own", e);
            warned = true;
            return false;
        }
    }

    private static float joinShown;
    private static Screen joinFor;

    private static long startBorn;
    private static long startLast;
    private static float startShown;
    private static Object startSession;

    public static boolean startup(Object session, GuiGraphics g, int w, int h, float progress, boolean done, float alpha) {
        try {
            if (!UiSettings.LOADING.on()) return false;
            long now = System.nanoTime();
            if (session != startSession) {
                startSession = session;
                startBorn = now;
                startLast = now;
                startShown = 0f;
                fullAt = 0;
            }
            float dt = Math.min(0.1f, (now - startLast) / 1e9f);
            startLast = now;
            float t = (now - startBorn) / 1e9f;
            float target = done ? 1f : norm(progress);
            float step = 1f - (float) Math.exp(-dt * 6f);
            startShown = Math.max(startShown, startShown + (target - startShown) * step);
            if (done) startShown = Math.min(1f, startShown + dt * 1.5f);
            if (alpha < 0.999f) startShown = 1f;
            ring(g, w, h, Math.min(1f, startShown), t, Math.max(0f, Math.min(1f, alpha)));
            return true;
        } catch (RuntimeException | LinkageError e) {
            if (!warned) Zeus.LOG.warn("Zeus start screen could not be drawn; using the game's own", e);
            warned = true;
            return false;
        }
    }

    private static void probe() {
        if (probed) return;
        probed = true;
        try {
            Class<?> screenClass = LevelLoadingScreen.class;
            Class<?> trackerType = null;
            for (Constructor<?> c : screenClass.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[1].isEnum()) trackerType = p[0];
            }
            for (Field f : screenClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                try {
                    f.setAccessible(true);
                } catch (RuntimeException e) {
                    continue;
                }
                if (trackerType != null && t == trackerType) trackerField = f;
                else if (t == float.class) smoothField = f;
                else if (t.isEnum()) reasonField = f;
            }
            if (trackerType != null) {
                for (Method m : trackerType.getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == float.class && m.getDeclaringClass() != Object.class) progressMethod = m;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            Zeus.LOG.warn("The game's loading screen could not be inspected", e);
        }
    }

    private static boolean portal(Screen screen) {
        if (reasonField == null) return false;
        try {
            Object v = reasonField.get(screen);
            return v instanceof Enum<?> e && e.ordinal() != 2;
        } catch (IllegalAccessException | RuntimeException e) {
            return false;
        }
    }

    private static float progress(Screen screen) {
        float best = 0f;
        try {
            if (trackerField != null && progressMethod != null) {
                Object tracker = trackerField.get(screen);
                if (tracker != null) best = Math.max(best, norm((Float) progressMethod.invoke(tracker)));
            }
            if (smoothField != null) best = Math.max(best, norm(smoothField.getFloat(screen)));
        } catch (ReflectiveOperationException | RuntimeException e) {
        }
        return best;
    }

    private static float norm(float v) {
        if (Float.isNaN(v) || v < 0f) return 0f;
        if (v > 1f) v = v <= 100f ? v / 100f : 1f;
        return Math.min(1f, v);
    }

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static float unit(int w, int h) {
        return Math.min(h * 0.50f / LH, w * 0.62f / LW);
    }

    private static Tex.Img logoPicture(float k, int gs) {
        return Tex.fitted("load_logo", "textures/gui/menu/logo.png", Math.round(LW * k * gs), Math.round(LH * k * gs), false, 0, 0);
    }

    private static void ring(GuiGraphics g, int w, int h, float p, float t, float a) {
        g.fill(0, 0, w, h, Draw.rgba(0xFF03050B, a));
        float k = unit(w, h);
        int gs = Tex.guiScale();
        int lw = Math.round(LW * k);
        int lh = Math.round(LH * k);
        int lx = Math.round(w / 2f - MCX * k);
        int ly = Math.round((h - lh - 26f * k) / 2f);
        float cx = lx + MCX * k;
        float cy = ly + MCY * k;
        float r = MR * k * 1.03f;

        Lightning.bloom(g, cx, cy, MR * k * 2.3f, 0xFF3A66C8, (0.05f + 0.13f * ease(p)) * a);

        Tex.Img logo = logoPicture(k, gs);
        if (logo != null) {
            int v = Math.round(255f * (0.20f + 0.80f * ease(p)));
            logo.draw(g, lx, ly, lw, lh, Draw.rgba(0xFF000000 | (v << 16) | (v << 8) | v, a));
        }

        float sweep = (float) (Math.PI * 2.0) * p;
        float start = (float) (-Math.PI / 2.0);
        int n = Math.max(2, Math.round(p * 84f));
        float width = Math.max(0.55f, r / 105f);
        boolean full = p >= 0.999f;
        float pulse = (full ? 0.8f + 0.2f * (float) Math.sin(t * 5f) : 1f) * a;
        float px = cx + (float) Math.cos(start) * r;
        float py = cy + (float) Math.sin(start) * r;
        for (int i = 1; i <= n && p > 0.002f; i++) {
            float ang = start + sweep * i / n;
            float x = cx + (float) Math.cos(ang) * r;
            float y = cy + (float) Math.sin(ang) * r;
            float fade = 0.50f + 0.50f * i / n;
            Lightning.seg(g, px, py, x, y, fade * pulse, 0xFF5C9CFF, 0xFF2A5CFF, width, 3);
            px = x;
            py = y;
        }
        if (p > 0.002f) {
            float flicker = 0.85f + 0.15f * (float) Math.sin(t * 17f);
            Lightning.bloom(g, px, py, 8f * k + 6f, 0xFF9CC4FF, (full ? 0.5f : 0.9f) * flicker * a);
            Lightning.bloom(g, px, py, 3f * k + 2.5f, 0xFFFFFFFF, 0.9f * flicker * a);
        }
        if (full) Lightning.bloom(g, cx, cy, r * 1.35f, 0xFF5C9CFF, 0.10f * pulse);
        if (full && a > 0.999f) finish(g, w, h, cx, cy, r, k, logo, lx, ly, lw, lh);

        int pct = Math.min(100, (int) Math.floor(p * 100f + 0.0001f));
        float s = Math.max(1f, k * 1.7f);
        Draw.textCenterS(g, pct + "%", w / 2, ly + lh + Math.round(8f * k), Draw.rgba(0xFFB4C0DC, a), s);

        if (UiSettings.LOADING_TIPS.on()) tip(g, w, h, t, ly + lh + Math.round(8f * k) + Math.round(14f * s) + Math.round(10f * k), k, a);

        float in = Math.max(0f, 1f - t / 0.35f);
        if (in > 0.01f) g.fill(0, 0, w, h, Draw.rgba(0xFF000000, in));
    }

    private static final String[] TIPS = {
            "Press Z in a world to open the Zeus panel.",
            "Click a module's row to open its settings; click its switch to turn it on.",
            "Click the None chip on a module and press a key to give it a key.",
            "Type /zeus find followed by a word to search every module.",
            "Type /zeus settings followed by a module name to read all of its settings.",
            "Panic, at the bottom right of the panel, switches everything off at once.",
            "Zeus Customs on the main menu opens the Zeus settings.",
            "The HUD editor lets you drag, scale and hide every Zeus card on the screen.",
            "Calm mode turns off the flashes, the shake and the sliding in.",
            "Effects quality Auto draws the effects lighter whenever your frame rate drops.",
            "The Performance Monitor shows what each module costs you.",
            "The Classic menu link at the bottom of the menu brings back the game's own title screen.",
            "Hover Totem puts a totem in your offhand just by pointing at it in any container.",
            "A storm can roll in on the main menu if you wait; the Storms setting turns it off.",
            "In December the menu has snow. Seasonal menu, set to Always, shows it any time.",
    };
    private static int tipBase;
    private static float tipLastT = 1e9f;

    private static void tip(GuiGraphics g, int w, int h, float t, int y, float k, float alpha) {
        if (t < tipLastT) tipBase = (int) (Math.abs(System.nanoTime() / 1000L) % TIPS.length);
        tipLastT = t;
        float period = 6.5f;
        int idx = (tipBase + (int) (t / period)) % TIPS.length;
        float f = (t % period) / period;
        float a = alpha * Math.min(1f, f / 0.08f) * Math.min(1f, (1f - f) / 0.10f) * Math.min(1f, Math.max(0f, (t - 0.6f) / 0.5f));
        if (a <= 0.02f) return;
        int cap = Math.max(7, Math.round(7.5f * k));
        GreekFont.drawCentered(g, "TIP", w / 2, y, cap, Draw.rgba(0xFF8FA6D8, a), true);
        String text = TIPS[idx];
        float sc = Math.max(1f, k * 1.05f);
        int max = Math.round(w * 0.8f);
        while (sc > 0.7f && Draw.widthS(text, sc) > max) sc -= 0.05f;
        Draw.textCenterS(g, text, w / 2, y + cap + Math.round(7f * k), Draw.rgba(0xFFB4C0DC, 0.85f * a), sc);
    }

    private static void finish(GuiGraphics g, int w, int h, float cx, float cy, float r, float k, Tex.Img logo, int lx, int ly, int lw, int lh) {
        long now = System.nanoTime();
        if (fullAt == 0) fullAt = now;
        float s = (now - fullAt) / 1e9f;
        float fl = s < 0.10f ? s / 0.10f : Math.max(0f, 1f - (s - 0.10f) / 0.60f);
        fl *= fl;
        if (fl > 0.01f) {
            g.fill(0, 0, w, h, Draw.rgba(0xFFBCD4FF, 0.30f * fl));
            Lightning.bloom(g, cx, cy, r * 2.4f, 0xFFDCE8FF, 0.65f * fl);
            if (logo != null) logo.draw(g, lx, ly, lw, lh, Draw.rgba(0xFFBCD4FF, 0.40f * fl));
        }
        float q = s / 0.80f;
        if (q > 0f && q < 1f) {
            float rad = r * (1f + 3.4f * (1f - (1f - q) * (1f - q)));
            float a = (float) Math.pow(1f - q, 1.5);
            float px = cx + rad;
            float py = cy;
            int segs = 72;
            for (int i = 1; i <= segs; i++) {
                float an = i * 6.2832f / segs;
                float x2 = cx + (float) Math.cos(an) * rad;
                float y2 = cy + (float) Math.sin(an) * rad;
                if (Math.max(py, y2) < cy + r * 1.1f) Draw.line(g, px, py, x2, y2, Math.max(1f, (3.2f - 2.0f * q) * k), Draw.rgba(0xFFDCE8FF, 0.7f * a));
                px = x2;
                py = y2;
            }
        }
    }

    public static void worldFade(GuiGraphics g, int w, int h) {
        if (lastFull == 0) return;
        try {
            if (Minecraft.getInstance().screen != null) return;
        } catch (RuntimeException e) {
            return;
        }
        float s = (System.nanoTime() - lastFull) / 1e9f;
        if (s > 1.0f) {
            lastFull = 0;
            return;
        }
        float a = (float) Math.pow(1f - s, 1.7);
        g.fill(0, 0, w, h, Draw.rgba(0xFF03050B, a));
    }

    private static void join(GuiGraphics g, int w, int h, float t) {
        ZeusTitleScreen.scene(g, w, h, t, new long[2], false);
        float k = unit(w, h);
        int gs = Tex.guiScale();
        int lw = Math.round(LW * k);
        int lh = Math.round(LH * k);
        int lx = Math.round(w / 2f - MCX * k);
        int ly = Math.round(h * 0.40f - MCY * k);
        float appear = ease(t / 0.6f);
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 1.6f);
        Lightning.bloom(g, lx + MCX * k, ly + MCY * k, MR * k * 1.55f, 0xFF7C9CE0, (0.05f + 0.05f * pulse + 0.20f * ZeusTitleScreen.lastFlash) * appear);
        Tex.Img logo = logoPicture(k, gs);
        if (logo != null) logo.draw(g, lx, ly, lw, lh, Draw.rgba(0xFFFFFFFF, appear));
        float in = Math.max(0f, 1f - t / 0.5f);
        if (in > 0.01f) g.fill(0, 0, w, h, Draw.rgba(0xFF000000, in * in));
    }
}
