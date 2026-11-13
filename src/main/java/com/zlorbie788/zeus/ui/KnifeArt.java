package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class KnifeArt {

    public static final float S = 1.35f;
    public static final float HL = 11f * S;
    public static final float HW = 1.8f * S;
    public static final float HT = 1.4f * S;
    public static final float BL = 11f * S;
    public static final float BH = 2.4f * S;
    public static final float BT = 0.5f * S;
    public static final float PIN_X = 0.95f * S;
    public static final float PIN_Z = 0.75f * S;

    public static final String[] PRESETS = {"Crimson anodized", "Steel", "Zeus blue", "Gold", "Black ops", "Neon green", "Violet", "Rose gold", "Ice", "Rainbow"};
    public static final String[] SHAPES = {"Tanto", "Drop point", "Clip point", "Wharncliffe", "Spear point", "Sheepsfoot"};
    public static final String[] FINISHES = {"Same as the knife", "Satin steel", "Black coated", "Mirror", "Gold plated", "Blue titanium", "Rainbow titanium"};
    public static final String[] DESIGNS = {"Slotted", "Grooved", "Skeleton", "Plain"};

    private static final int[][] HANDLES = {
        {0xFFFF7A7A, 0xFFD72634, 0xFF7C101A, 0xFF340608},
        {0xFFDCE3EE, 0xFF9AA6B8, 0xFF566074, 0xFF232935},
        {0xFF86BAFF, 0xFF2F6FE0, 0xFF163E8E, 0xFF081A44},
        {0xFFFFEBA6, 0xFFE0B040, 0xFF8A6418, 0xFF3A2808},
        {0xFF626A78, 0xFF2C313B, 0xFF171A21, 0xFF08090C},
        {0xFFA2FFB6, 0xFF2BDB5A, 0xFF0F7F30, 0xFF04361A},
        {0xFFD8B0FF, 0xFF8C46E8, 0xFF4A1E90, 0xFF1C0A40},
        {0xFFFFDAD0, 0xFFE8A090, 0xFF9A5A50, 0xFF462220},
        {0xFFF2FFFF, 0xFFA0E4F8, 0xFF4C9CC0, 0xFF1C4660},
        {0xFFFFFFFF, 0xFF888888, 0xFF444444, 0xFF111111},
    };
    private static final int[][] BLADES = {
        {0xFFF4F7FB, 0xFFB8C1CE, 0xFF6C7584},
        {0xFFF4F7FB, 0xFFB8C1CE, 0xFF6C7584},
        {0xFFEAF3FF, 0xFFA2C4F0, 0xFF506EA4},
        {0xFFFFF4C8, 0xFFE8C46A, 0xFFA07A28},
        {0xFF6A7080, 0xFF2A2E38, 0xFF12141A},
        {0xFFEAFFF0, 0xFFB8D8C4, 0xFF6C8478},
        {0xFFF0E4FF, 0xFFC0A8E0, 0xFF7058A0},
        {0xFFFFEEE8, 0xFFE8B8A8, 0xFFA87868},
        {0xFFFFFFFF, 0xFFD0F0FF, 0xFF80B4D4},
        {0xFFFFFFFF, 0xFFCCCCCC, 0xFF777777},
    };
    private static final int[] GLOWS = {0xFFFF3A3A, 0xFF9CC4FF, 0xFF5AA2FF, 0xFFFFD45E, 0xFF9CC4FF, 0xFF3CFF7A, 0xFFB070FF, 0xFFFFA090, 0xFF8EF0FF, 0xFFFFFFFF};
    private static final int[][] FINISH_COLOURS = {
        null,
        {0xFFF4F7FB, 0xFFB8C1CE, 0xFF6C7584},
        {0xFF6A7080, 0xFF2A2E38, 0xFF12141A},
        {0xFFFFFFFF, 0xFFDCE4F0, 0xFF8494AE},
        {0xFFFFF4C8, 0xFFE8C46A, 0xFFA07A28},
        {0xFFD4E8FF, 0xFF5A8CE0, 0xFF2A4A90},
        null,
    };

    public static final class Look {
        public final int[] handle = new int[4];
        public final int[] blade = new int[3];
        public int glow;
        public int shape;
        public int design;
        public int finish;
        public boolean mirror;

        public boolean fine = true;
    }

    public static Look look(int preset, int shape, int finish, int design, float time) {
        Look l = new Look();
        preset = Math.max(0, Math.min(PRESETS.length - 1, preset));
        System.arraycopy(HANDLES[preset], 0, l.handle, 0, 4);
        System.arraycopy(BLADES[preset], 0, l.blade, 0, 3);
        l.glow = GLOWS[preset];
        if (preset == 9) {
            float h = (time * 0.18f) % 1f;
            l.handle[0] = hsv(h, 0.45f, 1f);
            l.handle[1] = hsv(h, 0.85f, 0.9f);
            l.handle[2] = hsv(h, 0.9f, 0.55f);
            l.handle[3] = hsv(h, 0.95f, 0.22f);
            l.glow = hsv(h, 0.8f, 1f);
        }
        if (finish > 0 && finish < FINISH_COLOURS.length) {
            int[] f = FINISH_COLOURS[finish];
            if (f != null) {
                System.arraycopy(f, 0, l.blade, 0, 3);
            } else {
                float h = (time * 0.12f + 0.3f) % 1f;
                l.blade[0] = hsv(h, 0.25f, 1f);
                l.blade[1] = hsv(h, 0.6f, 0.9f);
                l.blade[2] = hsv(h, 0.8f, 0.55f);
            }
        } else if (preset == 9) {
            float h = (time * 0.12f + 0.5f) % 1f;
            l.blade[0] = hsv(h, 0.2f, 1f);
            l.blade[1] = hsv(h, 0.5f, 0.92f);
            l.blade[2] = hsv(h, 0.7f, 0.6f);
        }
        l.finish = Math.max(0, Math.min(FINISH_COLOURS.length - 1, finish));
        l.shape = Math.max(0, Math.min(SHAPES.length - 1, shape));
        l.design = Math.max(0, Math.min(DESIGNS.length - 1, design));
        return l;
    }

    private static int hsv(float h, float s, float v) {
        float r = 0, g = 0, b = 0;
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    public static void build(Rig rig, Rig.T k, float a, float b, float c, Look look) {
        rig.box(k.move(-PIN_X, 0, -PIN_Z).rotZ(a), 0, HL / 2, 0, HW, HL, HT, handle(look, false), false);
        rig.box(k.move(PIN_X, 0, PIN_Z).rotZ(b), 0, HL / 2, 0, HW, HL, HT, handle(look, true), false);
        rig.box(k.rotZ(c), 0, BL / 2, 0, BH, BL, BT, blade(look), false);
        pin(rig, k, -PIN_X, look);
        pin(rig, k, PIN_X, look);
    }

    public static float[] tip(Rig.T k, float c) {
        return k.rotZ(c).point(0f, BL, 0f);
    }

    private static void pin(Rig rig, Rig.T k, float x, Look look) {
        Rig.Painter p = (g, c, face, w, h) -> {
            if (face != 4 && face != 5) {
                g.fill(0, 0, w, h, c.col(0xFF8C97AA));
                return;
            }
            g.fill(0, 0, w, h, c.col(0xFF222833));
            g.fill(1, 1, w - 1, h - 1, c.col(0xFFC8D0DE));
            g.fill(1, 1, w - 2, 2, c.col(0xFFFFFFFF));
            g.fill(w / 2, h / 2, w - 1, h - 1, c.col(0xFF8C97AA));
        };
        rig.box(k, x, 0.8f * S, 0f, 1.0f * S, 1.0f * S, 3.5f * S, p, false);
    }

    private static Rig.Painter handle(Look look, boolean back) {
        return (g, c, face, w, h) -> {
            int light = c.col(look.handle[0]);
            int mid = c.col(look.handle[1]);
            int dark = c.col(look.handle[2]);
            int deep = c.col(look.handle[3]);
            if (face == 4 || face == 5) {
                plate(g, c, look, w, h, light, mid, dark, deep);
            } else if (face == 0 || face == 1) {
                g.fill(0, 0, w, h, deep);
                g.fill(1, 0, w - 1, h, face == 0 ? dark : mid);
                g.fill(1, 0, 2, h, light);
                for (int y = 8; y < h - 4; y += 6) g.fill(1, y, w - 1, y + 1, deep);
            } else {
                g.fill(0, 0, w, h, dark);
                g.fill(1, 1, w - 1, h - 1, mid);
                if (face == 2) g.fill(w / 2 - 1, h / 2 - 1, w / 2 + 1, h / 2 + 1, deep);
            }
        };
    }

    private static void plate(GuiGraphics g, Rig.Ctx c, Look look, int w, int h, int light, int mid, int dark, int deep) {
        g.fill(0, 0, w, h, deep);
        g.fill(1, 1, w - 1, h - 1, mid);
        g.fill(1, 1, 2, h - 1, light);
        g.fill(w - 2, 1, w - 1, h - 1, dark);
        int sheen = c.col(Draw.mix(look.handle[1], look.handle[0], 0.55f));
        g.fill(2, 1, 3, h - 1, sheen);
        g.fill(1, 1, w - 1, 2, light);
        int slotL = Math.max(2, Math.round(w * 0.28f));
        int slotR = w - slotL;
        int third = Math.max(1, w / 3);
        switch (look.design) {
            case 1 -> {
                for (int y = Math.round(h * 0.17f); y < h * 0.9f; y += 3) {
                    g.fill(1, y, w - 1, y + 1, deep);
                    g.fill(1, y + 1, w - 1, y + 2, light);
                }
            }
            case 2 -> {
                float[][] holes = {{0.13f, 0.30f}, {0.36f, 0.53f}, {0.59f, 0.76f}};
                for (float[] hole : holes) {
                    int y1 = Math.round(h * hole[0]);
                    int y2 = Math.round(h * hole[1]);
                    g.fill(slotL, y1, slotR, y2, deep);
                    g.fill(slotL + 1, y1 - 1, slotR - 1, y1, deep);
                    g.fill(slotL + 1, y2, slotR - 1, y2 + 1, deep);
                    g.fill(slotL, y2 - 1, slotR, y2, dark);
                }
            }
            case 3 -> {
                int y = Math.round(h * 0.5f);
                g.fill(third + 1, y, w - 2, y + 2, light);
                g.fill(2, y + 2, w - third - 1, y + 4, light);
                g.fill(third + 1, y + 4, w - 2, y + 6, light);
                g.fill(w / 2, Math.round(h * 0.18f), w / 2 + 1, Math.round(h * 0.45f), dark);
            }
            default -> {
                int y1 = Math.round(h * 0.16f);
                int y2 = Math.round(h * 0.60f);
                g.fill(slotL, y1, slotR, y2, deep);
                g.fill(slotL + 1, y1 - 1, slotR - 1, y1, deep);
                g.fill(slotL + 1, y2, slotR - 1, y2 + 1, deep);
                g.fill(slotR - 1, y1, slotR, y2, dark);
                for (float f : new float[] {0.66f, 0.74f}) {
                    int y = Math.round(h * f);
                    g.fill(w / 2 - 1, y, w / 2 + 1, y + 2, deep);
                    g.fill(w / 2 - 1, y + 2, w / 2 + 1, y + 3, light);
                }
                for (int k = 0; k < 4; k++) {
                    int y = Math.round(h * 0.80f) + k * 3;
                    g.fill(1, y, 1 + third, y + 1, deep);
                    g.fill(1 + third, y + 1, 1 + 2 * third, y + 2, deep);
                    g.fill(1 + 2 * third, y + 2, w - 1, y + 3, deep);
                    g.fill(1, y + 1, 1 + third, y + 2, light);
                    g.fill(1 + third, y + 2, 1 + 2 * third, y + 3, light);
                }
            }
        }
        int pc = w / 2;
        g.fill(pc - 2, 2, pc + 2, 6, c.col(0xFF222833));
        g.fill(pc - 1, 3, pc + 1, 5, c.col(0xFFD8DFEA));
        g.fill(pc - 1, h - 7, pc + 1, h - 3, deep);
        g.fill(pc - 1, h - 3, pc + 1, h - 2, light);
        int gy = 3 + Math.round(c.glint * (h - 12));
        g.fill(1, gy, w - 1, gy + 2, c.raw(0xFFFFFFFF, 0.2f));
    }

    public static float[] profile(int shape, float t) {
        float s = 0f;
        float e = BH;
        switch (shape) {
            case 0 -> {
                if (t > 0.78f) e = BH * (1f - (t - 0.78f) / 0.22f);
                if (t > 0.90f) s = BH * 0.10f * ((t - 0.90f) / 0.10f);
            }
            case 1 -> {
                if (t > 0.62f) s = BH * 0.52f * (float) Math.pow((t - 0.62f) / 0.38f, 1.7);
                if (t > 0.75f) e = BH * (1f - 0.5f * (float) Math.pow((t - 0.75f) / 0.25f, 2.0));
            }
            case 2 -> {
                if (t > 0.58f) s = BH * 0.62f * (float) Math.pow((t - 0.58f) / 0.42f, 1.25);
                if (t > 0.80f) e = BH * (1f - 0.34f * (float) Math.pow((t - 0.80f) / 0.20f, 2.0));
            }
            case 3 -> {
                if (t > 0.55f) s = BH * 0.98f * (float) Math.pow((t - 0.55f) / 0.45f, 2.0);
            }
            case 4 -> {
                if (t > 0.5f) {
                    float k = (float) Math.pow((t - 0.5f) / 0.5f, 1.4);
                    s = BH * 0.5f * k;
                    e = BH * (1f - 0.5f * k);
                }
            }
            default -> {
                if (t > 0.66f) s = BH * 0.98f * (float) Math.pow((t - 0.66f) / 0.34f, 1.8);
            }
        }
        if (e < s) e = s;
        return new float[] {s, e};
    }

    private static Rig.Painter blade(Look look) {
        return (g, c, face, w, h) -> {
            int light = c.col(look.blade[0]);
            int mid = c.col(look.blade[1]);
            int dark = c.col(look.blade[2]);
            if (face == 4 || face == 5) {
                boolean spineRight = face == 5;
                float gl = c.glint;
                final int R = look.fine ? 2 : 1;
                g.pose().pushMatrix();
                g.pose().scale(1f / R, 1f / R);
                int W = w * R;
                int H = h * R;
                int[] lo = new int[H];
                int[] hi = new int[H];
                for (int r = 0; r < H; r++) {
                    float[] pe = profile(look.shape, (r + 0.5f) / H);
                    int a = Math.round(pe[0] * Rig.K * R);
                    int b = Math.round(pe[1] * Rig.K * R);
                    lo[r] = spineRight ? W - b : a;
                    hi[r] = spineRight ? W - a : b;
                    if (b <= a) hi[r] = lo[r];
                }
                int edgeMax = 3 * R;
                for (int r0 = 0; r0 < H; ) {
                    int r1 = r0;
                    while (r1 + 1 < H && lo[r1 + 1] == lo[r0] && hi[r1 + 1] == hi[r0]) r1++;
                    int x1 = lo[r0];
                    int x2 = hi[r0];
                    if (x2 > x1) {
                        boolean tang = (r0 + 0.5f) / H < 0.12f;
                        g.fill(x1, r0, x2, r1 + 1, tang ? dark : mid);
                        int edgeW = Math.min(edgeMax, x2 - x1);
                        if (spineRight) {
                            g.fill(x1, r0, x1 + edgeW, r1 + 1, light);
                            g.fill(x2 - R, r0, x2, r1 + 1, dark);
                        } else {
                            g.fill(x2 - edgeW, r0, x2, r1 + 1, light);
                            g.fill(x1, r0, x1 + R, r1 + 1, dark);
                        }
                    }
                    r0 = r1 + 1;
                }
                int gr = Math.max(0, Math.min(H - 1, Math.round(c.glint * H)));
                if (hi[gr] > lo[gr]) g.fill(lo[gr], Math.max(0, gr - 2 * R), hi[gr], Math.min(H, gr + 2 * R), c.raw(0xFFFFFFFF, 0.45f));
                int px = W / 2;
                g.fill(px - R, 2 * R, px + R, 4 * R, dark);
                int fx = spineRight ? W - Math.round(BH * 0.38f * Rig.K * R) : Math.round(BH * 0.38f * Rig.K * R);
                g.fill(fx, Math.round(H * 0.2f), fx + R, Math.round(H * 0.7f), dark);
                g.pose().popMatrix();
            } else if (face == 0 || face == 1) {
                boolean edge = face == 1;
                for (int r = 0; r < h; r++) {
                    float t = (r + 0.5f) / h;
                    float[] pe = profile(look.shape, t);
                    boolean onSide = edge ? pe[1] >= BH - 0.08f : pe[0] <= 0.08f;
                    if (!onSide) continue;
                    g.fill(0, r, w, r + 1, edge ? light : dark);
                    if (edge) g.fill(0, r, 1, r + 1, mid);
                }
            }
        };
    }

    public static final String[] KPRESETS = {"Voidbloom", "Crimson claw", "Frost", "Toxic", "Gold rush", "Zeus blue", "Rainbow"};
    public static final String[] KCURVES = {"Claw", "Wide hook", "Talon"};

    private static final int[][] KHANDLES = {
        {0xFFE9B0FF, 0xFFB45AF2, 0xFF3C1A66, 0xFF140724},
        {0xFFFFB0B0, 0xFFE63A4A, 0xFF541420, 0xFF1E050A},
        {0xFFE6FAFF, 0xFF78C8F4, 0xFF204C78, 0xFF09192A},
        {0xFFCEFFB8, 0xFF58E468, 0xFF185C28, 0xFF072212},
        {0xFFFFF2B8, 0xFFEABC40, 0xFF644816, 0xFF261A06},
        {0xFFB8D4FF, 0xFF4C84F2, 0xFF1A3474, 0xFF090F2A},
        {0xFFFFFFFF, 0xFF888888, 0xFF444444, 0xFF111111},
    };

    private static final int[][] KBLADES = {
        {0xFFFF9EEA, 0xFFE040C4, 0xFF6C1E94},
        {0xFFFFC4AC, 0xFFFF4C4C, 0xFF8C1830},
        {0xFFFFFFFF, 0xFF92E2FF, 0xFF3272C4},
        {0xFFF2FFB4, 0xFF62FF62, 0xFF128444},
        {0xFFFFFFD4, 0xFFFFCA44, 0xFFB46414},
        {0xFFE4F2FF, 0xFF5AA4FF, 0xFF2444C4},
        {0xFFFFFFFF, 0xFFCCCCCC, 0xFF777777},
    };
    private static final int[] KGLOWS = {0xFFE444FF, 0xFFFF4A66, 0xFF72D2FF, 0xFF52FF74, 0xFFFFC844, 0xFF5AA2FF, 0xFFFFFFFF};

    public static final float Q = 1.2f;
    private static final float KX0 = -13.9f, KX1 = 3.0f, KY0 = -3.0f, KY1 = 21.5f;
    private static final float KHY = 8.4f;
    private static final float[] KR = {8.0f, 9.5f, 6.5f};
    private static final float[] KTM = {125f, 105f, 150f};
    private static final float[] KHW = {2.0f, 2.3f, 1.75f};

    public static String nameOf(int key) {
        if (key < KR.length) return "Karambit";
        return SMODELS[Math.min(SMODELS.length - 1, key - KR.length)].name;
    }

    public static final float KGRIP = 5.1f * Q;

    public static Look kLook(int preset, int curve, float time) {
        Look l = new Look();
        preset = Math.max(0, Math.min(KPRESETS.length - 1, preset));
        System.arraycopy(KHANDLES[preset], 0, l.handle, 0, 4);
        System.arraycopy(KBLADES[preset], 0, l.blade, 0, 3);
        l.glow = KGLOWS[preset];
        if (preset == KPRESETS.length - 1) {
            float h = (time * 0.16f) % 1f;
            l.handle[0] = hsv(h, 0.40f, 1f);
            l.handle[1] = hsv(h, 0.80f, 0.95f);
            l.handle[2] = hsv(h, 0.90f, 0.45f);
            l.handle[3] = hsv(h, 0.95f, 0.16f);
            float h2 = (h + 0.18f) % 1f;
            l.blade[0] = hsv(h2, 0.35f, 1f);
            l.blade[1] = hsv(h2, 0.85f, 1f);
            l.blade[2] = hsv(h2, 0.95f, 0.55f);
            l.glow = hsv(h2, 0.8f, 1f);
        }
        l.shape = Math.max(0, Math.min(KCURVES.length - 1, curve));
        return l;
    }

    public static String[] allKnives() {
        String[] st = straightNames();
        String[] all = new String[2 + st.length];
        all[0] = "Butterfly knife";
        all[1] = "Karambit";
        System.arraycopy(st, 0, all, 2, st.length);
        return all;
    }

    public static String[] finishNames(int type) {
        return type == 0 ? PRESETS : type == 1 ? KPRESETS : SFINISHES;
    }

    private static final Rig SHOW_RIG = new Rig();

    public static void showcase(GuiGraphics g, int type, int finish, int bx, int by, int bw, int bh, float time, boolean fine) {
        Rig rig = SHOW_RIG;
        rig.clear();
        rig.alpha(1f);
        Look look;
        Rig.T k;
        if (type == 0) {
            look = look(finish, 0, 0, 0, time);
            look.fine = fine;
            k = Rig.T.rz(-0.85f).rotY(-0.55f).rotX(0.12f);
            build(rig, k, 0f, TAU_SHOW, (float) Math.PI, look);
        } else {
            look = type == 1 ? kLook(finish, 0, time) : sLook(finish, type - 2, time);
            look.fine = fine;
            k = Rig.T.rz(type == 1 ? 0.40f : (float) Math.PI - 1.15f).rotY(-0.45f).rotX(0.10f);
            buildKarambit(rig, k, look);
        }
        float[] e = rig.extent();
        float ew = Math.max(1f, e[2] - e[0]), eh = Math.max(1f, e[3] - e[1]);
        float unit = Math.min(bw / ew, bh / eh) * 0.94f;
        g.pose().pushMatrix();
        g.pose().translate(bx + bw / 2f - (e[0] + e[2]) / 2f * unit, by + bh / 2f - (e[1] + e[3]) / 2f * unit);
        rig.flush(g, unit);
        g.pose().popMatrix();
    }

    private static final float TAU_SHOW = (float) (Math.PI * 2.0);

    public static float[] kTip(Rig.T k, int curve) {
        return kAt(k, curve, 1f);
    }

    public static float[] kAt(Rig.T k, int curve, float t) {
        curve = Math.max(0, Math.min(KR.length + SMODELS.length - 1, curve));
        if (curve >= KR.length) {
            Straight m = SMODELS[curve - KR.length];
            float y0 = m.hl + (m.guard ? 0.9f : 0f);
            if (m.bend == 0f) return k.point(0f, (y0 + m.bl * t) * Q, 0f);
            float rm = Math.abs(m.bend);
            float th = (float) Math.toRadians(m.sweep) * t;
            float sgn = m.bend > 0f ? 1f : -1f;
            return k.point(sgn * (-rm + rm * (float) Math.cos(th)) * Q, (y0 + rm * (float) Math.sin(th)) * Q, 0f);
        }
        float r = KR[curve];
        float th = (float) Math.toRadians(KTM[curve]) * t;
        return k.point((-r + r * (float) Math.cos(th)) * Q, (KHY + r * (float) Math.sin(th)) * Q, 0f);
    }

    public static void buildKarambit(Rig rig, Rig.T k, Look look) {
        int layers = look.fine ? 3 : 2;
        int cell = look.fine ? 2 : 4;
        KSet set = kSet(look.shape, cell, layers);
        float[] bd = bounds(look.shape);
        float cx = (bd[0] + bd[1]) / 2f * Q;
        float cy = (bd[2] + bd[3]) / 2f * Q;
        float w = (bd[1] - bd[0]) * Q;
        float h = (bd[3] - bd[2]) * Q;
        boolean faceOn = Math.abs(k.r[8]) > 0.5f;
        int near = k.r[8] < 0f ? 1 : -1;
        float step = 0.17f;
        for (int d = layers; d >= 0; d--) {
            int sides = d == 0 ? 1 : 2;
            for (int sd = 0; sd < sides; sd++) {
                int side = d == 0 ? 0 : (sd == 0 ? 1 : -1);
                if (faceOn && d > 0 && side != near) continue;
                KLayer lay = faceOn ? set.band[d] : set.full[d];
                if (lay.rects.length == 0) continue;
                rig.box(k, cx, cy, side * (0.05f + d * step), w, h, 0.10f, kPlate(look, lay, d, layers, cell), false);
            }
        }
    }

    private static Rig.Painter kPlate(Look look, KLayer lay, int depth, int layers, int cell) {
        return (g, c, face, w, h) -> {
            if (face != 4 && face != 5) return;
            int[] pal = kPalette(look, c, depth, layers);
            boolean mirror = face == 4;
            int[] r = lay.rects;
            for (int i = 0; i < r.length; i += 5) {
                int x1 = Math.min(w, r[i] * cell);
                int x2 = Math.min(w, r[i + 2] * cell);
                int y1 = Math.min(h, r[i + 1] * cell);
                int y2 = Math.min(h, r[i + 3] * cell);
                if (mirror) {
                    int a = w - x2;
                    x2 = w - x1;
                    x1 = a;
                }
                g.fill(x1, y1, x2, y2, pal[r[i + 4]]);
            }
        };
    }

    private static int[] kPalette(Look look, Rig.Ctx c, int depth, int layers) {
        float lift = depth == 0 ? -0.30f : 0.10f * depth;
        int[] src = {0, look.handle[3], look.handle[2], Draw.mix(look.handle[1], look.handle[2], 0.45f), look.handle[1], look.handle[0],
            0, look.blade[2], look.blade[1], look.blade[0], Draw.mix(look.blade[1], look.blade[2], 0.5f), 0, Draw.darken(look.blade[2], 0.45f)};
        int[] p = new int[48];
        for (int i = 0; i < src.length; i++) {
            if (i == 0 || i == 6 || i == 11) continue;
            int col = src[i];
            col = lift < 0 ? Draw.darken(col, -lift) : Draw.lighten(col, lift);
            p[i] = c.col(col);
            p[i + 16] = c.col(Draw.lighten(col, 0.28f));
            p[i + 32] = c.col(Draw.darken(col, 0.32f));
        }
        p[6] = c.raw(0xFF000000 | (look.glow & 0xFFFFFF), 0.95f);
        p[11] = c.raw(0xFF000000 | (Draw.mix(look.glow, 0xFFFFFFFF, 0.6f) & 0xFFFFFF), 1f);
        return p;
    }

    private static final class KLayer {
        int[] rects = new int[0];
    }

    private static final class KSet {
        KLayer[] full;
        KLayer[] band;
    }

    public static int kRects(int curve, boolean fine) {
        return kSet(curve, fine ? 2 : 4, fine ? 3 : 2).full[0].rects.length / 5;
    }

    public static int kRectsAll(int curve, boolean fine) {
        KSet s = kSet(curve, fine ? 2 : 4, fine ? 3 : 2);
        int n = 0;
        for (KLayer l : s.full) n += l.rects.length / 5;
        return n;
    }

    private static KSet kSet(int curve, int cell, int layers) {
        curve = Math.max(0, Math.min(KR.length + SMODELS.length - 1, curve));
        int ci = (cell <= 2 ? 0 : 2) + (layers >= 3 ? 0 : 1);
        KSet m = KSETS[curve][ci];
        if (m != null) return m;
        float[] bd = bounds(curve);
        int wpx = Math.round((bd[1] - bd[0]) * Q * Rig.K);
        int hpx = Math.round((bd[3] - bd[2]) * Q * Rig.K);
        int cols = (wpx + cell - 1) / cell;
        int rows = (hpx + cell - 1) / cell;
        float per = cell / (Rig.K * Q);
        int[][] code = new int[rows][cols];
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) code[j][i] = classify(curve, bd[0] + (i + 0.5f) * per, bd[2] + (j + 0.5f) * per);
        }
        float[][] dist = new float[rows][cols];
        float big = 1e6f;
        for (int j = 0; j < rows; j++) for (int i = 0; i < cols; i++) dist[j][i] = code[j][i] == 0 ? 0f : big;
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                if (code[j][i] == 0) continue;
                float dd = dist[j][i];
                dd = Math.min(dd, (j == 0 || i == 0 ? 1f : dist[j - 1][i - 1] + 1.414f));
                dd = Math.min(dd, (j == 0 ? 1f : dist[j - 1][i] + 1f));
                dd = Math.min(dd, (i == 0 ? 1f : dist[j][i - 1] + 1f));
                dd = Math.min(dd, (j == 0 || i == cols - 1 ? 1f : dist[j - 1][i + 1] + 1.414f));
                dist[j][i] = dd;
            }
        }
        for (int j = rows - 1; j >= 0; j--) {
            for (int i = cols - 1; i >= 0; i--) {
                if (code[j][i] == 0) continue;
                float dd = dist[j][i];
                dd = Math.min(dd, (j == rows - 1 || i == cols - 1 ? 1f : dist[j + 1][i + 1] + 1.414f));
                dd = Math.min(dd, (j == rows - 1 ? 1f : dist[j + 1][i] + 1f));
                dd = Math.min(dd, (i == cols - 1 ? 1f : dist[j][i + 1] + 1f));
                dd = Math.min(dd, (j == rows - 1 || i == 0 ? 1f : dist[j + 1][i - 1] + 1.414f));
                dist[j][i] = dd;
            }
        }
        boolean[][][] keep = new boolean[layers + 2][rows][cols];
        for (int d = 0; d <= layers; d++) {
            float f = d / (float) layers;
            for (int j = 0; j < rows; j++) {
                for (int i = 0; i < cols; i++) {
                    int cd = code[j][i];
                    if (cd == 0) continue;
                    float du = dist[j][i] * per;
                    boolean inner = (cd & 64) != 0;
                    int base = cd & 63;
                    float thr;
                    if (base == 4 || base == 5) thr = 0.62f * (1f - (float) Math.sqrt(Math.max(0f, 1f - 0.92f * f * f)));
                    else if (base >= 1 && base <= 3) thr = 1.65f * (1f - (float) Math.sqrt(Math.max(0f, 1f - 0.92f * f * f)));
                    else thr = inner ? 1.05f * f : 0.30f * f;
                    keep[d][j][i] = d == 0 || du >= thr;
                }
            }
        }
        m = new KSet();
        m.full = new KLayer[layers + 1];
        m.band = new KLayer[layers + 1];
        for (int d = 0; d <= layers; d++) {
            int[][] full = new int[rows][cols];
            int[][] band = new int[rows][cols];
            for (int j = 0; j < rows; j++) {
                for (int i = 0; i < cols; i++) {
                    if (!keep[d][j][i]) continue;
                    int cd = code[j][i] & 63;
                    int out = cd;
                    if (d == 0) {
                        boolean edge = j == 0 || i == 0 || j == rows - 1 || i == cols - 1
                            || code[j - 1][i] == 0 || code[j + 1][i] == 0 || code[j][i - 1] == 0 || code[j][i + 1] == 0;
                        if (edge) out = (code[j][i] & 64) != 0 ? 11 : 6;
                    }
                    if (d > 0 && out != 6 && out != 11 && j > 0 && i > 0 && j < rows - 1 && i < cols - 1) {
                        float gx = dist[j][i + 1] - dist[j][i - 1];
                        float gy = dist[j + 1][i] - dist[j - 1][i];
                        float sl = gx + gy;
                        if (sl > 0.9f) out += 16;
                        else if (sl < -0.9f) out += 32;
                    }
                    full[j][i] = out;
                    if (d == layers || !keep[d + 1][j][i]) band[j][i] = out;
                }
            }
            m.full[d] = kRectsOf(full, rows, cols);
            m.band[d] = kRectsOf(band, rows, cols);
        }
        KSETS[curve][ci] = m;
        return m;
    }

    private static KLayer kRectsOf(int[][] out, int rows, int cols) {
        java.util.ArrayList<int[]> list = new java.util.ArrayList<>();
        java.util.ArrayList<int[]> open = new java.util.ArrayList<>();
        for (int j = 0; j < rows; j++) {
            java.util.ArrayList<int[]> now = new java.util.ArrayList<>();
            for (int i = 0; i < cols; ) {
                int cd = out[j][i];
                if (cd == 0) {
                    i++;
                    continue;
                }
                int e = i;
                while (e + 1 < cols && out[j][e + 1] == cd) e++;
                int[] run = null;
                for (int[] o : open) {
                    if (o[0] == i && o[2] == e + 1 && o[4] == cd) {
                        run = o;
                        break;
                    }
                }
                if (run != null) {
                    run[3] = j + 1;
                } else {
                    run = new int[] {i, j, e + 1, j + 1, cd};
                    list.add(run);
                }
                now.add(run);
                i = e + 1;
            }
            open = now;
        }
        KLayer l = new KLayer();
        l.rects = new int[list.size() * 5];
        for (int n = 0; n < list.size(); n++) System.arraycopy(list.get(n), 0, l.rects, n * 5, 5);
        return l;
    }

    private static int kClass(int curve, float x, float y) {
        float rr = (float) Math.sqrt(x * x + y * y);
        if (rr <= 2.5f && rr >= 1.2f) return x + y < -0.4f ? 5 : 4;
        if (y >= 1.8f && y <= KHY + 0.2f && x >= -1.7f && x <= 1.7f) {
            float p1 = (x + 0.0f) * (x + 0.0f) + (y - 3.0f) * (y - 3.0f);
            float p2 = (x + 0.0f) * (x + 0.0f) + (y - 7.1f) * (y - 7.1f);
            if (p1 < 0.16f || p2 < 0.16f) return 5;
            if (x > -1.3f && x < -0.8f) return 3;
            int gx = (int) Math.floor((x + 1.7f) / 0.85f);
            int gy = (int) Math.floor((y - 1.8f) / 0.85f);
            if (x >= -0.8f) return ((gx + gy) & 1) == 1 ? 1 : 2;
            return x < 0.2f ? 2 : 1;
        }
        float r = KR[curve];
        float tm = (float) Math.toRadians(KTM[curve]);
        float dx = x + r;
        float dy = y - KHY;
        float th = (float) Math.atan2(dy, dx);
        if (th < 0f || th > tm) return 0;
        float t = th / tm;
        float hw = KHW[curve] * (1f + 0.3f * (float) Math.sin(Math.PI * Math.min(1f, t * 1.1f))) * (1f - (float) Math.pow(t, 1.7));
        float d = (float) Math.sqrt(dx * dx + dy * dy) - r;
        if (Math.abs(d) > hw) return 0;
        int cd;
        if (Math.abs(d + hw * 0.1f) < hw * 0.09f && t > 0.12f && t < 0.78f) cd = 12;
        else if (d > hw * 0.5f) cd = 7;
        else if (d < -hw * 0.4f) cd = 9;
        else if (t > 0.88f) cd = 10;
        else if (t < 0.15f) cd = 7;
        else cd = t > 0.35f && t < 0.7f ? 9 : 8;
        return d < 0f ? cd | 64 : cd;
    }

    public static final String[] SFINISHES = {"Stainless", "Blue steel", "Fade", "Slaughter", "Crimson web", "Doppler", "Case hardened", "Night",
        "Forest", "Gamma Doppler", "Gold", "Damascus", "Emerald", "Obsidian", "Copper", "Ruby", "Tiger", "Rainbow"};

    private static final int[][] SFIN = {
        {0xFF8A8F98, 0xFF4A4F58, 0xFF24272E, 0xFF0E1014, 0xFFF4F7FB, 0xFFB7C0CC, 0xFF6B7684, 0xFFE8F1FF},
        {0xFF9DB6D8, 0xFF34527E, 0xFF182A48, 0xFF080F1C, 0xFFC9DCF5, 0xFF5B7DAE, 0xFF26426E, 0xFF9CC4FF},
        {0xFFFFD6A8, 0xFFE0703A, 0xFF4A2418, 0xFF1C0C08, 0xFFFFE96B, 0xFFFF8A3C, 0xFFD2308C, 0xFFFFB070},
        {0xFFFFC09A, 0xFFCC3A1C, 0xFF4E140C, 0xFF1E0806, 0xFFFFC27A, 0xFFE2481E, 0xFF8A1608, 0xFFFF7A3A},
        {0xFFE8A0A0, 0xFF8E1620, 0xFF3C0A10, 0xFF18040A, 0xFFE9D0D0, 0xFFA86060, 0xFF5A1A22, 0xFFFF4A5A},
        {0xFFD6C0FF, 0xFF7A4EE0, 0xFF2C1A66, 0xFF100828, 0xFFFFB0E8, 0xFFB04AE0, 0xFF4A2A98, 0xFFD070FF},
        {0xFFC8D8FF, 0xFF3A62C8, 0xFF1A2E6E, 0xFF0A1230, 0xFFFFE08A, 0xFF5C88E6, 0xFF2A4AA8, 0xFF8AB4FF},
        {0xFF6A6E78, 0xFF30323A, 0xFF16171C, 0xFF08090B, 0xFF8A8F9C, 0xFF3E414B, 0xFF1B1C22, 0xFF9AA4C0},
        {0xFFB8D8A0, 0xFF4C7A3A, 0xFF22381A, 0xFF0C1608, 0xFFD2E6B8, 0xFF7C9E5C, 0xFF3A5428, 0xFF9CE070},
        {0xFFB8FFE8, 0xFF22C894, 0xFF0E5440, 0xFF04201A, 0xFFC8FFF0, 0xFF3AE0B4, 0xFF108070, 0xFF5CFFD0},
        {0xFFFFF0B8, 0xFFD2A21E, 0xFF5C4208, 0xFF241A04, 0xFFFFF6C4, 0xFFF0C23A, 0xFFA27010, 0xFFFFD45E},
        {0xFFE2C79A, 0xFF8A6A3A, 0xFF3C2A14, 0xFF18100A, 0xFFEAEAEA, 0xFF9A9AA6, 0xFF50505C, 0xFFDDDDEE},
        {0xFF8AE0B0, 0xFF14724A, 0xFF06301E, 0xFF02140C, 0xFFC8FFE0, 0xFF2AD08A, 0xFF0A5C3A, 0xFF5CFFB0},
        {0xFF4A4A58, 0xFF23232E, 0xFF101018, 0xFF06060A, 0xFF9A9AB8, 0xFF43435C, 0xFF14141E, 0xFF8A7CFF},
        {0xFFE8A878, 0xFFB06432, 0xFF4A2814, 0xFF1C0E06, 0xFFFFD0A8, 0xFFD07C3C, 0xFF6E3A18, 0xFFFFA860},
        {0xFFFFA0B0, 0xFFB01838, 0xFF4A081A, 0xFF1C0208, 0xFFFFC0CC, 0xFFE0284C, 0xFF7A0A22, 0xFFFF4A6A},
        {0xFFF0C860, 0xFF3A2A10, 0xFF1A1206, 0xFF0A0602, 0xFFFFF0B0, 0xFFE0A020, 0xFF6A4408, 0xFFFFC03A},
    };

    public static Look sLook(int finish, int model, float time) {
        Look l = new Look();
        finish = Math.max(0, Math.min(SFINISHES.length - 1, finish));
        if (finish == SFINISHES.length - 1) {
            float h = (time * 0.14f) % 1f;
            l.handle[0] = hsv(h, 0.40f, 1f);
            l.handle[1] = hsv(h, 0.80f, 0.95f);
            l.handle[2] = hsv(h, 0.90f, 0.45f);
            l.handle[3] = hsv(h, 0.95f, 0.16f);
            float h2 = (h + 0.22f) % 1f;
            l.blade[0] = hsv(h2, 0.30f, 1f);
            l.blade[1] = hsv(h2, 0.80f, 1f);
            l.blade[2] = hsv(h2, 0.95f, 0.55f);
            l.glow = hsv(h2, 0.7f, 1f);
        } else {
            int[] f = SFIN[finish];
            System.arraycopy(f, 0, l.handle, 0, 4);
            System.arraycopy(f, 4, l.blade, 0, 3);
            l.glow = f[7];
        }
        l.finish = finish;
        l.shape = KR.length + Math.max(0, Math.min(SMODELS.length - 1, model));
        return l;
    }

    private static final int H_RIBBED = 0, H_CORD = 1, H_SKEL = 2, H_SCALES = 3, H_PUSH = 4;

    public static final class Straight {
        public final String name;
        final float hl, hhw, bl;
        final float[] ws, we;
        final float bend, sweep;
        final int style;
        final boolean guard, pommel, fuller, serr, hook, lanyard, flare;

        Straight(String name, float hl, float hhw, float bl, float[] ws, float[] we, float bend, float sweep, int style, String feats) {
            this.name = name;
            this.hl = hl;
            this.hhw = hhw;
            this.bl = bl;
            this.ws = ws;
            this.we = we;
            this.bend = bend;
            this.sweep = sweep;
            this.style = style;
            this.guard = feats.contains("g");
            this.pommel = feats.contains("p");
            this.fuller = feats.contains("f");
            this.serr = feats.contains("s");
            this.hook = feats.contains("h");
            this.lanyard = feats.contains("l");
            this.flare = feats.contains("w");
        }
    }

    private static float[] w(float... v) {
        return v;
    }

    public static final Straight[] SMODELS = {
        new Straight("Bayonet", 8.4f, 1.5f, 15f, w(1.3f, 1.3f, 1.3f, 1.3f, 1.3f, 1.3f, 1.25f, 1.0f, 0.7f, 0.35f, 0f), w(1.5f, 1.6f, 1.6f, 1.6f, 1.6f, 1.6f, 1.55f, 1.4f, 1.1f, 0.6f, 0f), 0f, 0f, H_RIBBED, "gpf"),
        new Straight("M9 Bayonet", 8.6f, 1.6f, 15.5f, w(1.6f, 1.7f, 1.7f, 1.7f, 1.7f, 1.65f, 1.5f, 1.2f, 0.85f, 0.4f, 0f), w(1.9f, 2.1f, 2.2f, 2.2f, 2.2f, 2.1f, 1.9f, 1.5f, 1.0f, 0.5f, 0f), 0f, 0f, H_RIBBED, "gpfs"),
        new Straight("Huntsman", 7.6f, 1.7f, 14f, w(1.6f, 1.9f, 2.0f, 2.0f, 1.9f, 1.7f, 1.4f, 1.0f, 0.7f, 0.35f, 0f), w(1.6f, 2.1f, 2.4f, 2.5f, 2.5f, 2.4f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_SCALES, "gf"),
        new Straight("Flip knife", 8.0f, 1.4f, 14f, w(1.4f, 1.6f, 1.7f, 1.7f, 1.6f, 1.4f, 1.1f, 0.8f, 0.55f, 0.25f, 0f), w(1.4f, 1.8f, 2.0f, 2.1f, 2.1f, 2.0f, 1.7f, 1.3f, 0.8f, 0.4f, 0f), 0f, 0f, H_SCALES, "f"),
        new Straight("Gut knife", 8.0f, 1.5f, 14f, w(1.5f, 1.7f, 1.8f, 1.8f, 1.8f, 1.6f, 1.3f, 1.0f, 0.65f, 0.3f, 0f), w(1.5f, 1.9f, 2.2f, 2.3f, 2.3f, 2.2f, 1.9f, 1.4f, 0.9f, 0.4f, 0f), 0f, 0f, H_SCALES, "gh"),
        new Straight("Falchion", 8.0f, 1.4f, 16f, w(1.2f, 1.3f, 1.4f, 1.6f, 1.8f, 2.0f, 2.1f, 2.1f, 1.7f, 1.0f, 0f), w(1.2f, 1.3f, 1.5f, 1.7f, 1.9f, 2.0f, 2.0f, 1.8f, 1.4f, 0.8f, 0f), -22f, 50f, H_RIBBED, "gf"),
        new Straight("Bowie", 8.4f, 1.6f, 15f, w(1.6f, 1.8f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.1f, 0.55f, 0f), w(1.8f, 2.2f, 2.5f, 2.6f, 2.6f, 2.6f, 2.4f, 1.9f, 1.2f, 0.6f, 0f), 0f, 0f, H_SCALES, "gp"),
        new Straight("Navaja", 10f, 1.4f, 13.5f, w(1.1f, 1.3f, 1.5f, 1.6f, 1.7f, 1.7f, 1.6f, 1.3f, 0.9f, 0.4f, 0f), w(1.1f, 1.4f, 1.7f, 1.9f, 2.0f, 2.0f, 1.9f, 1.5f, 1.0f, 0.5f, 0f), -34f, 34f, H_SCALES, "pl"),
        new Straight("Stiletto", 8.2f, 1.1f, 16f, w(0.9f, 0.9f, 0.9f, 0.9f, 0.85f, 0.8f, 0.7f, 0.6f, 0.45f, 0.25f, 0f), w(0.9f, 0.9f, 0.9f, 0.9f, 0.85f, 0.8f, 0.7f, 0.6f, 0.45f, 0.25f, 0f), 0f, 0f, H_SCALES, "gp"),
        new Straight("Talon", 8.0f, 1.3f, 14f, w(1.2f, 1.3f, 1.35f, 1.3f, 1.2f, 1.0f, 0.8f, 0.6f, 0.4f, 0.2f, 0f), w(1.2f, 1.4f, 1.5f, 1.5f, 1.4f, 1.2f, 1.0f, 0.7f, 0.4f, 0.2f, 0f), 11f, 75f, H_RIBBED, "l"),
        new Straight("Ursus", 9.2f, 1.5f, 14f, w(1.5f, 1.7f, 1.8f, 1.8f, 1.7f, 1.5f, 1.2f, 0.9f, 0.6f, 0.3f, 0f), w(1.5f, 1.9f, 2.2f, 2.3f, 2.3f, 2.2f, 1.9f, 1.4f, 0.9f, 0.4f, 0f), 0f, 0f, H_RIBBED, "lw"),
        new Straight("Classic", 8.4f, 1.3f, 13.5f, w(1.4f, 1.6f, 1.7f, 1.7f, 1.6f, 1.4f, 1.1f, 0.8f, 0.5f, 0.25f, 0f), w(1.5f, 1.8f, 2.1f, 2.2f, 2.2f, 2.1f, 1.8f, 1.4f, 0.9f, 0.4f, 0f), 0f, 0f, H_CORD, "l"),
        new Straight("Paracord", 8.8f, 1.4f, 14f, w(1.5f, 1.6f, 1.6f, 1.6f, 1.6f, 1.6f, 1.6f, 1.6f, 1.5f, 1.1f, 0f), w(1.5f, 1.8f, 2.0f, 2.0f, 2.0f, 2.0f, 2.0f, 1.9f, 1.5f, 0.9f, 0f), 0f, 0f, H_CORD, "pl"),
        new Straight("Survival", 8.4f, 1.6f, 14f, w(1.6f, 1.9f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.0f, 0.5f, 0f), w(1.7f, 2.1f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_RIBBED, "gps"),
        new Straight("Nomad", 8.4f, 1.4f, 13.5f, w(1.3f, 1.5f, 1.6f, 1.6f, 1.5f, 1.3f, 1.1f, 0.8f, 0.5f, 0.25f, 0f), w(1.3f, 1.6f, 1.8f, 1.9f, 1.9f, 1.8f, 1.5f, 1.2f, 0.8f, 0.4f, 0f), 0f, 0f, H_SKEL, "f"),
        new Straight("Skeleton", 9.0f, 1.3f, 14f, w(1.1f, 1.3f, 1.4f, 1.4f, 1.3f, 1.1f, 0.9f, 0.7f, 0.45f, 0.2f, 0f), w(1.1f, 1.4f, 1.6f, 1.7f, 1.7f, 1.6f, 1.3f, 1.0f, 0.6f, 0.3f, 0f), 0f, 0f, H_SKEL, "pl"),
        new Straight("Kukri", 7.4f, 1.4f, 14f, w(1.0f, 1.1f, 1.2f, 1.4f, 1.7f, 2.1f, 2.5f, 2.6f, 2.2f, 1.3f, 0f), w(1.0f, 1.1f, 1.3f, 1.6f, 2.0f, 2.5f, 3.0f, 3.1f, 2.6f, 1.5f, 0f), -12f, 70f, H_RIBBED, "wf"),
        new Straight("Shadow Daggers", 3.4f, 0.9f, 10f, w(0.9f, 1.3f, 1.6f, 1.7f, 1.6f, 1.4f, 1.1f, 0.8f, 0.5f, 0.25f, 0f), w(0.9f, 1.3f, 1.6f, 1.7f, 1.6f, 1.4f, 1.1f, 0.8f, 0.5f, 0.25f, 0f), 0f, 0f, H_PUSH, "f"),
        new Straight("Tantō", 7.4f, 1.35f, 13.6f, w(1.35f, 1.45f, 1.5f, 1.5f, 1.5f, 1.45f, 1.4f, 1.3f, 1.15f, 0.9f, 0.4f), w(1.4f, 1.5f, 1.55f, 1.55f, 1.55f, 1.5f, 1.45f, 1.35f, 1.2f, 0.95f, 0.45f), 0f, 0f, H_CORD, "gp"),
        new Straight("Kris", 8f, 1.3f, 15.2f, w(1f, 1.35f, 1f, 1.4f, 1.05f, 1.5f, 1f, 1.35f, 0.9f, 1.1f, 0.3f), w(1.1f, 1.45f, 1.1f, 1.5f, 1.15f, 1.6f, 1.1f, 1.45f, 1f, 1.15f, 0.35f), 0f, 0f, H_RIBBED, "gp"),
        new Straight("Cleaver", 7.2f, 1.5f, 12.8f, w(1.7f, 2f, 2.2f, 2.3f, 2.4f, 2.4f, 2.4f, 2.4f, 2.3f, 2.2f, 1.9f), w(1.9f, 2.2f, 2.4f, 2.5f, 2.6f, 2.6f, 2.6f, 2.6f, 2.5f, 2.4f, 2.1f), 0f, 0f, H_CORD, "p"),
        new Straight("Kunai", 6.4f, 1f, 11.8f, w(0.9f, 1f, 1f, 0.95f, 0.9f, 0.8f, 0.7f, 0.6f, 0.45f, 0.3f, 0.1f), w(0.95f, 1.05f, 1.05f, 1f, 0.95f, 0.85f, 0.75f, 0.65f, 0.5f, 0.32f, 0.1f), 0f, 0f, H_CORD, "p"),
        new Straight("Jambiya", 7f, 1.45f, 12.6f, w(1.5f, 1.7f, 1.8f, 1.8f, 1.7f, 1.6f, 1.4f, 1.2f, 0.9f, 0.55f, 0.15f), w(1.6f, 1.85f, 1.95f, 1.95f, 1.85f, 1.7f, 1.5f, 1.3f, 0.95f, 0.6f, 0.15f), -20f, 55f, H_RIBBED, "gp"),
        new Straight("Khopesh", 7.8f, 1.3f, 15.4f, w(1.1f, 1.25f, 1.35f, 1.45f, 1.5f, 1.6f, 1.7f, 1.7f, 1.5f, 1f, 0.3f), w(1.2f, 1.35f, 1.45f, 1.55f, 1.6f, 1.7f, 1.8f, 1.8f, 1.6f, 1.1f, 0.3f), -26f, 78f, H_RIBBED, "h"),
        new Straight("Machete", 8.6f, 1.35f, 16.5f, w(1.5f, 1.7f, 1.8f, 1.85f, 1.9f, 1.95f, 2f, 2f, 1.8f, 1.4f, 0.7f), w(1.6f, 1.8f, 1.9f, 1.95f, 2f, 2.05f, 2.1f, 2.1f, 1.9f, 1.5f, 0.75f), -8f, 30f, H_CORD, "p"),
        new Straight("Dagger", 5.8f, 1f, 10.8f, w(1.1f, 1.25f, 1.3f, 1.28f, 1.2f, 1.1f, 0.95f, 0.8f, 0.6f, 0.4f, 0.12f), w(1.1f, 1.25f, 1.3f, 1.28f, 1.2f, 1.1f, 0.95f, 0.8f, 0.6f, 0.42f, 0.12f), 0f, 0f, H_SCALES, "gp"),

        // the HD tactical knives: drawn from the sheet atlas by KnifeHd, these entries are
        // their pixel fallback (same names, same order as knives/hd/tactical.json)
        new Straight("Tactical · Case hardened", 9.6f, 1.6f, 14.5f, w(1.6f, 1.9f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.0f, 0.5f, 0f), w(1.7f, 2.1f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_RIBBED, "gps"),
        new Straight("Tactical · Crimson web", 9.6f, 1.6f, 14.5f, w(1.6f, 1.9f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.0f, 0.5f, 0f), w(1.7f, 2.1f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_SKEL, "lps"),
        new Straight("Tactical · Damascus", 9.6f, 1.6f, 14.5f, w(1.6f, 1.9f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.0f, 0.5f, 0f), w(1.7f, 2.1f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_CORD, "gps"),
        new Straight("Tactical · Sapphire", 9.6f, 1.6f, 14.5f, w(1.6f, 1.9f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.0f, 0.5f, 0f), w(1.7f, 2.1f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_SCALES, "gps"),
        new Straight("Tactical · Fade", 9.6f, 1.6f, 14.5f, w(1.6f, 1.9f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.0f, 0.5f, 0f), w(1.7f, 2.1f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_SCALES, "lps"),
        new Straight("Tactical · Slaughter", 9.6f, 1.6f, 14.5f, w(1.6f, 1.9f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.0f, 0.5f, 0f), w(1.7f, 2.1f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_CORD, "lps"),
        new Straight("Tactical · Tiger tooth", 9.6f, 1.6f, 14.5f, w(1.6f, 1.9f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.0f, 0.5f, 0f), w(1.7f, 2.1f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_RIBBED, "gps"),
        new Straight("Tactical · Urban masked", 9.6f, 1.6f, 14.5f, w(1.6f, 1.9f, 1.9f, 1.9f, 1.9f, 1.9f, 1.8f, 1.5f, 1.0f, 0.5f, 0f), w(1.7f, 2.1f, 2.3f, 2.3f, 2.3f, 2.3f, 2.1f, 1.6f, 1.0f, 0.5f, 0f), 0f, 0f, H_SKEL, "ps"),
    };

    /** How many of the last straight models are HD tactical knives drawn by {@link KnifeHd}. */
    public static final int HD_COUNT = 8;

    /** HD knife index for a knife key (KR.length + straight index), or -1. */
    public static int hdOf(int key) {
        int si = key - KR.length;
        if (si < 0 || si >= SMODELS.length) return -1;
        int hd = si - (SMODELS.length - HD_COUNT);
        return hd >= 0 && hd < KnifeHd.count() ? hd : -1;
    }

    /** HD knife index for a knife type (0 butterfly, 1 karambit, 2.. straight), or -1. */
    public static int hdType(int type) {
        return type >= 2 ? hdOf(KR.length + type - 2) : -1;
    }


    public static String[] straightNames() {
        String[] n = new String[SMODELS.length];
        for (int i = 0; i < n.length; i++) n[i] = SMODELS[i].name;
        return n;
    }

    public static float gripOf(int key) {
        if (key < KR.length) return KGRIP;
        int hd = hdOf(key);
        if (hd >= 0) return KnifeHd.grip(KnifeHd.model(hd));
        Straight m = SMODELS[Math.min(SMODELS.length - 1, key - KR.length)];
        return (m.style == H_PUSH ? 1.6f : m.hl * 0.5f) * Q;
    }

    private static final float[][] BOUNDS = new float[KR.length + SMODELS.length][];
    private static final KSet[][] KSETS = new KSet[KR.length + SMODELS.length][4];

    private static float[] bounds(int key) {
        key = Math.max(0, Math.min(KR.length + SMODELS.length - 1, key));
        if (key < KR.length) return new float[] {KX0, KX1, KY0, KY1};
        float[] b = BOUNDS[key];
        if (b != null) return b;
        float x0 = 1e9f, x1 = -1e9f, y0 = 1e9f, y1 = -1e9f;
        for (float y = -3f; y <= 34f; y += 0.2f) {
            for (float x = -16f; x <= 16f; x += 0.2f) {
                if (sClass(SMODELS[key - KR.length], x, y) != 0) {
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                }
            }
        }
        b = new float[] {x0 - 0.6f, x1 + 0.6f, y0 - 0.6f, y1 + 0.6f};
        BOUNDS[key] = b;
        return b;
    }

    private static int classify(int key, float x, float y) {
        if (key < KR.length) return kClass(key, x, y);
        return sClass(SMODELS[key - KR.length], x, y);
    }

    private static float prof(float[] a, float t) {
        float f = Math.max(0f, Math.min(1f, t)) * (a.length - 1);
        int i = Math.min(a.length - 2, (int) f);
        float k = f - i;
        return a[i] * (1f - k) + a[i + 1] * k;
    }

    private static int sClass(Straight m, float x, float y) {
        float hl = m.hl;
        float gy = m.guard ? 0.9f : 0f;
        if (y >= 0f && y < hl) {
            float hw = m.hhw;
            if (m.style == H_PUSH) {
                if (y < 2.4f) {
                    if (Math.abs(x) <= 4.6f) return Math.abs(x) > 3.6f ? 4 : (((int) Math.floor((x + 4.6f) / 0.9f)) & 1) == 1 ? 1 : 2;
                    return 0;
                }
                return Math.abs(x) <= 0.9f ? 2 : 0;
            }
            if (m.flare) hw *= 1f + 0.22f * (float) Math.sin(Math.PI * Math.max(0f, 1f - y / (hl * 0.7f)));
            if (m.style == H_SCALES) hw *= 1f + 0.06f * (float) Math.sin(y * 0.7f);
            if (Math.abs(x) > hw) return pommelOf(m, x, y);
            if (m.lanyard && x * x + (y - 1.0f) * (y - 1.0f) < 0.20f) return 0;
            switch (m.style) {
                case H_SKEL -> {
                    boolean frame = Math.abs(x) > hw - 0.45f || y < 0.8f || y > hl - 0.8f;
                    if (!frame) {
                        for (float yc = 2.3f; yc < hl - 1.2f; yc += 2.4f) {
                            float dy = (y - yc) / 1.0f;
                            if ((x / 0.55f) * (x / 0.55f) + dy * dy < 1f) return 0;
                        }
                    }
                    return Math.abs(x) > hw - 0.45f ? 4 : 2;
                }
                case H_CORD -> {
                    int gx = (int) Math.floor((x + hw) / 0.8f);
                    int gyy = (int) Math.floor(y / 0.8f);
                    if (y < 0.8f) return 4;
                    return ((gx + gyy) & 1) == 1 ? 1 : 2;
                }
                case H_SCALES -> {
                    if (y < 0.9f || y > hl - 0.9f) return 4;
                    float r1 = x * x + (y - 2.6f) * (y - 2.6f);
                    float r2 = x * x + (y - (hl - 2.6f)) * (y - (hl - 2.6f));
                    if (r1 < 0.16f || r2 < 0.16f) return 5;
                    return x > hw * 0.35f ? 3 : x > -hw * 0.2f ? 2 : 1;
                }
                default -> {
                    if (y < 0.8f) return 4;
                    float r1 = x * x + (y - 2.0f) * (y - 2.0f);
                    float r2 = x * x + (y - (hl - 2.0f)) * (y - (hl - 2.0f));
                    if (r1 < 0.14f || r2 < 0.14f) return 5;
                    if (Math.abs(x) < 0.25f) return 3;
                    return (((int) Math.floor(y / 0.85f)) & 1) == 1 ? 1 : 2;
                }
            }
        }
        if (y < 0f) return pommelOf(m, x, y);
        if (m.guard && y >= hl && y < hl + gy) return Math.abs(x) <= m.hhw + 1.0f ? 4 : 0;
        float y0 = hl + gy;
        float t;
        float d;
        if (m.bend == 0f) {
            t = (y - y0) / m.bl;
            d = x;
        } else {
            float rm = Math.abs(m.bend);
            float tm = (float) Math.toRadians(m.sweep);
            float dx = m.bend > 0f ? x + rm : rm - x;
            float dy = y - y0;
            float th = (float) Math.atan2(dy, dx);
            if (th < 0f || th > tm) return 0;
            t = th / tm;
            float rr = (float) Math.sqrt(dx * dx + dy * dy);
            d = m.bend > 0f ? rr - rm : rm - rr;
        }
        if (t < 0f || t > 1f) return 0;
        float ws = prof(m.ws, t);
        float we = prof(m.we, t);
        boolean spineSide = d > 0f;
        float lim = spineSide ? ws : we;
        if (Math.abs(d) > lim) return 0;
        if (spineSide && m.serr && t > 0.12f && t < 0.62f && d > ws - 0.55f && (((int) Math.floor(t * m.bl * 1.25f)) & 1) == 1) return 0;
        if (spineSide && m.hook) {
            float u = Math.abs(t - 0.5f) / 0.07f;
            if (u < 1f && d > ws - 1.0f * (1f - u)) return 0;
        }
        int cd;
        if (m.fuller && t > 0.10f && t < 0.78f && Math.abs(d - ws * 0.30f) < 0.16f) cd = 12;
        else if (spineSide && d > ws * 0.55f) cd = 7;
        else if (!spineSide && d < -we * 0.45f) cd = 9;
        else if (t > 0.9f) cd = 10;
        else cd = t > 0.35f && t < 0.75f ? 9 : 8;
        return spineSide ? cd : cd | 64;
    }

    private static int pommelOf(Straight m, float x, float y) {
        if (!m.pommel) return 0;
        float r = m.hhw * 1.05f;
        return x * x + (y + 0.1f) * (y + 0.1f) <= r * r && y < 0.3f && y > -r - 0.1f ? 4 : 0;
    }
}
