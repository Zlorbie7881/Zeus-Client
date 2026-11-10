package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sharp text and smooth shapes for the panel. Everything here works in real screen pixels (the panel draws with the pose
 * scaled down by the GUI scale), so a letter or a rounded corner covers exactly the pixels it was made for.
 *
 * <p>Text is Inter, drawn once by Java2D (with kerning and anti-aliasing) into a shared picture, and every later frame is one
 * picture call per string. Rounded boxes are made the same way: a small white corner picture per radius, tinted when drawn,
 * so any colour or fade costs nothing extra and the picture never grows. Both live in a few big pictures, so the whole
 * panel is drawn from two textures. A string or shape is only made the first time it is seen.
 */
final class Ty {
    private Ty() {}

    static final int REG = 0;
    static final int MED = 1;
    static final int SEMI = 2;
    static final int BOLD = 3;
    static final int XBOLD = 4;

    private static final String[] FILES = {"inter-regular", "inter-medium", "inter-semibold", "inter-bold", "inter-extrabold"};
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static Font[] base;
    private static boolean tried;
    private static final Map<Integer, Font> DERIVED = new HashMap<>();
    private static final int[] BOOST = new int[256];

    static {
        for (int i = 0; i < 256; i++) BOOST[i] = Math.min(255, (int) Math.round(255.0 * Math.pow(i / 255.0, 0.80)));
    }

    // ------------------------------------------------------------------ fonts

    static boolean ready() {
        if (!tried) {
            tried = true;
            try {
                Font[] f = new Font[FILES.length];
                for (int i = 0; i < f.length; i++) {
                    try (InputStream in = Ty.class.getResourceAsStream("/assets/zeus/fonts/" + FILES[i] + ".otf")) {
                        if (in == null) return false;
                        f[i] = Font.createFont(Font.TRUETYPE_FONT, in);
                    }
                }
                base = f;
            } catch (Throwable t) {
                Zeus.LOG.warn("The panel font could not be loaded, the game's font is used instead", t);
                base = null;
            }
        }
        return base != null && !disabled;
    }

    private static boolean disabled;

    private static Font font(int weight, int size2) {
        int key = weight * 100000 + size2;
        Font f = DERIVED.get(key);
        if (f == null) {
            Map<TextAttribute, Object> a = new HashMap<>();
            a.put(TextAttribute.SIZE, size2 / 2f);
            a.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
            a.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
            f = base[weight].deriveFont(a);
            DERIVED.put(key, f);
        }
        return f;
    }

    private static final Map<String, Boolean> SUPPORTED = new HashMap<>();

    /** True when every character of the text is in the panel font. */
    private static boolean supported(String s) {
        Boolean b = SUPPORTED.get(s);
        if (b == null) {
            if (SUPPORTED.size() > 6000) SUPPORTED.clear();
            boolean ok = true;
            Font f = base[REG];
            for (int i = 0; i < s.length() && ok; i++) {
                char c = s.charAt(i);
                if (c < 32 || !f.canDisplay(c)) ok = false;
            }
            b = ok;
            SUPPORTED.put(s, b);
        }
        return b;
    }

    // ------------------------------------------------------------------ the shared pictures

    private static final class Page {
        final int w;
        final int h;
        final int[] pix;
        final BufferedImage img;
        Tex.Img tex;
        int shelfY;
        int shelfH;
        int curX;
        int dirty0 = Integer.MAX_VALUE;
        int dirty1 = -1;

        Page(int w, int h) {
            this.w = w;
            this.h = h;
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            pix = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        }

        void dirty(int y0, int y1) {
            dirty0 = Math.min(dirty0, y0);
            dirty1 = Math.max(dirty1, y1);
        }
    }

    private record Slot(Page page, int x, int y, int w, int h, int base) {}

    private static final class Atlas {
        final int pw;
        final int ph;
        final int maxPages;
        final List<Page> pages = new ArrayList<>();
        boolean full;

        Atlas(int pw, int ph, int maxPages) {
            this.pw = pw;
            this.ph = ph;
            this.maxPages = maxPages;
        }

        Slot alloc(int w, int h, int base) {
            if (w > pw || h > ph) return null;
            int ah = (h + 3) & ~3;
            for (Page p : pages) {
                Slot s = place(p, w, h, ah, base);
                if (s != null) return s;
            }
            if (pages.size() < maxPages) {
                Page p = new Page(pw, ph);
                pages.add(p);
                return place(p, w, h, ah, base);
            }
            full = true;
            return null;
        }

        private Slot place(Page p, int w, int h, int ah, int base) {
            if (p.shelfH == 0 || ah > p.shelfH || p.curX + w > p.w) {
                int ny = p.shelfH == 0 ? 0 : p.shelfY + p.shelfH;
                if (ny + ah > p.h) return null;
                p.shelfY = ny;
                p.shelfH = ah;
                p.curX = 0;
            }
            Slot s = new Slot(p, p.curX, p.shelfY, w, h, base);
            p.curX += w + 1;
            return s;
        }

        void reset() {
            for (Page p : pages) {
                Arrays.fill(p.pix, 0);
                p.shelfY = 0;
                p.shelfH = 0;
                p.curX = 0;
                p.dirty(0, p.h);
            }
            full = false;
        }

        void flush() {
            for (Page p : pages) {
                if (p.dirty1 < 0) continue;
                if (p.tex == null) {
                    p.tex = Tex.blank(p.w, p.h);
                    p.dirty0 = 0;
                    p.dirty1 = p.h;
                }
                p.tex.putRows(p.pix, p.dirty0, p.dirty1);
                p.dirty0 = Integer.MAX_VALUE;
                p.dirty1 = -1;
            }
        }

        void release() {
            for (Page p : pages) if (p.tex != null) p.tex.release();
            pages.clear();
            full = false;
        }
    }

    private static final Atlas TEXT = new Atlas(2048, 1024, 3);
    private static final Atlas SHAPES = new Atlas(1024, 1024, 1);
    private static final Map<TextKey, Slot> TEXT_SLOTS = new HashMap<>();
    private static final Map<ShapeKey, Slot> SHAPE_SLOTS = new HashMap<>();
    private static final Map<TextKey, Float> WIDTHS = new HashMap<>();

    private record TextKey(String s, int weight, int size2) {}

    private record ShapeKey(char kind, int r, int extra) {}

    /** Call at the start of a frame: starts over with empty pictures when the last frame ran out of room. */
    static void begin() {
        if (TEXT.full) {
            TEXT.reset();
            TEXT_SLOTS.clear();
        }
        if (SHAPES.full) {
            SHAPES.reset();
            SHAPE_SLOTS.clear();
        }
    }

    /** Call at the end of a frame: sends what was made this frame to the graphics card (once). */
    static void flush() {
        TEXT.flush();
        SHAPES.flush();
    }

    /** Gives the pictures back (the window was resized a lot, or the screen closed for good). */
    static void release() {
        TEXT.release();
        SHAPES.release();
        TEXT_SLOTS.clear();
        SHAPE_SLOTS.clear();
    }

    // ------------------------------------------------------------------ text

    private static final int PAD = 2;

    private static int size2(float px) {
        return Math.max(14, Math.round(px * 2f));
    }

    /** Width of the text in pixels. */
    static int w(String s, float px, int weight) {
        if (s == null || s.isEmpty()) return 0;
        if (!ready() || !supported(s)) return Math.round(Draw.width(s) * Math.max(1f, px / 9.6f));
        return (int) Math.ceil(widthF(s, px, weight));
    }

    private static float widthF(String s, float px, int weight) {
        TextKey k = new TextKey(s, weight, size2(px));
        Float v = WIDTHS.get(k);
        if (v == null) {
            if (WIDTHS.size() > 5000) WIDTHS.clear();
            Font f = font(weight, k.size2());
            v = (float) f.createGlyphVector(FRC, s).getLogicalBounds().getWidth();
            WIDTHS.put(k, v);
        }
        return v;
    }

    private static Slot textSlot(String s, int weight, int size2) {
        TextKey k = new TextKey(s, weight, size2);
        Slot sl = TEXT_SLOTS.get(k);
        if (sl != null) return sl;
        Font f = font(weight, size2);
        LineMetrics lm = f.getLineMetrics(s, FRC);
        float width = (float) f.createGlyphVector(FRC, s).getLogicalBounds().getWidth();
        int asc = (int) Math.ceil(lm.getAscent());
        int desc = (int) Math.ceil(lm.getDescent());
        int w = (int) Math.ceil(width) + 2 * PAD + 2;
        int h = asc + desc + 2 * PAD;
        sl = TEXT.alloc(w, h, PAD + asc);
        if (sl == null) return null;
        Graphics2D g = sl.page().img.createGraphics();
        try {
            g.setClip(sl.x(), sl.y(), w, h);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setFont(f);
            g.setColor(java.awt.Color.WHITE);
            g.drawString(s, sl.x() + PAD, sl.y() + PAD + asc);
        } finally {
            g.dispose();
        }
        int[] pix = sl.page().pix;
        int pw = sl.page().w;
        for (int yy = sl.y(); yy < sl.y() + h; yy++) {
            int row = yy * pw;
            for (int xx = sl.x(); xx < sl.x() + w; xx++) {
                int a = pix[row + xx] >>> 24;
                if (a != 0) pix[row + xx] = (BOOST[a] << 24) | 0xFFFFFF;
            }
        }
        sl.page().dirty(sl.y(), sl.y() + h);
        TEXT_SLOTS.put(k, sl);
        return sl;
    }

    private static void fallback(GuiGraphics g, String s, int x, int cy, float px, int color) {
        float sc = Math.max(1f, px / 9.6f);
        Draw.textS(g, s, x, cy - Math.round(4 * sc), color, sc);
    }

    /** Left aligned text, vertically centred on {@code cy}. */
    static void text(GuiGraphics g, String s, int x, int cy, float px, int weight, int color) {
        if (s == null || s.isEmpty() || (color >>> 24) == 0) return;
        if (!ready() || !supported(s)) {
            fallback(g, s, x, cy, px, color);
            return;
        }
        Slot sl = textSlot(s, weight, size2(px));
        if (sl == null) {
            fallback(g, s, x, cy, px, color);
            return;
        }
        // the middle of a capital letter sits on cy
        int baseline = cy + Math.round(px * 0.364f);
        ensure(sl);
        part(g, sl, x - PAD, baseline - sl.base(), sl.w(), sl.h(), 0, 0, sl.w(), sl.h(), color);
    }

    static void textC(GuiGraphics g, String s, int cx, int cy, float px, int weight, int color) {
        text(g, s, cx - w(s, px, weight) / 2, cy, px, weight, color);
    }

    static void textR(GuiGraphics g, String s, int xr, int cy, float px, int weight, int color) {
        text(g, s, xr - w(s, px, weight), cy, px, weight, color);
    }

    /** The start of the text that fits in {@code max} pixels, with an ellipsis when cut. */
    static String fit(String s, int max, float px, int weight) {
        if (s == null || w(s, px, weight) <= max) return s;
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (w(s.substring(0, mid).stripTrailing() + "…", px, weight) <= max) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo).stripTrailing() + "…";
    }

    // ------------------------------------------------------------------ shapes

    private static Slot shape(char kind, int r, int extra) {
        ShapeKey k = new ShapeKey(kind, r, extra);
        Slot sl = SHAPE_SLOTS.get(k);
        if (sl != null) return sl;
        int size;
        int c;
        switch (kind) {
            case 'G' -> {
                c = r + extra;
                size = 2 * c + 2;
            }
            default -> {
                c = r;
                size = 2 * r;
            }
        }
        sl = SHAPES.alloc(size, size, 0);
        if (sl == null) return null;
        BufferedImage tmp = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(java.awt.Color.WHITE);
            if (kind == 'F') {
                g.fill(new RoundRectangle2D.Float(0, 0, size, size, size, size));
            } else if (kind == 'R') {
                g.fill(new RoundRectangle2D.Float(0, 0, size, size, size, size));
                g.setComposite(AlphaComposite.Clear);
                int in = extra;
                float ir = Math.max(0, r - in);
                g.fill(new RoundRectangle2D.Float(in, in, size - 2 * in, size - 2 * in, ir * 2, ir * 2));
            } else {
                g.fill(new RoundRectangle2D.Float(extra, extra, 2 * r + 2, 2 * r + 2, 2 * r, 2 * r));
            }
        } finally {
            g.dispose();
        }
        int[] px = tmp.getRGB(0, 0, size, size, null, 0, size);
        if (kind == 'G') px = glowPixels(px, size, extra);
        int[] pix = sl.page().pix;
        int pw = sl.page().w;
        for (int yy = 0; yy < size; yy++) System.arraycopy(px, yy * size, pix, (sl.y() + yy) * pw + sl.x(), size);
        sl.page().dirty(sl.y(), sl.y() + size);
        SHAPE_SLOTS.put(k, sl);
        return sl;
    }

    /** Blurs the white box and keeps only what lies outside it: a soft halo. */
    private static int[] glowPixels(int[] box, int size, int spread) {
        float[] a = new float[size * size];
        for (int i = 0; i < a.length; i++) a[i] = (box[i] >>> 24) / 255f;
        int rad = Math.max(1, spread);
        float sigma = Math.max(0.8f, spread / 2.3f);
        float[] ker = new float[rad * 2 + 1];
        float sum = 0;
        for (int i = -rad; i <= rad; i++) {
            ker[i + rad] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += ker[i + rad];
        }
        for (int i = 0; i < ker.length; i++) ker[i] /= sum;
        float[] t = new float[a.length];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float v = 0;
                for (int i = -rad; i <= rad; i++) {
                    int xx = Math.max(0, Math.min(size - 1, x + i));
                    v += a[y * size + xx] * ker[i + rad];
                }
                t[y * size + x] = v;
            }
        }
        float[] o = new float[a.length];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float v = 0;
                for (int i = -rad; i <= rad; i++) {
                    int yy = Math.max(0, Math.min(size - 1, y + i));
                    v += t[yy * size + x] * ker[i + rad];
                }
                o[y * size + x] = v;
            }
        }
        int[] out = new int[a.length];
        for (int i = 0; i < out.length; i++) {
            float v = o[i] * (1f - a[i]);
            int al = Math.max(0, Math.min(255, Math.round(v * 255f)));
            out[i] = (al << 24) | 0xFFFFFF;
        }
        return out;
    }

    private static void part(GuiGraphics g, Slot sl, int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh, int tint) {
        if (dw <= 0 || dh <= 0) return;
        sl.page().tex.drawPart(g, dx, dy, dw, dh, sl.x() + sx, sl.y() + sy, sw, sh, tint);
    }

    /** A filled rounded box with smooth corners. */
    static void fill(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r < 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        Slot sl = shape('F', r, 0);
        if (sl == null) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + r, color);
        g.fill(x, y + r, x + w, y + h - r, color);
        g.fill(x + r, y + h - r, x + w - r, y + h, color);
        ensure(sl);
        part(g, sl, x, y, r, r, 0, 0, r, r, color);
        part(g, sl, x + w - r, y, r, r, r, 0, r, r, color);
        part(g, sl, x, y + h - r, r, r, 0, r, r, r, color);
        part(g, sl, x + w - r, y + h - r, r, r, r, r, r, r, color);
    }

    /** The left end of a rounded box only: the two rounded corners and the strip between them, {@code r} pixels wide. */
    static void fillLeftEnd(GuiGraphics g, int x, int y, int h, int r, int color) {
        if (h <= 0 || r < 2 || (color >>> 24) == 0) return;
        r = Math.min(r, h / 2);
        Slot sl = shape('F', r, 0);
        if (sl == null) {
            g.fill(x, y, x + r, y + h, color);
            return;
        }
        g.fill(x, y + r, x + r, y + h - r, color);
        ensure(sl);
        part(g, sl, x, y, r, r, 0, 0, r, r, color);
        part(g, sl, x, y + h - r, r, r, 0, r, r, r, color);
    }

    /** A thin smooth outline of a rounded box, {@code bw} pixels thick. */
    static void ring(GuiGraphics g, int x, int y, int w, int h, int r, int bw, int color) {
        if (w <= 1 || h <= 1 || (color >>> 24) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        bw = Math.max(1, bw);
        if (r < 2) {
            g.fill(x, y, x + w, y + bw, color);
            g.fill(x, y + h - bw, x + w, y + h, color);
            g.fill(x, y + bw, x + bw, y + h - bw, color);
            g.fill(x + w - bw, y + bw, x + w, y + h - bw, color);
            return;
        }
        Slot sl = shape('R', r, bw);
        if (sl == null) return;
        g.fill(x + r, y, x + w - r, y + bw, color);
        g.fill(x + r, y + h - bw, x + w - r, y + h, color);
        g.fill(x, y + r, x + bw, y + h - r, color);
        g.fill(x + w - bw, y + r, x + w, y + h - r, color);
        ensure(sl);
        part(g, sl, x, y, r, r, 0, 0, r, r, color);
        part(g, sl, x + w - r, y, r, r, r, 0, r, r, color);
        part(g, sl, x, y + h - r, r, r, 0, r, r, r, color);
        part(g, sl, x + w - r, y + h - r, r, r, r, r, r, r, color);
    }

    /** A soft halo around a rounded box (nothing inside it). The colour's alpha is the strength. */
    static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int spread, int color) {
        if (w <= 0 || h <= 0 || spread < 2 || (color >>> 24) == 0) return;
        r = Math.max(2, Math.min(r, Math.min(w, h) / 2));
        Slot sl = shape('G', r, spread);
        if (sl == null) return;
        ensure(sl);
        int c = r + spread;
        part(g, sl, x - spread, y - spread, c, c, 0, 0, c, c, color);
        part(g, sl, x + w - r, y - spread, c, c, c + 2, 0, c, c, color);
        part(g, sl, x - spread, y + h - r, c, c, 0, c + 2, c, c, color);
        part(g, sl, x + w - r, y + h - r, c, c, c + 2, c + 2, c, c, color);
        part(g, sl, x + r, y - spread, w - 2 * r, spread, c, 0, 1, spread, color);
        part(g, sl, x + r, y + h, w - 2 * r, spread, c, c + 2 + r, 1, spread, color);
        part(g, sl, x - spread, y + r, spread, h - 2 * r, 0, c, spread, 1, color);
        part(g, sl, x + w, y + r, spread, h - 2 * r, c + 2 + r, c, spread, 1, color);
    }

    static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        fill(g, cx - r, cy - r, 2 * r, 2 * r, r, color);
    }

    private static void ensure(Slot sl) {
        // the page picture exists only once something was sent to the card; flush() is called at the end of the frame
        if (sl.page().tex == null) {
            sl.page().tex = Tex.blank(sl.page().w, sl.page().h);
            sl.page().dirty(0, sl.page().h);
        }
    }
}
