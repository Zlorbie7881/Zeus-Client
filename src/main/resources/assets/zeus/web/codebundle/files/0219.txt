package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ArmArt {
    public static final String[] LOOKS = {"My skin", "Light skin", "Tan skin", "Dark skin", "Black glove", "Zeus glove"};

    private static final int[][] PALETTES = {
        {0xFFC4916C, 0xFF9C6A48, 0xFF2BA8A8, 0xFF1A7474},
        {0xFFF1C7A5, 0xFFD39F78, 0xFF2BA8A8, 0xFF1A7474},
        {0xFFD9A06F, 0xFFB4794A, 0xFF2BA8A8, 0xFF1A7474},
        {0xFF8A5A3C, 0xFF63391F, 0xFF2BA8A8, 0xFF1A7474},
        {0xFF3C4350, 0xFF181B22, 0xFF2D3446, 0xFF181C28},
        {0xFF3A5CA0, 0xFF1B2C58, 0xFF1C2A4A, 0xFF0E1630},
    };

    public static final class Skin {
        public Identifier texture;
        public boolean slim;
        public int look;
        public int glow;
        public boolean hat = true;
    }

    public static float armWidth(Skin s) {
        return s.slim ? 6f : 8f;
    }

    public static Rig.Painter arm(Skin s) {
        return (g, c, face, w, h) -> {
            if (s.texture != null) {
                int[] r = region(face, s.slim);
                tex(g, c, s, r, w, h, 0);
                if (s.hat) tex(g, c, s, r, w, h, 16);
            } else {
                solid(g, c, s, face, w, h);
            }
            glow(g, c, s, w, h);
        };
    }

    private static int[] region(int face, boolean slim) {
        return switch (face) {
            case 5 -> new int[] {44, 20, slim ? 3 : 4, 12};
            case 4 -> new int[] {slim ? 51 : 52, 20, slim ? 3 : 4, 12};
            case 0 -> new int[] {40, 20, 4, 12};
            case 1 -> new int[] {slim ? 47 : 48, 20, 4, 12};
            case 3 -> new int[] {44, 16, slim ? 3 : 4, 4};
            default -> new int[] {slim ? 47 : 48, 16, slim ? 3 : 4, 4};
        };
    }

    private static void tex(GuiGraphics g, Rig.Ctx c, Skin s, int[] r, int w, int h, int dv) {
        int k = Math.round(Math.min(1f, c.shade) * 255f);
        int a = Math.round(c.alpha * 255f);
        int tint = (a << 24) | (k << 16) | (k << 8) | k;
        g.blit(RenderPipelines.GUI_TEXTURED, s.texture, 0, 0, (float) r[0], (float) (r[1] + dv), w, h, r[2], r[3], 64, 64, tint);
        if (dv == 0 && c.shade > 1f) g.fill(0, 0, w, h, c.raw(0xFFFFFFFF, (c.shade - 1f) * 0.6f));
    }

    private static void solid(GuiGraphics g, Rig.Ctx c, Skin s, int face, int w, int h) {
        int[] p = PALETTES[Math.max(0, Math.min(PALETTES.length - 1, s.look))];
        int block = Math.max(2, Rig.K * 2);
        int rows = Math.max(1, Math.round(h / (float) block));
        int cols = Math.max(1, Math.round(w / (float) block));
        for (int j = 0; j < rows; j++) {
            int y1 = j * h / rows;
            int y2 = (j + 1) * h / rows;
            boolean sleeve = face != 2 && j < rows / 3 + (face == 3 ? 1 : 0);
            for (int i = 0; i < cols; i++) {
                int x1 = i * w / cols;
                int x2 = (i + 1) * w / cols;
                int base = sleeve ? Draw.mix(p[2], p[3], 0.35f + 0.5f * j / rows) : Draw.mix(p[0], p[1], 0.18f + 0.55f * j / rows);
                float n = noise(i * 7 + j * 13 + face * 3) * 0.10f - 0.05f;
                g.fill(x1, y1, x2, y2, c.col(n > 0 ? Draw.lighten(base, n * 2f) : Draw.darken(base, -n * 2f)));
            }
        }
        if (face == 5 || face == 4) {
            int y = h / 3 + 1;
            g.fill(0, y, w, y + 1, c.col(Draw.darken(p[3], 0.1f)));
        }
        if (face == 0 || face == 1) g.fill(0, 0, 1, h, c.raw(0xFF000000, 0.12f));
    }

    private static float noise(int n) {
        int x = n * 374761393 + 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        return ((x ^ (x >>> 16)) & 255) / 255f;
    }

    private static void glow(GuiGraphics g, Rig.Ctx c, Skin s, int w, int h) {
        int a = (s.glow >>> 24) & 255;
        if (a < 4) return;
        g.fill(0, 0, w, h, c.raw(s.glow & 0x00FFFFFF | 0xFF000000, a / 255f));
    }

    public static Rig.Painter finger(Skin s) {
        return (g, c, face, w, h) -> {
            if (s.texture != null) {
                int[] r = region(2, s.slim);
                tex(g, c, s, r, w, h, 0);
                if (face != 4 && face != 5) g.fill(0, 0, w, h, c.raw(0xFF000000, 0.10f));
            } else {
                int[] p = PALETTES[Math.max(0, Math.min(PALETTES.length - 1, s.look))];
                g.fill(0, 0, w, h, c.col(p[1]));
                g.fill(1, 1, w - 1, h - 1, c.col(Draw.mix(p[0], p[1], 0.22f)));
            }
            g.fill(0, 0, w, 1, c.raw(0xFFFFFFFF, 0.14f));
            g.fill(0, h - 1, w, h, c.raw(0xFF000000, 0.30f));
            g.fill(0, 0, 1, h, c.raw(0xFF000000, 0.14f));
            glow(g, c, s, w, h);
        };
    }

    public static Skin skin(net.minecraft.client.player.AbstractClientPlayer player, int look, int glow) {
        Skin s = new Skin();
        s.look = look <= 0 ? 1 : look;
        s.glow = glow;
        if (look == 0 && player != null) {
            try {
                var sk = player.getSkin();
                if (sk != null) {
                    s.texture = sk.body().texturePath();
                    s.slim = "SLIM".equals(sk.model().name());
                }
            } catch (Throwable ignored) {
                s.texture = null;
            }
        }
        return s;
    }
}
