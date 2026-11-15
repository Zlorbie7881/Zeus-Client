package com.zlorbie788.zeus.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix3x2f;

/**
 * Rounded rectangles and their outlines as one GUI element each: nine slices of a small anti-aliased corner
 * texture (made once per radius, at the screen's real pixel density), with the gradient in the vertex colours.
 * Drawing a rounded panel used to take one fill per row of pixels; this takes one element, and the GUI batches
 * neighbouring ones into a single draw call. The corners are smooth instead of stepped.
 */
public final class Shapes {
    private Shapes() {
    }

    private static boolean broken;

    /** True while rounded shapes draw as one textured piece each (cheap enough for the light effects mode too). */
    static boolean ok() {
        return !broken;
    }

    /** The corner texture: (2R+1) square, white, alpha = coverage of a disc (fill) or a ring (outline) of radius R pixels. */
    private static Tex.Img corner(int rpx, int ring) {
        String key = "shape:" + rpx + ":" + ring;
        Tex.Img have = Tex.cached(key);
        if (have != null) return have;
        int s = 2 * rpx + 1;
        int[] argb = new int[s * s];
        float c = rpx + 0.5f;
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                float a = 0f;
                // supersample 4x4 for clean coverage
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float dx = i + (sx + 0.5f) / 4f - c, dy = j + (sy + 0.5f) / 4f - c;
                        float d = (float) Math.sqrt(dx * dx + dy * dy);
                        boolean in = d <= rpx + 0.5f;
                        if (ring > 0) in &= d >= rpx + 0.5f - ring;
                        if (in) a += 1f / 16f;
                    }
                }
                argb[j * s + i] = (Math.round(a * 255f) << 24) | 0xFFFFFF;
            }
        }
        return Tex.madeSmooth(key, argb, s, s);
    }

    private static float scale() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    /** A rounded rectangle filled with a vertical gradient. False if it could not be drawn this way. */
    public static boolean rrect(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
        return draw(g, x, y, w, h, r, top, bottom, 0);
    }

    /** A one-pixel rounded outline. */
    public static boolean outline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        return draw(g, x, y, w, h, r, color, color, 1);
    }

    private static boolean draw(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom, int ringGui) {
        if (broken || w <= 0 || h <= 0 || r < 1 || ((top | bottom) >>> 24) == 0) return broken ? false : true;
        try {
            float sc = Math.max(1f, scale());
            int rpx = Math.max(1, Math.round(r * sc));
            int ring = ringGui > 0 ? Math.max(1, Math.round(ringGui * sc)) : 0;
            Tex.Img img = corner(rpx, ring);
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(img.id);
            TextureSetup setup = TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler());
            Matrix3x2f pose = new Matrix3x2f(g.pose());
            ScreenRectangle scissor = g.scissorStack.peek();
            g.guiRenderState.submitGuiElement(new Slice(setup, pose, scissor, x, y, w, h, r, top, bottom, 2 * rpx + 1));
            return true;
        } catch (RuntimeException | Error e) {
            broken = true;
            com.zlorbie788.zeus.core.Zeus.LOG.warn("Rounded shapes fall back to plain fills", e);
            return false;
        }
    }

    private static int lerp(int a, int b, float t) {
        if (a == b) return a;
        int r = 0;
        for (int sh = 0; sh < 32; sh += 8) {
            int ca = (a >>> sh) & 255, cb = (b >>> sh) & 255;
            r |= (Math.round(ca + (cb - ca) * t) & 255) << sh;
        }
        return r;
    }

    /** Nine quads: corners from the texture's corners, edges and middle from its centre row and column. */
    private static final class Slice implements GuiElementRenderState {
        private final TextureSetup setup;
        private final Matrix3x2f pose;
        private final ScreenRectangle scissor;
        private final ScreenRectangle bounds;
        private final float[] xs, ys, us, vs;
        private final int top, bottom;
        private final int y0, h;
        private float mid;

        Slice(TextureSetup setup, Matrix3x2f pose, ScreenRectangle scissor, int x, int y, int w, int h, int r, int top, int bottom, int size) {
            this.setup = setup;
            this.pose = pose;
            this.scissor = scissor;
            this.top = top;
            this.bottom = bottom;
            this.y0 = y;
            this.h = h;
            int rr = Math.min(r, Math.min(w, h) / 2);
            float rx = rr, ry = rr;
            xs = new float[] {x, x + rx, x + w - rx, x + w};
            ys = new float[] {y, y + ry, y + h - ry, y + h};
            int half = size / 2;                         // the centre texel: straight edges and the middle use only it
            float lo = half / (float) size, hi = (half + 1) / (float) size, mid = (half + 0.5f) / size;
            us = new float[] {0f, lo, hi, 1f};
            vs = new float[] {0f, lo, hi, 1f};
            this.mid = mid;
            ScreenRectangle b = new ScreenRectangle(x, y, Math.max(1, w), Math.max(1, h)).transformMaxBounds(pose);
            bounds = scissor != null ? scissor.intersection(b) : b;
        }

        private int col(float yy) {
            return lerp(top, bottom, h <= 0 ? 0f : Math.max(0f, Math.min(1f, (yy - y0) / h)));
        }

        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    float xa = xs[i], xb = xs[i + 1], ya = ys[j], yb = ys[j + 1];
                    if (xb <= xa || yb <= ya) continue;
                    float ua = i == 1 ? mid : us[i], ub = i == 1 ? mid : us[i + 1];
                    float va = j == 1 ? mid : vs[j], vb = j == 1 ? mid : vs[j + 1];
                    int ca = col(ya), cb = col(yb);
                    vc.addVertexWith2DPose(pose, xa, ya).setUv(ua, va).setColor(ca);
                    vc.addVertexWith2DPose(pose, xa, yb).setUv(ua, vb).setColor(cb);
                    vc.addVertexWith2DPose(pose, xb, yb).setUv(ub, vb).setColor(cb);
                    vc.addVertexWith2DPose(pose, xb, ya).setUv(ub, va).setColor(ca);
                }
            }
        }

        @Override
        public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return RenderPipelines.GUI_TEXTURED;
        }

        @Override
        public TextureSetup textureSetup() {
            return setup;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return scissor;
        }

        @Override
        public ScreenRectangle bounds() {
            return bounds;
        }
    }
}
