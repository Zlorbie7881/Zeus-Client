package com.zlorbie788.zeus.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class Tex {
    private Tex() {
    }

    static {
        javax.imageio.ImageIO.setUseCache(false);
    }

    public static final class Img {
        public final Identifier id;
        public final int w;
        public final int h;
        private final DynamicTexture tex;

        Img(Identifier id, DynamicTexture tex, int w, int h) {
            this.id = id;
            this.tex = tex;
            this.w = w;
            this.h = h;
        }

        public void draw(GuiGraphics g, int x, int y, int dw, int dh, int tint) {
            g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, dw, dh, w, h, w, h, tint);
        }

        public void drawPart(GuiGraphics g, int x, int y, int dw, int dh, int sx, int sy, int sw, int sh, int tint) {
            g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, (float) sx, (float) sy, dw, dh, sw, sh, w, h, tint);
        }

        public void put(int[] argb) {
            writePixels(tex.getPixels(), argb, w, h);
            tex.upload();
        }

        public void putRows(int[] argb, int y0, int y1) {
            y0 = Math.max(0, y0);
            y1 = Math.min(h, y1);
            if (y1 <= y0) return;
            writeRows(tex.getPixels(), argb, w, y0, y1);
            tex.upload();
        }

        public void release() {
            PENDING.add(new Object[] {this, System.nanoTime()});
        }

        void free() {
            boolean managed = false;
            try {
                Minecraft.getInstance().getTextureManager().release(id);
                managed = true;
            } catch (RuntimeException ignored) {
            }
            if (!managed) {
                try {
                    tex.close();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private static final Map<String, Img> CACHE = new HashMap<>();
    private static final java.util.List<Object[]> PENDING = new java.util.ArrayList<>();

    private static void flush(boolean all) {
        if (PENDING.isEmpty()) return;
        long now = System.nanoTime();
        java.util.Iterator<Object[]> it = PENDING.iterator();
        while (it.hasNext()) {
            Object[] p = it.next();
            if (all || now - (Long) p[1] > 1_000_000_000L) {
                ((Img) p[0]).free();
                it.remove();
            }
        }
    }
    private static int counter;
    private static int order = -1;

    private static boolean abgr() {
        if (order < 0) {
            order = 0;
            try {
                BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                probe.setRGB(0, 0, 0xFFFF0000);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(probe, "png", out);
                try (NativeImage img = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(out.toByteArray()))) {
                    int v = img.getPixel(0, 0);
                    if ((v & 255) > ((v >> 16) & 255)) order = 1;
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return order == 1;
    }

    static void writePixels(NativeImage img, int[] argb, int w, int h) {
        writeRows(img, argb, w, 0, h);
    }

    static void writeRows(NativeImage img, int[] argb, int w, int y0, int y1) {
        boolean swap = abgr();
        int ww = Math.min(w, img.getWidth());
        int hh = Math.min(y1, img.getHeight());
        for (int y = y0; y < hh; y++) {
            int row = y * w;
            for (int x = 0; x < ww; x++) {
                int c = argb[row + x];
                if (swap) c = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
                img.setPixel(x, y, c);
            }
        }
    }

    private static final class Smooth extends DynamicTexture {
        Smooth(java.util.function.Supplier<String> label, NativeImage image) {
            super(label, image);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    private static Img register(String key, NativeImage image) {
        return register(key, image, false);
    }

    private static Img register(String key, NativeImage image, boolean smooth) {
        flush(false);
        int n = ++counter;
        Identifier id = Identifier.fromNamespaceAndPath("zeus", "dyn/" + n);
        DynamicTexture tex = smooth ? new Smooth(() -> "zeus_" + n, image) : new DynamicTexture(() -> "zeus_" + n, image);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        return new Img(id, tex, image.getWidth(), image.getHeight());
    }

    public static Img resource(String path) {
        Img have = CACHE.get(path);
        if (have != null) return have;
        try (InputStream in = Tex.class.getResourceAsStream("/assets/zeus/" + path)) {
            if (in == null) return null;
            NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in);
            Img img = register(path, image);
            CACHE.put(path, img);
            return img;
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not load picture {}", path, e);
            return null;
        }
    }

    public static Img sprite(String path) {
        String key = "smooth:" + path;
        Img have = CACHE.get(key);
        if (have != null) return have;
        try (InputStream in = Tex.class.getResourceAsStream("/assets/zeus/" + path)) {
            if (in == null) return null;
            NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in);
            Img img = register(key, image, true);
            CACHE.put(key, img);
            return img;
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not load picture {}", path, e);
            return null;
        }
    }

    public static BufferedImage image(String path) {
        try (InputStream in = Tex.class.getResourceAsStream("/assets/zeus/" + path)) {
            if (in == null) return null;
            return javax.imageio.ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            Zeus.LOG.warn("Could not read picture {}", path, e);
            return null;
        }
    }

    public static Img fitted(String key, String path, int w, int h, boolean cover, float anchorX, float anchorY) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        Img have = CACHE.get(key);
        if (have != null && have.w == w && have.h == h) return have;
        BufferedImage src = image(path);
        if (src == null) return null;
        BufferedImage out = scale(src, w, h, cover, anchorX, anchorY);
        return made(key, out.getRGB(0, 0, w, h, null, 0, w), w, h);
    }

    static BufferedImage scale(BufferedImage src, int w, int h, boolean cover, float ax, float ay) {
        double sx = w / (double) src.getWidth();
        double sy = h / (double) src.getHeight();
        int tw = w;
        int th = h;
        if (cover) {
            double s = Math.max(sx, sy);
            tw = (int) Math.ceil(src.getWidth() * s);
            th = (int) Math.ceil(src.getHeight() * s);
        }
        BufferedImage cur = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g0 = cur.createGraphics();
        g0.drawImage(src, 0, 0, null);
        g0.dispose();
        while (cur.getWidth() >= tw * 2 || cur.getHeight() >= th * 2) {
            int nw = Math.max(tw, cur.getWidth() >= tw * 2 ? cur.getWidth() / 2 : cur.getWidth());
            int nh = Math.max(th, cur.getHeight() >= th * 2 ? cur.getHeight() / 2 : cur.getHeight());
            BufferedImage next = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g1 = next.createGraphics();
            g1.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g1.drawImage(cur, 0, 0, nw, nh, null);
            g1.dispose();
            cur = next;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        int ox = Math.round((w - tw) * Math.max(0f, Math.min(1f, ax)));
        int oy = Math.round((h - th) * Math.max(0f, Math.min(1f, ay)));
        g2.drawImage(cur, ox, oy, tw, th, null);
        g2.dispose();
        return out;
    }

    public static Img blank(int w, int h) {
        return register("blank", new NativeImage(w, h, true));
    }

    public static Img made(String key, int[] argb, int w, int h) {
        Img have = CACHE.get(key);
        if (have != null && have.w == w && have.h == h) return have;
        if (have != null) have.release();
        Img img = blank(w, h);
        img.put(argb);
        CACHE.put(key, img);
        return img;
    }

    public static Img cached(String key) {
        return CACHE.get(key);
    }

    public static byte[] bytes(String path) {
        try (InputStream in = Tex.class.getResourceAsStream("/assets/zeus/" + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    public static int guiScale() {
        try {
            return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        } catch (RuntimeException e) {
            return 2;
        }
    }

    public static void releaseAll() {
        for (Img i : CACHE.values()) i.release();
        CACHE.clear();
    }
}
