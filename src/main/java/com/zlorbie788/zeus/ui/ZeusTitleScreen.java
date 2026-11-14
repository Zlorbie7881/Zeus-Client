package com.zlorbie788.zeus.ui;

import com.mojang.realmsclient.RealmsMainScreen;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

public final class ZeusTitleScreen extends Screen {
    private static final float MW = 1360f;
    private static final float MH = 768f;
    private static final float PLATE_X = 490f;
    private static final float PLATE_W = 382f;
    private static final float PLATE_H = 48f;
    private static final float[] PLATE_Y = {300f, 356f, 412f, 468f, 524f, 580f, 636f};

    // the logo is 85% of its old size so seven buttons fit above the version line
    private static final float LOGO_X = 527.6f;
    private static final float LOGO_Y = 12f;
    private static final float LOGO_W = 312.8f;
    private static final float LOGO_H = 282.2f;
    private static final float MEDAL_CX = 678.9f;
    private static final float MEDAL_CY = 123.4f;
    private static final float MEDAL_R = 104.6f;

    private static final float[][] ICON = {{74.5f, 714f}, {165.5f, 714f}, {256.5f, 714f}, {347.5f, 714f}, {438.5f, 714f}};

    private static final String DISCORD = "https://discord.gg/znrj2TfCP8";
    private static final float ICON_R = 34f;
    private static final float ICON_PIC = 37f;
    private static final String[] NAMES = {"singleplayer", "multiplayer", "customs", "crates", "settings", "realms", "exit"};
    /** The Zeus Crates plate glows gold even when idle, so it is found. */
    private static final int CRATES = 3;
    private static final String[] ICON_NAMES = {"language", "accessibility", "settings", "credits", "discord"};

    private static final int[] ICON_FRAMES = {24, 24, 0, 12, 0};
    private static final float CLICK_LENGTH = 0.34f;
    private static final float CLICK_GO = 0.25f;
    private static final String[] ICON_TIPS = {"Language", "Accessibility", "Zeus Settings", "About Zeus", "Join the Discord"};

    private static final int PIC_W = 1920;
    private static final int PIC_H = 1080;
    private static final int PILLAR_L_W = 370;
    private static final int PILLAR_R_X = 1538;
    private static final int PILLAR_R_W = 382;
    private static final int CLOUD_W = 2560;
    private static final int CLOUD_H = 890;
    private static final int MIST_H = 420;

    private final long born = System.nanoTime();
    private final float[] glow = new float[7];
    private final float[] iconGlow = new float[5];
    private final float[] plateSpring = new float[7];
    private final float[] plateVel = new float[7];
    private final float[] plateMag = new float[7];
    private final float[] enterAt = new float[7];
    private final float[] enterX = new float[7];
    private final boolean[] platePrev = new boolean[7];
    private final float[] iconSpring = new float[5];
    private final float[] iconVel = new float[5];
    private final float[] iconEnter = new float[5];
    private final boolean[] iconPrev = new boolean[5];
    private final int[] shineBand = new int[2];
    private int soundPlate = -1;
    private int soundIcon = -1;
    private final float[] iconPhase = new float[4];

    private int clickKind = -1;
    private int clickIdx = -1;
    private long clickAt;
    private final float[] clickBox = new float[7];
    private final MenuNews news = new MenuNews();

    private int selected = -1;
    private boolean selectedByMouse;

    private final int[] logoBox = {0, 0, 0, 0};

    private final float[] logoC = {0, 0, 0};
    private float logoHot;
    private long logoHotAt;
    private int curX = -1;
    private int curY = -1;
    private boolean mouseMoved;
    private int lastMx = -1;
    private int lastMy = -1;
    private long lastNanos;
    private float sceneParX;
    private float sceneParY;

    public ZeusTitleScreen() {
        super(Component.literal("Zeus Client"));
    }

    @Override
    protected void init() {
        warm();
    }

    private void warm() {
        try {
            int gs = Tex.guiScale();
            float u = u();
            float[] c = canvas(width, height, 0f, 0f);
            scenePictures(c[0], gs);
            MenuStorm.warm(width, height, c[0], gs, canvasPx(c[0], gs, CLOUD_W));
            MenuSnow.warm(height, gs);
            int ipw = Math.min(222, Math.round(2 * ICON_PIC * u * gs));
            for (int i = 0; i < 4; i++) {
                if (ICON_FRAMES[i] > 0) Tex.fitted("menu_anim_" + ICON_NAMES[i], "textures/gui/menu/icon_anim_" + ICON_NAMES[i] + ".png", ipw * ICON_FRAMES[i], ipw, false, 0, 0);
            }
            Tex.fitted("menu_logo", "textures/gui/menu/logo.png", Math.round(LOGO_W * u * gs), Math.round(LOGO_H * u * gs), false, 0, 0);
            for (String n : NAMES) Tex.fitted("menu_ink_" + n, "textures/gui/menu/ink_" + n + ".png", Math.round(PLATE_W * u * gs), Math.round(PLATE_H * u * gs), false, 0, 0);
            Tex.fitted("menu_plate", "textures/gui/menu/plate.png", Math.round(PLATE_W * u * gs), Math.round(PLATE_H * u * gs), false, 0, 0);
            int ip = Math.round(2 * ICON_PIC * u * gs);
            for (String n : ICON_NAMES) Tex.fitted("menu_icon_" + n, "textures/gui/menu/icon_" + n + ".png", ip, ip, false, 0, 0);
            if (news.layout(width, height, plateRight(), newsBottom(), newsRight())) news.prepare();
        } catch (RuntimeException | LinkageError e) {
            Zeus.LOG.warn("Menu pictures could not be prepared", e);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private float time() {
        return (System.nanoTime() - born) / 1e9f;
    }

    private float u() {
        return Math.min(width / MW, height / MH);
    }

    private int ux(float unit) {
        return Math.round((width - MW * u()) / 2f + unit * u());
    }

    private int uy(float unit) {
        return Math.round((height - MH * u()) / 2f + unit * u());
    }

    private int plateX() {
        return ux(PLATE_X);
    }

    private int plateW() {
        return Math.round(PLATE_W * u());
    }

    private int plateH() {
        return Math.round(PLATE_H * u());
    }

    private int plateRight() {
        return plateX() + plateW();
    }

    private int plateY(int i) {
        return uy(PLATE_Y[i]);
    }

    private int iconCx(int i) {
        return Math.round(ICON[i][0] * u());
    }

    private int iconCy() {
        return Math.round(height - (MH - ICON[0][1]) * u());
    }

    private int newsBottom() {
        return Math.max(10, Math.round((MH - 750f) * u()));
    }

    private int newsRight() {
        return Math.max(8, Math.round(18f * u()));
    }

    private int plateAt(int mx, int my) {
        for (int i = 0; i < NAMES.length; i++) {
            if (mx >= plateX() && mx < plateX() + plateW() && my >= plateY(i) && my < plateY(i) + plateH()) return i;
        }
        return -1;
    }

    private int iconAt(int mx, int my) {
        float r = ICON_R * 1.06f * u();
        for (int i = 0; i < ICON.length; i++) {
            float dx = mx - iconCx(i);
            float dy = my - iconCy();
            if (dx * dx + dy * dy <= r * r) return i;
        }
        return -1;
    }

    static float parX;
    static float parY;
    static float flashX;
    static float flashY;

    static float lastFlash;

    private static float[] canvas(int width, int height, float t, float depth) {
        float cs = Math.max(width / MW, height / MH) * 1.035f;
        boolean calm = UiSettings.CALM.on() || Perf.fast();
        float sway = calm ? 0f : (float) Math.sin(t * 0.12f) * 0.5f + (float) Math.sin(t * 0.047f + 1.3f) * 0.3f;
        float mx = calm ? 0f : parX;
        float my = calm ? 0f : parY;
        float ox = (width - MW * cs) / 2f + depth * (sway * 0.009f * width - mx * 0.012f * width);
        float oy = (height - MH * cs) / 2f + depth * (-my * 0.012f * height);
        return new float[] {cs, ox, oy};
    }

    private static Tex.Img pic(String key, String path, int w, int h) {
        return Tex.fitted(key, path, w, h, false, 0, 0);
    }

    private static int canvasPx(float cs, int gs, int cap) {
        return Math.max(64, Math.min(Math.round(MW * cs * gs), cap));
    }

    private static void scenePictures(float cs, int gs) {
        int cw = canvasPx(cs, gs, PIC_W);
        int ch = Math.round(cw * (float) PIC_H / PIC_W);
        pic("menu_bg", "textures/gui/menu/background.png", cw, ch);
        pic("menu_pillar_l", "textures/gui/menu/pillar_l.png", Math.round(cw * PILLAR_L_W / (float) PIC_W), ch);
        pic("menu_pillar_r", "textures/gui/menu/pillar_r.png", Math.round(cw * PILLAR_R_W / (float) PIC_W), ch);
        int kw = canvasPx(cs, gs, CLOUD_W);
        int kh = Math.round(kw * (float) CLOUD_H / CLOUD_W);
        pic("menu_cloud_far", "textures/gui/menu/cloud_far.png", kw, kh);
        pic("menu_cloud_mid", "textures/gui/menu/cloud_mid.png", kw, kh);
        pic("menu_cloud_near", "textures/gui/menu/cloud_near.png", kw, kh);
        if (!(UiSettings.CALM.on() || Perf.fast())) {
            pic("menu_cloud_far_b", "textures/gui/menu/cloud_far_b.png", kw, kh);
            pic("menu_cloud_mid_b", "textures/gui/menu/cloud_mid_b.png", kw, kh);
            pic("menu_cloud_near_b", "textures/gui/menu/cloud_near_b.png", kw, kh);
        }
        int mh = Math.round(kw * (float) MIST_H / CLOUD_W);
        pic("menu_mist_a", "textures/gui/menu/mist_a.png", kw, mh);
        pic("menu_mist_b", "textures/gui/menu/mist_b.png", kw, mh);
    }

    private static float skyFlash(float t, int width, int height) {
        int slot = (int) Math.floor(t / 6f);
        float local = t - slot * 6f;
        float best = 0f;
        for (int n = 0; n < 2; n++) {
            if (n == 1 && Lightning.hash(slot, 44) < 0.5f) continue;
            float start = n == 0 ? 0.8f + 2.0f * Lightning.hash(slot, 41) : 3.6f + 0.9f * Lightning.hash(slot, 42);
            float e = local - start;
            if (e < 0f || e > 1.1f) continue;
            float v = e < 0.07f ? e / 0.07f : e < 0.15f ? 0.30f : e < 0.24f ? 1f : Math.max(0f, 1f - (e - 0.24f) / 0.8f);
            if (n == 1) v *= 0.7f;
            if (v > best) {
                best = v;
                flashX = width * (0.20f + 0.60f * Lightning.hash(slot, 43 + n));
                flashY = height * (0.30f + 0.25f * Lightning.hash(slot, 47 + n));
            }
        }
        return best;
    }

    private static void strips(GuiGraphics g, Tex.Img img, float[] c, float top, float hUnits, float offset, float[] alpha, int tintRgb,
                               float waveAmp, float waveK, float phase) {
        if (img == null) return;
        int n = alpha.length;
        float sw = MW / n;
        float cs = c[0];
        int dh = Math.round(hUnits * cs);
        float off = ((offset % MW) + MW) % MW;
        for (int i = 0; i < n; i++) {
            if (alpha[i] <= 0.01f) continue;
            float cx = i * sw;
            int dx0 = Math.round(c[1] + cx * cs);
            int dx1 = Math.round(c[1] + (cx + sw) * cs);
            int dy = Math.round(c[2] + (top + (waveAmp == 0f ? 0f : waveAmp * (float) Math.sin(cx * waveK + phase))) * cs);
            int col = Draw.rgba(tintRgb, alpha[i]);
            int s0 = Math.round((cx + off) / MW * img.w);
            int s1 = Math.round((cx + sw + off) / MW * img.w);
            int sx = s0 % img.w;
            int sWide = Math.max(1, s1 - s0);
            int dw = dx1 - dx0;
            if (sx + sWide <= img.w) {
                img.drawPart(g, dx0, dy, dw, dh, sx, 0, sWide, img.h, col);
            } else {
                int first = img.w - sx;
                int dFirst = Math.max(1, Math.round(dw * first / (float) sWide));
                img.drawPart(g, dx0, dy, dFirst, dh, sx, 0, first, img.h, col);
                if (dw - dFirst > 0) img.drawPart(g, dx0 + dFirst, dy, dw - dFirst, dh, 0, 0, sWide - first, img.h, col);
            }
        }
    }

    private static float[] fill(int n, float v) {
        float[] a = new float[n];
        java.util.Arrays.fill(a, v);
        return a;
    }

    static void scene(GuiGraphics g, int width, int height, float t, long[] flash) {
        scene(g, width, height, t, flash, true);
    }

    static void scene(GuiGraphics g, int width, int height, float t, long[] flash, boolean menu) {
        if (!menu) MenuStorm.reset();
        float ct = menu ? MenuStorm.update() : t;
        float lv = menu ? MenuStorm.level : 0f;
        MenuSnow.update();
        if (menu) MenuAudio.ambience(lv, MenuSnow.level());
        int gs = Tex.guiScale();
        boolean still = UiSettings.CALM.on() || Perf.fast();
        float[] back = canvas(width, height, t, 0.35f);
        float[] mid = canvas(width, height, t, 0.65f);
        float[] front = canvas(width, height, t, 1.0f);
        float cs = back[0];
        int cw = canvasPx(cs, gs, PIC_W);
        int ch = Math.round(cw * (float) PIC_H / PIC_W);
        int kw = canvasPx(cs, gs, CLOUD_W);
        int kh = Math.round(kw * (float) CLOUD_H / CLOUD_W);
        float fl = still ? 0f : skyFlash(t, width, height);
        float sf = still ? 0f : MenuStorm.flash;
        if (sf > fl) {
            fl = sf;
            flashX = width * MenuStorm.flashX;
            flashY = height * MenuStorm.flashY;
        }
        lastFlash = fl;

        Tex.Img bg = pic("menu_bg", "textures/gui/menu/background.png", cw, ch);
        if (bg != null) bg.draw(g, Math.round(back[1]), Math.round(back[2]), Math.round(MW * cs), Math.round(MH * cs), 0xFFFFFFFF);
        else g.fillGradient(0, 0, width, height, 0xFF0A1226, 0xFF050810);

        float moonCover = lv > 0.004f ? ceilingCover(Math.min(16, (int) (MOON_X / (MW / 17f))), 17, lv) : 0f;

        if (!still) {
            float mxp = back[1] + 1015f * cs;
            float myp = back[2] + 90f * cs;
            Lightning.bloom(g, mxp, myp, 150f * cs, 0xFFB4CCFF, (0.05f + 0.035f * (0.5f + 0.5f * (float) Math.sin(t * 0.8f))) * (1f - moonCover));
            for (int i = 0; i < 70; i++) {
                float sx = 175f + Lightning.hash(i, 61) * 1010f;
                float sy = 8f + Lightning.hash(i, 62) * 300f;
                float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.7f + 1.3f * Lightning.hash(i, 63)) + i * 2.1f);
                int ix = Math.round(back[1] + sx * cs);
                int iy = Math.round(back[2] + sy * cs);
                g.fill(ix, iy, ix + 1, iy + 1, Draw.rgba(0xFFC8DCFF, (0.10f + 0.55f * tw * tw) * (1f - Math.min(1f, lv * 1.15f))));
            }
        }
        if (moonCover > 0.02f) {
            float c = moonCover * moonCover * (3f - 2f * moonCover);
            int mx = Math.round(back[1] + MOON_X * cs);
            int my = Math.round(back[2] + 90f * cs);
            Lightning.disc(g, mx, my, Math.round(66f * cs), Draw.rgba(0xFF0A1630, 0.26f * c));
            Lightning.disc(g, mx, my, Math.round(52f * cs), Draw.rgba(0xFF0A1630, 0.42f * c));
            Lightning.disc(g, mx, my, Math.round(45f * cs), Draw.rgba(0xFF0A1630, 0.60f * c));
        }
        if (MenuSnow.level() > 0.01f || MenuAurora.level() > 0.01f) {
            MenuAurora.draw(g, width, height, t, lv, back[1] + MOON_X * cs, back[2] + 90f * cs, 66f * cs, still);
        }
        if (fl > 0.01f) {
            Lightning.bloom(g, flashX, flashY, width * 0.40f, 0xFF9CBEFF, fl * 0.8f);
            g.fillGradient(0, 0, width, Math.round(height * 0.70f), Draw.rgba(0xFF9CB8F0, 0.10f * fl), 0x00000000);
        }

        if (lv > 0.004f) {
            MenuStorm.bolt(g, width, height);
            stormCeiling(g, back, front, kw, lv, fl, ct, still);
            if (!still && moonCover > 0.08f && moonCover < 0.98f) {
                float rim = (float) Math.sin(Math.PI * moonCover);
                float mxp = back[1] + MOON_X * back[0];
                float myp = back[2] + 90f * back[0];
                Lightning.bloom(g, mxp, myp, 95f * back[0], 0xFFD2E2FF, 0.20f * rim * rim);
            }
        }

        float cloudTop = MH - CLOUD_H * (MW / CLOUD_W);
        float cloudH = CLOUD_H * (MW / CLOUD_W);
        int nStrip = 17;
        float[] env = new float[nStrip];
        for (int i = 0; i < nStrip; i++) {
            float sxm = (back[1] + (i + 0.5f) * (MW / nStrip) * cs) / Math.max(1, width);
            float d = Math.abs(sxm - 0.5f) * 2f;
            float k = Math.max(0f, Math.min(1f, (d - 0.18f) / 0.55f));
            env[i] = 0.34f + 0.66f * (k * k * (3f - 2f * k));
            env[i] += (1f - env[i]) * 0.85f * lv;
        }
        String[] names = {"far", "mid", "near"};
        float[] speed = {6f, 12f, 21f};
        float[] base = {0.80f, 0.95f, 1.0f};
        for (int L = 0; L < 3; L++) {
            Tex.Img img = pic("menu_cloud_" + names[L], "textures/gui/menu/cloud_" + names[L] + ".png", kw, kh);
            float[] c = L == 0 ? back : L == 1 ? mid : front;
            c = new float[] {c[0], (back[1] * (2 - L) + front[1] * L) / 2f, c[2]};
            float bob = still ? 0f : 2.2f * (float) Math.sin(t * (0.25f + 0.1f * L) + L * 1.7f);
            float[] a = new float[nStrip];
            float thick = base[L] + (1f - base[L]) * lv;
            for (int i = 0; i < nStrip; i++) a[i] = Math.min(1f, env[i] * thick);
            float off = still ? 140f * (L + 1) : ct * speed[L] + 380f * L;
            int bankTint = lv > 0.004f ? Draw.mix(0xFFFFFFFF, 0xFF5A6480, 0.9f * lv) : 0xFFFFFFFF;
            Tex.Img twin = still ? null : pic("menu_cloud_" + names[L] + "_b", "textures/gui/menu/cloud_" + names[L] + "_b.png", kw, kh);
            if (twin != null) {
                float wv = 0.5f - 0.5f * (float) Math.cos((ct + 23f * L) * (float) (Math.PI * 2.0) / (46f + 14f * L));
                float wv2 = wv * wv * (3f - 2f * wv);
                float[] aa = new float[nStrip];
                float[] ab = new float[nStrip];
                for (int i = 0; i < nStrip; i++) {
                    aa[i] = a[i] * Math.min(1f, 2f * (1f - wv2));
                    ab[i] = a[i] * Math.min(1f, 2f * wv2);
                }
                strips(g, img, c, cloudTop + bob, cloudH, off, aa, bankTint, 0f, 0f, 0f);
                strips(g, twin, c, cloudTop + bob, cloudH, off, ab, bankTint, 0f, 0f, 0f);
            } else {
                strips(g, img, c, cloudTop + bob, cloudH, off, a, bankTint, 0f, 0f, 0f);
            }
            if (fl > 0.02f && L > 0) {
                float[] b = new float[nStrip];
                for (int i = 0; i < nStrip; i++) b[i] = env[i] * 0.55f * fl;
                strips(g, img, c, cloudTop + bob, cloudH, off, b, L == 2 ? 0xFFD2E2FF : 0xFFBED2FF, 0f, 0f, 0f);
            }
        }

        if (lv > 0.05f) {
            int sh = Math.round(kw * 520f / 2560f);
            Tex.Img scud = pic("menu_storm_scud", "textures/gui/menu/storm_scud.png", kw, sh);
            if (scud != null) {
                float[] a = new float[nStrip];
                for (int i = 0; i < nStrip; i++) a[i] = Math.min(1f, (0.55f + 0.45f * env[i]) * lv * lv * 0.9f);
                float scudH = 520f * (MW / 2560f);
                float[] c = {front[0], front[1], front[2]};
                float off = still ? 400f : ct * 46f;
                strips(g, scud, c, MH - scudH - 4f, scudH, off, a, Draw.mix(0xFFFFFFFF, 0xFF566078, 0.9f * lv), 0f, 0f, 0f);
                if (fl > 0.02f) {
                    float[] b = new float[nStrip];
                    for (int i = 0; i < nStrip; i++) b[i] = 0.50f * fl * lv;
                    strips(g, scud, c, MH - scudH - 4f, scudH, off, b, 0xFFC8DAFF, 0f, 0f, 0f);
                }
            }
        }

        int pwL = Math.round(cw * PILLAR_L_W / (float) PIC_W);
        int pwR = Math.round(cw * PILLAR_R_W / (float) PIC_W);
        Tex.Img pl = pic("menu_pillar_l", "textures/gui/menu/pillar_l.png", pwL, ch);
        Tex.Img pr = pic("menu_pillar_r", "textures/gui/menu/pillar_r.png", pwR, ch);
        float unitPerPx = MW / PIC_W;
        int plx = Math.round(front[1]);
        int ply = Math.round(front[2]);
        int plw = Math.round(PILLAR_L_W * unitPerPx * front[0]);
        int prx = Math.round(front[1] + PILLAR_R_X * unitPerPx * front[0]);
        int prw = Math.round(PILLAR_R_W * unitPerPx * front[0]);
        int phh = Math.round(MH * front[0]);
        if (pl != null) pl.draw(g, plx, ply, plw, phh, 0xFFFFFFFF);
        if (pr != null) pr.draw(g, prx, ply, prw, phh, 0xFFFFFFFF);
        if (fl > 0.02f) {
            int lit = Draw.rgba(0xFFC8DCFF, 0.22f * fl);
            if (pl != null) pl.draw(g, plx, ply, plw, phh, lit);
            if (pr != null) pr.draw(g, prx, ply, prw, phh, lit);
        }

        int mh = Math.round(kw * (float) MIST_H / CLOUD_W);
        float mistH = MIST_H * (MW / CLOUD_W);
        int mStrip = still ? 34 : 136;
        float[] fa = new float[mStrip];
        float[] fb = new float[mStrip];
        for (int i = 0; i < mStrip; i++) {
            float sxm = (front[1] + (i + 0.5f) * (MW / mStrip) * front[0]) / Math.max(1, width);
            float d = Math.abs(sxm - 0.5f) * 2f;
            float breathe = still ? 1f : 0.86f + 0.14f * (float) Math.sin(t * 0.35f + i * 0.09f);
            fa[i] = (0.62f + 0.38f * d) * breathe;
            fb[i] = (0.55f + 0.45f * d) * (still ? 1f : 0.88f + 0.12f * (float) Math.sin(t * 0.28f + 1.9f + i * 0.07f));
        }
        Tex.Img ma = pic("menu_mist_a", "textures/gui/menu/mist_a.png", kw, mh);
        Tex.Img mb = pic("menu_mist_b", "textures/gui/menu/mist_b.png", kw, mh);
        float waveT = still ? 0f : t;
        int mistTint = lv > 0.004f ? Draw.mix(0xFFFFFFFF, 0xFF7A86A6, 0.8f * lv) : 0xFFFFFFFF;
        strips(g, mb, mid, MH - mistH * 0.92f, mistH, still ? 300f : -ct * 9f + 700f, fb, mistTint, still ? 0f : 4.5f, 0.021f, waveT * 0.55f + 1.3f);
        strips(g, ma, front, MH - mistH * 0.80f, mistH, still ? 0f : ct * 16f, fa, mistTint, still ? 0f : 6.5f, 0.015f, -waveT * 0.7f);

        if (lv > 0.004f) {
            g.fill(0, 0, width, height, Draw.rgba(0xFF050A1C, 0.18f * lv));
            g.fillGradient(0, 0, width, Math.round(height * 0.62f), Draw.rgba(0xFF02040C, 0.22f * lv), 0x00000000);
            if (fl > 0.02f) g.fill(0, 0, width, height, Draw.rgba(0xFFB4CCFF, 0.10f * fl));
            MenuStorm.rain(g, width, height);
        }
        if (MenuSnow.level() > 0.004f) {
            g.fill(0, 0, width, height, Draw.rgba(0xFF9CB8E8, 0.045f * MenuSnow.level()));
            MenuSnow.draw(g, width, height, lv);
        }

        g.fillGradient(0, 0, width, Math.max(1, height / 7), 0x30000000, 0x00000000);
        g.fillGradient(0, height - Math.max(1, height / 6), width, height, 0x00000000, 0x50000000);
    }

    private static final float MOON_X = 1015f;

    static float ceilingCover(int i, int n, float lv) {
        float x = (i + 0.5f) / n;
        float phase = (MenuStorm.rising ? 1f - x : x) * 0.75f;
        float v = Math.max(0f, Math.min(1f, lv * 1.75f - phase));
        return v * v * (3f - 2f * v);
    }

    private static void stormCeiling(GuiGraphics g, float[] back, float[] front, int kw, float lv, float fl, float ct, boolean still) {
        int kh = Math.round(kw * 700f / 2560f);
        float hUnits = 700f * (MW / 2560f);
        int n = 17;
        {
            int fh = Math.round(kw * 1000f / 2560f);
            Tex.Img fa = pic("menu_storm_full_a", "textures/gui/menu/storm_full_a.png", kw, fh);
            Tex.Img fb = still ? null : pic("menu_storm_full_b", "textures/gui/menu/storm_full_b.png", kw, fh);
            float fullH = 860f;
            float[] c = {back[0], back[1] * 0.75f + front[1] * 0.25f, back[2]};
            float[] a = new float[n];
            for (int i = 0; i < n; i++) a[i] = Math.min(1f, ceilingCover(i, n, lv) * 0.97f);
            float off = still ? 500f : ct * 5f + 1300f;
            float drop = (1f - lv) * -70f;
            int tint = Draw.mix(0xFFFFFFFF, 0xFFB4BEDA, 0.5f);
            if (fa != null && fb != null) {
                float wv = 0.5f - 0.5f * (float) Math.cos(ct * (float) (Math.PI * 2.0) / 34f);
                wv = wv * wv * (3f - 2f * wv);
                float[] aa = new float[n];
                float[] ab = new float[n];
                for (int i = 0; i < n; i++) {
                    aa[i] = a[i] * Math.min(1f, 2f * (1f - wv));
                    ab[i] = a[i] * Math.min(1f, 2f * wv);
                }
                strips(g, fa, c, -40f + drop, fullH, off, aa, tint, 0f, 0f, 0f);
                strips(g, fb, c, -40f + drop, fullH, off, ab, tint, 0f, 0f, 0f);
            } else {
                strips(g, fa, c, -40f + drop, fullH, off, a, tint, 0f, 0f, 0f);
            }
            if (fl > 0.02f && fa != null) {
                float[] b = new float[n];
                for (int i = 0; i < n; i++) b[i] = a[i] * 0.42f * fl;
                strips(g, fa, c, -40f + drop, fullH, off, b, 0xFF9DB6F0, 0f, 0f, 0f);
            }
        }
        String[] names = {"a", "b"};
        float[] speed = {8f, 17f};
        float[] top = {-30f, -48f};
        float[] base = {0.93f, 0.88f};
        for (int L = 0; L < 2; L++) {
            Tex.Img img = pic("menu_storm_" + names[L], "textures/gui/menu/storm_" + names[L] + ".png", kw, kh);
            if (img == null) continue;
            float[] c = {back[0], back[1] * (1 - 0.5f * L) + front[1] * 0.5f * L, back[2]};
            float[] a = new float[n];
            for (int i = 0; i < n; i++) a[i] = ceilingCover(i, n, lv) * base[L];
            float off = still ? 260f * (L + 1) : ct * speed[L] + 900f * L;
            float drop = (1f - lv) * -40f;
            strips(g, img, c, top[L] + drop, hUnits, off, a, 0xFFFFFFFF, 0f, 0f, 0f);
            if (fl > 0.02f) {
                float[] b = new float[n];
                for (int i = 0; i < n; i++) b[i] = lv * 0.45f * fl;
                strips(g, img, c, top[L] + drop, hUnits, off, b, 0xFFC4D6FF, 0f, 0f, 0f);
            }
        }
    }

    static float strike(GuiGraphics g, int width, int height, float t, long[] state) {
        long now = System.nanoTime();
        if (state[0] == 0) state[0] = now + (long) (3.5e9 + 3e9 * Lightning.hash((int) (now >> 20), 9));
        float since = (now - state[0]) / 1e9f;
        if (since < 0f) return 0f;
        if (since > 0.9f) {
            state[1]++;
            state[0] = now + (long) ((3.0 + 4.5 * Lightning.hash((int) state[1], 11)) * 1e9);
            return 0f;
        }
        int seed = (int) state[1] * 7 + (since > 0.18f && since < 0.3f ? 1 : 0);
        float fl = since < 0.08f ? since / 0.08f : since < 0.18f ? 0.35f : since < 0.3f ? 1f : Math.max(0f, 1f - (since - 0.3f) / 0.6f);
        float x0 = width * (0.55f + 0.3f * Lightning.hash(seed >> 1, 21));
        float x1 = x0 + width * (Lightning.hash(seed >> 1, 22) - 0.5f) * 0.25f;
        float alpha = Math.min(1f, fl * 1.1f);
        Lightning.bolt(g, x0, -4, x1, height * (0.45f + 0.2f * Lightning.hash(seed >> 1, 23)), 1f, seed, alpha, 0xFF8EC8FF, 0xFFD6E6FF, Math.max(1.2f, height / 190f), 1, 0.7f, 0.3f, 3);
        return fl;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float t = time();
        float u = u();
        if (mouseX != lastMx || mouseY != lastMy) {
            if (lastMx >= 0) mouseMoved = true;
            lastMx = mouseX;
            lastMy = mouseY;
        }
        curX = mouseMoved ? mouseX : -1;
        curY = mouseMoved ? mouseY : -1;
        float targetParX = mouseMoved ? mouseX / (float) Math.max(1, width) - 0.5f : 0f;
        float targetParY = mouseMoved ? mouseY / (float) Math.max(1, height) - 0.5f : 0f;
        float parallaxDamping = 1f - (float) Math.exp(-dt * 2.8f);
        sceneParX += (targetParX - sceneParX) * parallaxDamping;
        sceneParY += (targetParY - sceneParY) * parallaxDamping;
        parX = sceneParX;
        parY = sceneParY;
        int hoverBtn = mouseMoved ? plateAt(mouseX, mouseY) : -1;
        if (hoverBtn >= 0) {
            selected = hoverBtn;
            selectedByMouse = true;
        } else if (selectedByMouse && mouseMoved) {
            selected = -1;
            selectedByMouse = false;
        }
        int hoverIcon = mouseMoved ? iconAt(mouseX, mouseY) : -1;
        if (selected != soundPlate) {
            if (selected >= 0 && t > 0.9f) MenuAudio.hover(true, selected);
            soundPlate = selected;
        }
        if (hoverIcon != soundIcon) {
            if (hoverIcon >= 0 && t > 1.3f) MenuAudio.hover(false, hoverIcon);
            soundIcon = hoverIcon;
        }

        scene(g, width, height, t, new long[2]);

        int gs = Tex.guiScale();
        drawLogo(g, u, t, gs);

        int w = plateW();
        int h = plateH();
        Tex.Img plate = Tex.fitted("menu_plate", "textures/gui/menu/plate.png", w * gs, h * gs, false, 0, 0);
        for (int i = 0; i < NAMES.length; i++) {
            float want = i == selected ? 1f : 0f;
            float hoverBlend = 1f - (float) Math.exp(-dt * 12.5f);
            glow[i] += (want - glow[i]) * hoverBlend;
            if (Math.abs(want - glow[i]) < 0.0025f) glow[i] = want;
            float appear = Math.max(0f, Math.min(1f, (t - 0.35f - i * 0.06f) / 0.5f));
            float ease = 1f - (float) Math.pow(1f - appear, 3.0);
            int x = plateX();
            int y = plateY(i) + Math.round((1f - ease) * 10f * u);
            float gl = glow[i];
            boolean fx = !UiSettings.CALM.on();
            boolean isIn = want > 0.5f;
            if (isIn && !platePrev[i]) {
                enterAt[i] = t;
                enterX[i] = curX >= 0 && selectedByMouse ? curX : x + w / 2f;
            }
            platePrev[i] = isIn;
            float sp = fx ? HoverFx.spring(plateSpring[i], plateVel, i, want, dt) : want;
            plateSpring[i] = sp;
            float magWant = fx && isIn && curX >= 0 ? Math.max(-1f, Math.min(1f, (curX - (x + w / 2f)) / (w * 0.5f))) * 2.6f * u : 0f;
            plateMag[i] += (magWant - plateMag[i]) * (1f - (float) Math.exp(-dt * 12f));
            int lift = fx ? Math.round(1.7f * u * sp) : 0;
            if (fx && sp > 0.02f) Draw.shadow(g, x, y, w, h, 3, Math.max(3, Math.round(5 * u * sp)), 0.40f * sp * ease);
            x += Math.round(plateMag[i]);
            y -= lift;
            if (gl > 0.01f) Draw.glow(g, x, y, w, h, 3, i == CRATES ? 0xFFF5C04A : 0xFF7C9CE0, Math.max(3, Math.round(6 * u)), 0.34f * gl * ease);
            if (i == CRATES && gl < 0.99f) {
                // idle: a slow gold breath round the plate and a sweep of light every few seconds
                float breath = 0.5f + 0.5f * (float) Math.sin(t * 2.2f);
                Draw.glow(g, x, y, w, h, 3, 0xFFF5C04A, Math.max(3, Math.round(7 * u)), (0.16f + 0.14f * breath) * (1f - gl) * ease);
            }
            if (plate != null) plate.draw(g, x, y, w, h, Draw.rgba(0xFFFFFFFF, ease));
            else Draw.rrect(g, x, y, w, h, 3, Draw.rgba(0xFF05070C, ease));
            Tex.Img ink = Tex.fitted("menu_ink_" + NAMES[i], "textures/gui/menu/ink_" + NAMES[i] + ".png", w * gs, h * gs, false, 0, 0);
            if (ink != null) {
                ink.draw(g, x, y, w, h, Draw.rgba(0xFFFFFFFF, ease));
                if (i == CRATES) ink.draw(g, x, y, w, h, Draw.rgba(0xFFFFD27A, (0.35f + 0.25f * (float) Math.sin(t * 2.2f)) * ease));
                if (gl > 0.02f) ink.draw(g, x, y, w, h, Draw.rgba(i == CRATES ? 0xFFFFE6A8 : 0xFFDCE8FF, 0.55f * gl * ease));
            } else {
                Draw.boldCenter(g, NAMES[i].toUpperCase(), x + w / 2, y + h / 2 - 4, Draw.rgba(0xFFC8C0B4, ease));
            }
            if (gl > 0.02f && fx) {
                float since = t - enterAt[i];
                if (HoverFx.shine(g, x, y, w, h, since / 0.62f, u, ease, shineBand) && ink != null) {
                    g.enableScissor(Math.max(x, shineBand[0]), y, Math.min(x + w, shineBand[1]), y + h);
                    ink.draw(g, x, y, w, h, Draw.rgba(0xFFFFFFFF, 0.92f * ease));
                    g.disableScissor();
                }
                HoverFx.ripple(g, x, y, w, h, enterX[i], since / 0.7f, u, ease);
            }
            if (i == CRATES && fx) {
                float cyc = (t % 4.5f) / 0.9f;
                if (cyc < 1f && HoverFx.shine(g, x, y, w, h, cyc, u, ease * 0.7f, shineBand) && ink != null) {
                    g.enableScissor(Math.max(x, shineBand[0]), y, Math.min(x + w, shineBand[1]), y + h);
                    ink.draw(g, x, y, w, h, Draw.rgba(0xFFFFF1C8, 0.85f * ease));
                    g.disableScissor();
                }
            }
            if (gl > 0.02f) Draw.rrectLine(g, x, y, w, h, 3, Draw.rgba(i == CRATES ? 0xFFF5D08A : 0xFFA8BEEA, (0.68f + 0.10f * (float) Math.sin(t * 4f)) * gl * ease));
            if (gl > 0.02f && fx) {
                PlateFx.hover(g, x, y, w, h, t, gl * ease, u);
                HoverFx.underline(g, x, y, w, h, gl * ease, t);
                HoverFx.embers(g, x, y, w, h, t, gl * ease, u, i * 13);
            }
        }

        int ip = Math.round(2 * ICON_PIC * u);
        for (int i = 0; i < ICON_NAMES.length; i++) {
            float want = i == hoverIcon ? 1f : 0f;
            iconGlow[i] += (want - iconGlow[i]) * (1f - (float) Math.exp(-dt * 12f));
            if (Math.abs(want - iconGlow[i]) < 0.003f) iconGlow[i] = want;
            float appear = Math.max(0f, Math.min(1f, (t - 0.9f - i * 0.07f) / 0.5f));
            float gl = iconGlow[i];
            boolean fx = !UiSettings.CALM.on();
            boolean inI = want > 0.5f;
            if (inI && !iconPrev[i]) iconEnter[i] = t;
            iconPrev[i] = inI;
            float sp = fx ? HoverFx.spring(iconSpring[i], iconVel, i, want, dt) : want;
            iconSpring[i] = sp;
            int cx = iconCx(i);
            int cy = iconCy() - (fx ? Math.round(2.8f * u * sp) : 0);
            int size = Math.round(ip * (1f + 0.12f * sp));
            Tex.Img pic = Tex.fitted("menu_icon_" + ICON_NAMES[i], "textures/gui/menu/icon_" + ICON_NAMES[i] + ".png", ip * gs, ip * gs, false, 0, 0);
            if (gl > 0.01f) Lightning.bloom(g, cx, cy, ICON_R * u * 1.9f, 0xFFB4C8FF, 0.16f * gl * appear);
            if (gl > 0.02f && fx) HoverFx.roundFx(g, cx, cy, ICON_R * u * (1f + 0.12f * sp), t, t - iconEnter[i], gl * appear, u);
            if (pic != null) {
                pic.draw(g, cx - size / 2, cy - size / 2 + Math.round((1f - appear) * 6f * u), size, size, Draw.rgba(Draw.mix(0xFFFFFFFF, 0xFFE8F0FF, gl), appear));
                if (gl > 0.02f) pic.draw(g, cx - size / 2, cy - size / 2, size, size, Draw.rgba(0xFFC8DAFF, 0.22f * gl * appear));
                if (gl > 0.02f && !UiSettings.CALM.on()) iconAnim(g, i, cx, cy + Math.round((1f - appear) * 6f * u), size, gl, appear, dt, t, u, pic);
            }
            if (gl > 0.3f) {
                String tip = ICON_TIPS[i];
                int tipW = Draw.width(tip) + 15;
                float ta = Math.min(1f, (gl - 0.3f) / 0.5f);
                int tipX = Math.max(2, Math.min(width - tipW - 2, cx - tipW / 2));
                int tipY = cy - Math.round(ICON_R * u) - 19 + Math.round((1f - ta) * 4f);
                Draw.rrect(g, tipX, tipY, tipW, 15, 5, Draw.rgba(0xF0050914, ta));
                Draw.rrectLine(g, tipX, tipY, tipW, 15, 5, Draw.rgba(0xFF8090B4, 0.6f * ta));
                g.fill(tipX + 4, tipY + 4, tipX + 5, tipY + 11, Draw.rgba(0xFF9FC4FF, ta));
                Draw.text(g, tip, tipX + 9, tipY + 4, Draw.rgba(0xFFE8EEF8, ta));
            }
        }

        if (news.layout(width, height, plateRight(), newsBottom(), newsRight())) {
            float na = Math.max(0f, Math.min(1f, (t - 1.1f) / 0.6f));
            news.draw(g, mouseX, mouseY, dt, 1f - (1f - na) * (1f - na), t);
        }

        String name = minecraft.getUser().getName().toUpperCase(java.util.Locale.ROOT);
        String line = "VERSION " + ZeusMenu.MENU_VERSION + " - " + name + " ONLINE";
        Draw.textCenter(g, line, width / 2, height - 11, 0x99B4C0D8);
        String back = "Classic menu";
        boolean hotB = inClassic(mouseX, mouseY);
        Draw.textCenter(g, back, width / 2, height - 23, hotB ? 0xFFFFFFFF : 0x99B4C0D8);

        MenuClock.draw(g, width, height, u, Math.max(0f, Math.min(1f, (t - 0.6f) / 0.6f)));

        if (clickIdx >= 0) {
            float cp = (System.nanoTime() - clickAt) / 1e9f / CLICK_LENGTH;
            PlateFx.click(g, clickBox[0], clickBox[1], Math.round(clickBox[2]), Math.round(clickBox[3]), Math.round(clickBox[4]), Math.round(clickBox[5]), u, cp, width, height, clickIdx * 7 + clickKind * 3 + 1);
        }

        float in = Math.max(0f, 1f - t / 0.9f);
        if (in > 0.01f) g.fill(0, 0, width, height, Draw.rgba(0xFF000000, in * in));
    }

    private boolean inClassic(int mx, int my) {
        int cw = Draw.width("Classic menu") + 10;
        return mx >= width / 2 - cw / 2 && mx < width / 2 + cw / 2 && my >= height - 25 && my < height - 13;
    }

    private void drawLogo(GuiGraphics g, float u, float t, int gs) {
        int lw = Math.round(LOGO_W * u);
        int lh = Math.round(LOGO_H * u);
        Tex.Img logo = Tex.fitted("menu_logo", "textures/gui/menu/logo.png", lw * gs, lh * gs, false, 0, 0);
        float appear = Math.max(0f, Math.min(1f, (t - 0.15f) / 0.7f));
        if (logo == null || appear <= 0.01f) return;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 1.6f);
        int lx = ux(LOGO_X);
        int ly = uy(LOGO_Y) + Math.round((1f - appear) * (1f - appear) * 8f * u);
        float cx = lx + (MEDAL_CX - LOGO_X) * u;
        float cy = ly + (MEDAL_CY - LOGO_Y) * u;
        long nowNs = System.nanoTime();
        float ldt = logoHotAt == 0 ? 0f : Math.min(0.1f, (nowNs - logoHotAt) / 1e9f);
        logoHotAt = nowNs;
        boolean over = inLogo(curX, curY) && clickIdx < 0;
        logoHot += ((over ? 1f : 0f) - logoHot) * Math.min(1f, ldt * (over ? 11f : 7f));
        if (Math.abs((over ? 1f : 0f) - logoHot) < 0.004f) logoHot = over ? 1f : 0f;
        float hot = UiSettings.CALM.on() ? (over ? 1f : 0f) : logoHot;
        float sc = 1f + 0.04f * hot;
        int sw = Math.round(lw * sc);
        int sh = Math.round(lh * sc);
        int sx = Math.round(cx - (cx - lx) * sc);
        int sy = Math.round(cy - (cy - ly) * sc);
        float mr = MEDAL_R * u * sc;
        Lightning.bloom(g, cx, cy, MEDAL_R * u * (1.55f + 0.35f * hot), 0xFF7C9CE0, (0.05f + 0.05f * pulse + 0.20f * lastFlash + 0.16f * hot) * appear);
        logo.draw(g, sx, sy, sw, sh, Draw.rgba(0xFFFFFFFF, appear));
        MedallionFx.draw(g, cx, cy, mr, u, t, appear, logo, sx, sy, sw, sh);
        logoBox[0] = lx;
        logoBox[1] = ly;
        logoBox[2] = lw;
        logoBox[3] = lh;
        logoC[0] = cx;
        logoC[1] = cy;
        logoC[2] = MEDAL_R * u;
        if (hot > 0.01f) {
            float rr = mr * 1.07f;
            int segs = Perf.fast() ? 36 : 72;
            for (int i = 0; i < segs; i++) {
                float a0 = i * 6.2832f / segs;
                float a1 = (i + 1) * 6.2832f / segs;
                float k = 0.5f + 0.5f * (float) Math.cos((a0 - t * 1.7f)) ;
                float k2 = 0.5f + 0.5f * (float) Math.cos((a0 + t * 1.1f + 2.4f));
                float arc = Math.max((float) Math.pow(k, 7), (float) Math.pow(k2, 9));
                Draw.line(g, cx + (float) Math.cos(a0) * rr, cy + (float) Math.sin(a0) * rr, cx + (float) Math.cos(a1) * rr, cy + (float) Math.sin(a1) * rr,
                        Math.max(1f, 1.2f * u), Draw.rgba(0xFFB4CCFF, (0.20f + 0.65f * arc) * hot * appear));
            }
            logo.draw(g, sx, sy, sw, sh, Draw.rgba(0xFFFFFFFF, 0.14f * hot * appear));
            String tip = "Zeus settings";
            int tipW = Draw.width(tip) + 10;
            int tipX = Math.max(2, Math.min(width - tipW - 2, lx + lw / 2 - tipW / 2));
            int tipY = Math.min(height - 16, ly + lh + 2);
            Draw.rrect(g, tipX, tipY, tipW, 13, 4, Draw.rgba(0xFF050914, 0.94f * hot));
            Draw.rrectLine(g, tipX, tipY, tipW, 13, 4, Draw.rgba(0xFF7C8CB0, hot));
            Draw.text(g, tip, tipX + 5, tipY + 3, Draw.rgba(0xFFFFFFFF, hot));
        }
    }

    private void iconAnim(GuiGraphics g, int i, int cx, int cy, int size, float gl, float appear, float dt, float t, float u, Tex.Img still) {
        int frames = ICON_FRAMES[i];
        if (frames > 0) {
            int gs = Tex.guiScale();
            int fp = Math.min(222, Math.round(2 * ICON_PIC * u * gs));
            Tex.Img strip = Tex.fitted("menu_anim_" + ICON_NAMES[i], "textures/gui/menu/icon_anim_" + ICON_NAMES[i] + ".png", fp * frames, fp, false, 0, 0);
            if (strip == null) return;
            int f;
            if (i == 3) {
                f = Math.max(0, Math.min(frames - 1, Math.round(gl * (frames - 1))));
            } else {
                iconPhase[i] += dt * (i == 0 ? 6f + 16f * gl : 15f * gl);
                f = ((int) iconPhase[i]) % frames;
            }
            strip.drawPart(g, cx - size / 2, cy - size / 2, size, size, f * fp, 0, fp, fp, Draw.rgba(0xFFFFFFFF, Math.min(1f, gl * 1.8f) * appear));
            return;
        }
        still.draw(g, cx - size / 2, cy - size / 2, size, size, Draw.rgba(Draw.mix(0xFFFFFFFF, 0xFFE2EEFF, gl), appear));
        Thunder.ring(g, cx, cy, ICON_R * u * 0.96f, t, gl * appear, Math.max(0.6f, u));
    }

    private void play() {
        try {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        } catch (Throwable ignored) {
        }
    }

    private boolean inLogo(int x, int y) {
        if (logoBox[2] <= 0 || x < logoBox[0] || x >= logoBox[0] + logoBox[2] || y < logoBox[1] || y >= logoBox[1] + logoBox[3]) return false;
        float dx = x - logoC[0];
        float dy = y - logoC[1];
        if (dx * dx + dy * dy <= logoC[2] * logoC[2] * 1.12f) return true;
        return y >= logoC[1] + logoC[2] * 0.98f;
    }

    private void openSettings() {
        try {
            minecraft.setScreen(new ZeusScreen(this));
        } catch (RuntimeException e) {
            Zeus.LOG.warn("The Zeus settings could not be opened", e);
        }
    }

    private void trigger(int kind, int i) {
        if (clickIdx >= 0) return;
        play();
        MenuAudio.click();
        if (UiSettings.CALM.on() || Perf.fast()) {
            if (kind == 0) activate(i);
            else if (kind == 2) openSettings();
            else activateIcon(i);
            return;
        }
        clickKind = kind;
        clickIdx = i;
        clickAt = System.nanoTime();
        float u = u();
        if (kind == 0) {
            int w = plateW();
            int h = plateH();
            int x = plateX();
            int y = plateY(i);
            clickBox[0] = x + w / 2f;
            clickBox[1] = y + h / 2f;
            clickBox[2] = x;
            clickBox[3] = y;
            clickBox[4] = w;
            clickBox[5] = h;
        } else if (kind == 2) {
            int r = Math.round(logoC[2]);
            clickBox[0] = logoC[0];
            clickBox[1] = logoC[1];
            clickBox[2] = logoC[0] - r;
            clickBox[3] = logoC[1] - r;
            clickBox[4] = 2 * r;
            clickBox[5] = 2 * r;
        } else {
            int r = Math.round(ICON_R * u);
            clickBox[0] = iconCx(i);
            clickBox[1] = iconCy();
            clickBox[2] = iconCx(i) - r;
            clickBox[3] = iconCy() - r;
            clickBox[4] = 2 * r;
            clickBox[5] = 2 * r;
        }
        if (UiSettings.THUNDER.on() && !UiSettings.MENU_SOUNDS.on()) {
            try {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.35f, 0.32f));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void tick() {
        if (clickIdx >= 0 && (System.nanoTime() - clickAt) / 1e9f >= CLICK_GO) {
            int kind = clickKind;
            int i = clickIdx;
            clickIdx = -1;
            if (kind == 0) activate(i);
            else if (kind == 2) openSettings();
            else activateIcon(i);
        }
    }

    private void activate(int i) {
        try {
            switch (i) {
                case 0 -> minecraft.setScreen(new SelectWorldScreen(this));
                case 1 -> minecraft.setScreen(new JoinMultiplayerScreen(this));
                case 2 -> minecraft.setScreen(new ZeusScreen(this));
                case 3 -> minecraft.setScreen(new CratesScreen(this));
                case 4 -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
                case 5 -> minecraft.setScreen(new RealmsMainScreen(this));
                default -> minecraft.stop();
            }
        } catch (RuntimeException e) {
            Zeus.LOG.warn("Menu button {} failed", NAMES[i], e);
        }
    }

    private void activateIcon(int i) {
        try {
            switch (i) {
                case 0 -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()));
                case 1 -> minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options));
                case 2 -> minecraft.setScreen(new ZeusScreen(this));
                case 4 -> {
                    net.minecraft.util.Util.getPlatform().openUri(DISCORD);
                    Zeus.toast("Opening Discord", true);
                }
                default -> minecraft.setScreen(new ZeusCreditsScreen(this));
            }
        } catch (RuntimeException e) {
            Zeus.LOG.warn("Round button {} failed", ICON_NAMES[i], e);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (news.click(event.x(), event.y())) {
                play();
                return true;
            }
            int i = plateAt((int) event.x(), (int) event.y());
            if (i >= 0) {
                trigger(0, i);
                return true;
            }
            int ic = iconAt((int) event.x(), (int) event.y());
            if (ic >= 0) {
                trigger(1, ic);
                return true;
            }
            if (inLogo((int) event.x(), (int) event.y())) {
                trigger(2, 0);
                return true;
            }
            if (inClassic((int) event.x(), (int) event.y())) {
                UiSettings.MENU.set(false);
                Zeus.save();
                minecraft.setScreen(new TitleScreen());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (news.wheel(x, y, scrollY)) return true;
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) {
            selectedByMouse = false;
            selected = (selected + 1) % NAMES.length;
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            selectedByMouse = false;
            selected = selected < 0 ? NAMES.length - 1 : (selected + NAMES.length - 1) % NAMES.length;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            if (selected >= 0) trigger(0, selected);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
    }
}
